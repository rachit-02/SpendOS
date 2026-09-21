import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RecurringPage from './RecurringPage'
import { recurringService } from '@/services/budgetService'
import { renderWithProviders } from '@/test/utils'
import type { RecurringPayment } from '@/types/budgets'

function payment(overrides: Partial<RecurringPayment>): RecurringPayment {
  return {
    id: 'r-1',
    merchantName: 'Netflix',
    typicalAmount: 499,
    currencyCode: 'INR',
    frequency: 'monthly',
    nextExpectedDate: '2026-10-05',
    occurrencesCount: 12,
    confidence: 0.98,
    isActive: true,
    isUserConfirmed: false,
    status: 'pending',
    monthlyCost: 499,
    ...overrides,
  }
}

describe('RecurringPage', () => {
  beforeEach(() => {
    vi.spyOn(recurringService, 'list').mockResolvedValue([
      payment({}),
      payment({ id: 'r-2', merchantName: 'Gym', typicalAmount: 1500, monthlyCost: 1500, isUserConfirmed: true, status: 'confirmed' }),
      payment({ id: 'r-3', merchantName: 'Old Magazine', isActive: false, status: 'lapsed', monthlyCost: 200 }),
    ])
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('groups payments by status and totals the monthly cost of active ones', async () => {
    renderWithProviders(<RecurringPage />)

    const review = await screen.findByRole('list', { name: 'Recurring payments to review' })
    expect(within(review).getByText('Netflix')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Confirmed recurring payments' })).getByText('Gym')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Lapsed recurring payments' })).getByText('Old Magazine')).toBeInTheDocument()
    expect(screen.getByText('₹1,999.00')).toBeInTheDocument()
    expect(within(review).getByText('98% confident')).toBeInTheDocument()
  })

  it('confirms and dismisses detections', async () => {
    const confirm = vi.spyOn(recurringService, 'confirm').mockResolvedValue({ id: 'r-1' })
    const reject = vi.spyOn(recurringService, 'reject').mockResolvedValue({ id: 'r-1' })
    renderWithProviders(<RecurringPage />)

    await userEvent.click(await screen.findByRole('button', { name: 'Confirm Netflix' }))
    await userEvent.click(screen.getByRole('button', { name: 'Not recurring: Gym' }))

    expect(confirm).toHaveBeenCalledWith('r-1', expect.anything())
    expect(reject).toHaveBeenCalledWith('r-2', expect.anything())
  })

  it('runs a scan on demand', async () => {
    const detect = vi.spyOn(recurringService, 'detect').mockResolvedValue({ detected: 3, created: 1, updated: 2, active: 2 })
    renderWithProviders(<RecurringPage />)

    await userEvent.click(await screen.findByRole('button', { name: /Scan transactions/ }))
    expect(detect).toHaveBeenCalled()
    expect(await screen.findByText('Scan complete: 3 recurring payments found.')).toBeInTheDocument()
  })
})
