import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BudgetsPage from './BudgetsPage'
import { budgetService } from '@/services/budgetService'
import { categoryService } from '@/services/accountService'
import { userService } from '@/services/userService'
import { renderWithProviders } from '@/test/utils'
import { categories } from '@/test/fixtures'
import type { Budget } from '@/types/budgets'

const foodBudget: Budget = {
  id: 'b-1',
  budgetName: 'Food Budget',
  budgetType: 'monthly',
  totalAmount: 9000,
  currencyCode: 'INR',
  startDate: '2026-09-01',
  endDate: '2026-09-30',
  alertThreshold: 90,
  isActive: true,
  spentAmount: 8400,
  remainingAmount: 600,
  percentage: 93.3,
  isExceeded: false,
  isAlert: true,
  status: 'warning',
  categories: [
    {
      categoryId: 'cat-food', categoryName: 'Food', allocatedAmount: 9000, spentAmount: 8400, remainingAmount: 600,
      percentage: 93.3, isExceeded: false, isAlert: true,
    },
  ],
}

describe('BudgetsPage', () => {
  beforeEach(() => {
    vi.spyOn(budgetService, 'list').mockResolvedValue([foodBudget])
    vi.spyOn(budgetService, 'alerts').mockResolvedValue([
      {
        budgetId: 'b-1', budgetName: 'Food Budget', limit: 9000, spent: 8400, percentage: 93.3, level: 'warning',
        message: 'Food Budget has used 93% of its budget',
      },
    ])
    vi.spyOn(categoryService, 'list').mockResolvedValue(categories)
    vi.spyOn(userService, 'preferences').mockResolvedValue({
      currencyCode: 'INR', timezone: 'Asia/Kolkata', fiscalYearStartMonth: 1, theme: 'light', language: 'en',
      financialHealthScoreEnabled: true, demoMode: false, emailReportsEnabled: false, emailAlertsEnabled: false,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows progress, the warning status and alerts', async () => {
    renderWithProviders(<BudgetsPage />)

    expect(await screen.findByText('Near limit')).toBeInTheDocument()
    expect(screen.getByText('93.3%')).toBeInTheDocument()
    expect(screen.getByText('₹600.00 left')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Budget alerts' })).getByText(/93% of its budget/)).toBeInTheDocument()
    expect(screen.getAllByRole('progressbar', { name: 'Food Budget spent' })[0]).toHaveAttribute('aria-valuenow', '93')
  })

  it('validates allocations against the total before creating', async () => {
    const create = vi.spyOn(budgetService, 'create').mockResolvedValue(foodBudget)
    renderWithProviders(<BudgetsPage />)
    await screen.findByText('Food Budget')

    await userEvent.click(screen.getByRole('button', { name: /New budget/ }))
    const dialog = await screen.findByRole('dialog', { name: 'New budget' })
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Groceries')
    await userEvent.type(within(dialog).getByLabelText('Total limit'), '5000')
    await userEvent.click(within(dialog).getByRole('button', { name: /Add category limit/ }))
    await userEvent.selectOptions(within(dialog).getByLabelText('Category 1'), 'cat-food')
    await userEvent.type(within(dialog).getByLabelText('Limit for category 1'), '6000')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create budget' }))

    expect(within(dialog).getByText('Category allocations add up to more than the total')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()

    await userEvent.clear(within(dialog).getByLabelText('Limit for category 1'))
    await userEvent.type(within(dialog).getByLabelText('Limit for category 1'), '4000')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create budget' }))

    expect(create).toHaveBeenCalledWith(expect.objectContaining({
      budgetName: 'Groceries',
      budgetType: 'monthly',
      totalAmount: 5000,
      alertThreshold: 90,
      categories: [{ categoryId: 'cat-food', allocatedAmount: 4000 }],
    }))
  })

  it('asks for confirmation before deleting', async () => {
    const remove = vi.spyOn(budgetService, 'remove').mockResolvedValue()
    renderWithProviders(<BudgetsPage />)

    await userEvent.click(await screen.findByRole('button', { name: 'Delete Food Budget' }))
    expect(remove).not.toHaveBeenCalled()
    await userEvent.click(within(await screen.findByRole('dialog', { name: 'Delete budget?' })).getByRole('button', { name: /Delete Food Budget/ }))
    expect(remove).toHaveBeenCalledWith('b-1')
  })
})
