import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import InsightsPage from './InsightsPage'
import { insightService } from '@/services/insightService'
import { userService } from '@/services/userService'
import { renderWithProviders } from '@/test/utils'
import type { Insight } from '@/types/insights'

function insight(overrides: Partial<Insight>): Insight {
  return {
    id: 'i-1',
    type: 'money_leak',
    title: 'Frequent small purchases at Zomato',
    description: 'You spent ₹1,860 across 18 small purchases at Zomato this month, 42% more than your three-month average.',
    actionable: true,
    suggestedAction: 'Halving these purchases would save about ₹930 a month.',
    importance: 55,
    periodStartDate: '2026-09-01',
    periodEndDate: '2026-09-30',
    createdAt: '2026-09-21T00:00:00Z',
    ...overrides,
  }
}

describe('InsightsPage', () => {
  beforeEach(() => {
    vi.spyOn(insightService, 'list').mockResolvedValue([
      insight({}),
      insight({ id: 'i-2', type: 'anomaly', title: 'Unusual Food spending', description: 'You normally spend ₹1,500–₹2,500 on Food in a month.', suggestedAction: 'Review your Food transactions.' }),
    ])
    vi.spyOn(insightService, 'history').mockResolvedValue([])
    vi.spyOn(insightService, 'anomalies').mockResolvedValue([])
    vi.spyOn(userService, 'preferences').mockResolvedValue({
      currencyCode: 'INR', timezone: 'Asia/Kolkata', fiscalYearStartMonth: 1, theme: 'light', language: 'en',
      financialHealthScoreEnabled: true, demoMode: false, emailReportsEnabled: false, emailAlertsEnabled: false,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('lists explainable insights with their suggested actions', async () => {
    renderWithProviders(<InsightsPage />)

    const list = await screen.findByRole('list', { name: 'Insights' })
    expect(within(list).getByText(/₹1,860 across 18 small purchases/)).toBeInTheDocument()
    expect(within(list).getByText('Halving these purchases would save about ₹930 a month.')).toBeInTheDocument()
    expect(within(list).getAllByRole('listitem')).toHaveLength(2)
  })

  it('filters by insight type', async () => {
    renderWithProviders(<InsightsPage />)
    await screen.findByRole('list', { name: 'Insights' })

    await userEvent.click(screen.getByRole('tab', { name: 'Unusual spending' }))

    const list = screen.getByRole('list', { name: 'Insights' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(1)
    expect(within(list).getByText('Unusual Food spending')).toBeInTheDocument()
  })

  it('shows the supporting transactions for an insight', async () => {
    vi.spyOn(insightService, 'get').mockResolvedValue(insight({
      relatedTransactions: [
        { id: 't-1', amount: 100, merchant: 'Zomato', date: '2026-09-02' },
        { id: 't-2', amount: 160, merchant: 'Zomato', date: '2026-09-05' },
      ],
    }))
    renderWithProviders(<InsightsPage />)

    const list = await screen.findByRole('list', { name: 'Insights' })
    await userEvent.click(within(list).getAllByRole('button', { name: 'See the transactions behind this' })[0])

    const drawer = await screen.findByRole('dialog')
    const evidence = await within(drawer).findByRole('list', { name: 'Supporting transactions' })
    expect(within(evidence).getAllByRole('link')).toHaveLength(2)
    expect(within(drawer).getByText('2 transactions · ₹260.00')).toBeInTheDocument()
  })

  it('re-queries anomalies when sensitivity changes', async () => {
    renderWithProviders(<InsightsPage />)
    await screen.findByRole('list', { name: 'Insights' })

    await userEvent.selectOptions(screen.getByLabelText('Sensitivity'), 'high')

    expect(insightService.anomalies).toHaveBeenLastCalledWith('high')
  })
})
