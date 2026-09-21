import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PlanningPage from './PlanningPage'
import { planningService, simulationService } from '@/services/planningService'
import { categoryService } from '@/services/accountService'
import { userService } from '@/services/userService'
import { renderWithProviders } from '@/test/utils'
import { categories } from '@/test/fixtures'
import type { AffordabilityResult, Simulation, SpendingPrediction } from '@/types/planning'

const prediction: SpendingPrediction = {
  currentDate: '2026-09-15', month: 9, year: 2026, currentSpending: 18500, daysElapsed: 15, remainingDaysInMonth: 15,
  projectedMonthEnd: { min: 36000, max: 44000, median: 40000 }, confidence: 0.82,
  confidenceReason: 'Based on 3 months of history and 15 days of this month', sufficientData: true,
  methodology: 'Spending so far plus the daily rate extrapolated over the remaining days.',
  upcomingRecurring: 1499, dailyAverage: 1233, previousMonthTotal: 42300, monthlyAverage: 39800, currencyCode: 'INR',
}

const affordable: AffordabilityResult = {
  purchase: { amount: 10000, description: 'Laptop' },
  affordability: { isAffordable: true, confidence: 'high' },
  analysis: {
    currentMonthSpending: 18500, expectedIncome: 75000, upcomingRecurring: 1499, expectedRemainingExpenses: 40000,
    availableAfterPurchase: 25000, projectedSavingsWithoutPurchase: 35000, projectedSavingsWithPurchase: 25000,
    savingsImpact: -10000, monthlyGoalContributions: 0,
  },
  considerations: ['Expected income this month: ₹75,000'],
  explanation: 'After expected spending, the Laptop is likely affordable.',
  disclaimer: 'This is a planning estimate, not professional financial advice.',
}

function simulation(id: string, name: string, savingsChange: number): Simulation {
  const impact = (before: number, change: number) => ({ before, after: before + change, change, changePercentage: (change / before) * 100 })
  return {
    id, simulationName: name, createdAt: '2026-09-15T10:00:00Z',
    scenarios: [{ type: 'spend_reduction', categoryId: 'cat-food', amount: savingsChange, period: 'monthly' }],
    results: {
      monthlyImpact: impact(42300, -savingsChange), annualImpact: impact(507600, -savingsChange * 12),
      monthlySavings: impact(32700, savingsChange), annualSavings: impact(392400, savingsChange * 12),
      goalImpact: [{ goalId: 'g-1', goalName: 'Emergency Fund', currentMonthsToCompletion: 6, projectedMonthsWithSimulation: 5 }],
      notes: [], baselineMonths: 3,
    },
  }
}

describe('PlanningPage', () => {
  beforeEach(() => {
    vi.spyOn(planningService, 'prediction').mockResolvedValue(prediction)
    vi.spyOn(simulationService, 'list').mockResolvedValue([])
    vi.spyOn(categoryService, 'list').mockResolvedValue(categories)
    vi.spyOn(userService, 'preferences').mockRejectedValue(new Error('offline'))
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the forecast with its range and confidence', async () => {
    renderWithProviders(<PlanningPage />)
    const forecast = await screen.findByRole('region', { name: 'Spending forecast' })

    expect(within(forecast).getByText('82% confidence')).toBeInTheDocument()
    expect(within(forecast).getByText('₹40,000')).toBeInTheDocument()
    expect(within(forecast).getByText('between ₹36,000 and ₹44,000')).toBeInTheDocument()
    expect(within(forecast).getByText(/Based on 3 months of history/)).toBeInTheDocument()
    expect(within(forecast).getByText(/₹1,499 of recurring payments/)).toBeInTheDocument()
  })

  it('checks whether a purchase is affordable', async () => {
    const check = vi.spyOn(planningService, 'affordability').mockResolvedValue(affordable)
    renderWithProviders(<PlanningPage />)
    const form = await screen.findByRole('form', { name: 'Affordability check' })

    await userEvent.click(within(form).getByRole('button', { name: 'Check' }))
    expect(await screen.findByText('Enter a positive amount')).toBeInTheDocument()
    expect(check).not.toHaveBeenCalled()

    await userEvent.type(within(form).getByLabelText('What is it?'), 'Laptop')
    await userEvent.type(within(form).getByLabelText('Price'), '10,000')
    await userEvent.click(within(form).getByRole('button', { name: 'Check' }))

    expect(check).toHaveBeenCalledWith(10000, 'Laptop')
    const status = await screen.findByRole('status')
    expect(within(status).getByText('Likely affordable')).toBeInTheDocument()
    expect(within(status).getByText(affordable.disclaimer)).toBeInTheDocument()
  })

  it('runs a what-if scenario and compares saved ones', async () => {
    const cut = simulation('s-1', 'Cook more', 2000)
    const laptop = simulation('s-2', 'Buy laptop', -500)
    const create = vi.spyOn(simulationService, 'create').mockResolvedValue(cut)
    vi.spyOn(simulationService, 'list').mockResolvedValue([cut, laptop])
    const compare = vi.spyOn(simulationService, 'compare').mockResolvedValue({ simulations: [cut, laptop], bestForSavingsId: 's-1' })
    renderWithProviders(<PlanningPage />)

    await userEvent.click(await screen.findByRole('tab', { name: 'What-if' }))
    const form = screen.getByRole('form', { name: 'What-if scenario' })
    await userEvent.type(within(form).getByLabelText('Scenario name'), 'Cook more')
    await userEvent.selectOptions(within(form).getByLabelText('Change 1 category'), 'cat-food')
    await userEvent.type(within(form).getByLabelText('Change 1 amount'), '2000')
    await userEvent.click(within(form).getByRole('button', { name: 'Run simulation' }))

    expect(create).toHaveBeenCalledWith('Cook more', [
      { type: 'spend_reduction', amount: 2000, categoryId: 'cat-food', period: 'monthly' },
    ])
    const result = await screen.findByRole('table', { name: 'Impact of Cook more' })
    expect(within(result).getByText('₹40,300')).toBeInTheDocument()
    expect(within(result).getByText('-₹2,000 (-4.7%)')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Goal impact' })).toHaveTextContent('Emergency Fund: 6 → 5 months')

    const compareButton = screen.getByRole('button', { name: /Compare/ })
    expect(compareButton).toBeDisabled()
    await userEvent.click(screen.getByRole('checkbox', { name: /Cook more/ }))
    await userEvent.click(screen.getByRole('checkbox', { name: /Buy laptop/ }))
    await userEvent.click(compareButton)

    expect(compare).toHaveBeenCalledWith(['s-1', 's-2'])
    const table = await screen.findByRole('table', { name: 'Scenario comparison' })
    expect(within(table).getByText('Best for savings')).toBeInTheDocument()
  })

  it('only lets income changes be negative', async () => {
    const create = vi.spyOn(simulationService, 'create')
    renderWithProviders(<PlanningPage />)
    await userEvent.click(await screen.findByRole('tab', { name: 'What-if' }))
    const form = screen.getByRole('form', { name: 'What-if scenario' })

    await userEvent.type(within(form).getByLabelText('Scenario name'), 'Pay cut')
    await userEvent.type(within(form).getByLabelText('Change 1 amount'), '-5000')
    await userEvent.click(within(form).getByRole('button', { name: 'Run simulation' }))
    expect(await within(form).findByText('Enter a positive amount for each change')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })
})
