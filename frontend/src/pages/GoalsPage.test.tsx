import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GoalsPage from './GoalsPage'
import { goalService } from '@/services/planningService'
import { renderWithProviders } from '@/test/utils'
import type { Goal } from '@/types/planning'

const emergency: Goal = {
  id: 'g-1', goalName: 'Emergency Fund', goalType: 'savings', targetAmount: 150000, currentProgress: 45000,
  remainingAmount: 105000, progressPercentage: 30, targetDate: '2027-06-30', isActive: true, monthsToTarget: 3,
  monthlyContributionNeeded: 11667, projectedCompletionDate: '2026-12-21', onTrack: true, currencyCode: 'INR',
  createdAt: '2026-09-01T00:00:00Z',
}

describe('GoalsPage', () => {
  beforeEach(() => {
    vi.spyOn(goalService, 'list').mockResolvedValue([emergency])
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows progress and the projection from actual savings', async () => {
    renderWithProviders(<GoalsPage />)
    const card = await screen.findByRole('article', { name: 'Emergency Fund' })

    expect(within(card).getByText('On track')).toBeInTheDocument()
    expect(within(card).getByRole('progressbar', { name: 'Emergency Fund progress' })).toHaveAttribute('aria-valuenow', '30')
    expect(within(card).getByText('Needs ₹11,667 a month to finish on time.')).toBeInTheDocument()
    expect(within(card).getByText(/At your current savings rate: Dec 2026 \(3 months\)/)).toBeInTheDocument()
  })

  it('creates a goal and validates the target date', async () => {
    const create = vi.spyOn(goalService, 'create').mockResolvedValue(emergency)
    renderWithProviders(<GoalsPage />)
    await userEvent.click(await screen.findByRole('button', { name: /New goal/ }))
    const dialog = screen.getByRole('dialog', { name: 'New goal' })

    await userEvent.type(within(dialog).getByLabelText('Name'), 'Vacation')
    await userEvent.type(within(dialog).getByLabelText('Target amount'), '100000')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create goal' }))
    expect(await within(dialog).findByText('Choose a target date in the future')).toBeInTheDocument()

    await userEvent.type(within(dialog).getByLabelText('Target date'), '2030-01-31')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create goal' }))
    expect(create).toHaveBeenCalledWith({
      goalName: 'Vacation', goalDescription: undefined, goalType: 'savings', targetAmount: 100000, currentProgress: 0,
      targetDate: '2030-01-31',
    })
  })

  it('adds money to a goal', async () => {
    const contribute = vi.spyOn(goalService, 'contribute').mockResolvedValue({ ...emergency, currentProgress: 50000 })
    renderWithProviders(<GoalsPage />)
    await userEvent.click(await screen.findByRole('button', { name: /Add money/ }))
    const dialog = screen.getByRole('dialog', { name: 'Add money to Emergency Fund' })

    await userEvent.type(within(dialog).getByLabelText('Amount'), '5000')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add' }))
    expect(contribute).toHaveBeenCalledWith('g-1', 5000)
  })
})
