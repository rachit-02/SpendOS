import { screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import HealthPage from './HealthPage'
import { healthMetricsService } from '@/services/healthMetricsService'
import { userService } from '@/services/userService'
import { renderWithProviders } from '@/test/utils'
import { currentMonth } from '@/utils/months'
import type { HealthMetrics } from '@/types/health'

const metrics: HealthMetrics = {
  enabled: true,
  period: '2026-09',
  score: 64,
  summary: 'Good, with room to improve.',
  factors: [
    { key: 'savingsRate', label: 'Savings rate', weight: 30, points: 20, metric: 20, scored: true, explanation: 'You saved 20% of income.' },
    { key: 'budgetAdherence', label: 'Budget adherence', weight: 25, points: 0, scored: false, explanation: 'No budgets set.' },
  ],
  metrics: { savingsRate: 20, averageMonthlyIncome: 75000, averageMonthlyExpense: 60000, emergencyBufferMonths: 1.7 },
  recommendations: [
    { factor: 'budgetAdherence', title: 'Improve budget adherence', action: 'Create a monthly budget, starting with Food.', potentialPoints: 25 },
  ],
  calculatedAt: '2026-09-21T10:00:00Z',
}

describe('HealthPage', () => {
  beforeEach(() => {
    vi.spyOn(userService, 'preferences').mockRejectedValue(new Error('offline'))
    vi.spyOn(healthMetricsService, 'current').mockResolvedValue(metrics)
    vi.spyOn(healthMetricsService, 'history').mockResolvedValue([
      { period: '2026-08', year: 2026, month: 8, score: 72, factors: {} },
      { period: '2026-09', year: 2026, month: 9, score: 64, factors: {} },
    ])
    vi.spyOn(healthMetricsService, 'explanation').mockResolvedValue({
      enabled: true, period: '2026-09', previousPeriod: '2026-08', score: 64, previousScore: 72, change: -8,
      summary: 'Your score fell from 72 to 64, mainly because of savings rate.',
      changes: [{ factor: 'savingsRate', label: 'Savings rate', previousPoints: 30, currentPoints: 20, change: -10,
        reason: 'You saved 20% of your income, compared with 30% before.' }],
      recommendations: [],
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the score, breakdown, advice, trend and what changed', async () => {
    renderWithProviders(<HealthPage />)

    expect(await screen.findByText('64')).toBeInTheDocument()
    const breakdown = screen.getByRole('list', { name: 'Score breakdown' })
    expect(within(breakdown).getByText('20 / 30')).toBeInTheDocument()
    expect(within(breakdown).getByText('Not scored')).toBeInTheDocument()

    const advice = screen.getByRole('list', { name: 'Recommendations' })
    expect(within(advice).getByText('Create a monthly budget, starting with Food.')).toBeInTheDocument()
    expect(within(advice).getByText('up to +25 pts')).toBeInTheDocument()
    expect(screen.getByText('₹75,000')).toBeInTheDocument()

    expect(await screen.findByRole('img', { name: /Health score over 2 months, from 72 to 64/ })).toBeInTheDocument()
    expect(await screen.findByRole('status')).toHaveTextContent('Your score fell from 72 to 64')
    const changes = screen.getByRole('list', { name: 'Factor changes' })
    expect(within(changes).getByText('-10')).toBeInTheDocument()
    expect(healthMetricsService.explanation).toHaveBeenCalledWith(currentMonth())
  })

  it('explains when the score is turned off', async () => {
    vi.spyOn(healthMetricsService, 'current').mockResolvedValue({ ...metrics, enabled: false, score: undefined })
    renderWithProviders(<HealthPage />)

    expect(await screen.findByText('Health score is turned off')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open settings' })).toHaveAttribute('href', '/settings')
  })
})
