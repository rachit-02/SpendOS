import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AnalyticsPage from './AnalyticsPage'
import { analyticsService } from '@/services/analyticsService'
import { categoryService } from '@/services/accountService'
import { userService } from '@/services/userService'
import { renderWithProviders } from '@/test/utils'
import { categories } from '@/test/fixtures'
import type { MonthlyAnalytics } from '@/types/analytics'

const monthly: MonthlyAnalytics = {
  period: { month: 'September', monthNumber: 9, year: 2026, startDate: '2026-09-01', endDate: '2026-09-21' },
  income: { total: 75000, bySource: [{ source: 'Salary', amount: 75000, percentage: 100 }] },
  expenses: {
    total: 42300,
    byCategory: [{ categoryId: 'cat-food', categoryName: 'Food', colorHex: '#FF6B6B', amount: 8400, percentage: 100, count: 24 }],
    byPaymentMethod: [{ method: 'upi', amount: 30000, percentage: 70.9, count: 30 }],
  },
  savings: 32700,
  savingsRate: 0.436,
  previousMonthComparison: {
    previousIncome: 75000,
    previousExpense: 36700,
    expenseChange: 15.3,
    spendingTrend: 'up',
    categoryChanges: [
      { categoryName: 'Food', previous: 6200, current: 8400, change: 2200, changePercentage: 35.5 },
      { categoryName: 'Travel', previous: 0, current: 3000, change: 3000 },
    ],
  },
  currencyCode: 'INR',
}

describe('AnalyticsPage', () => {
  beforeEach(() => {
    vi.spyOn(analyticsService, 'monthly').mockResolvedValue(monthly)
    vi.spyOn(categoryService, 'list').mockResolvedValue(categories)
    vi.spyOn(userService, 'preferences').mockResolvedValue({
      currencyCode: 'INR', timezone: 'Asia/Kolkata', fiscalYearStartMonth: 1, theme: 'light', language: 'en',
      financialHealthScoreEnabled: true, demoMode: false, emailReportsEnabled: false, emailAlertsEnabled: false,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the monthly breakdown and what changed', async () => {
    renderWithProviders(<AnalyticsPage />, { route: '/analytics' })

    expect(await screen.findByText('₹42,300')).toBeInTheDocument()
    expect(screen.getByText('+15.3% vs last month')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Spending by payment method' })).getByText('UPI')).toBeInTheDocument()
    const changes = screen.getByRole('table', { name: 'Category changes versus last month' })
    expect(within(changes).getByText('(+35.5%)')).toBeInTheDocument()
    expect(within(changes).getByText('(new)')).toBeInTheDocument()
  })

  it('exports the month as CSV and PDF', async () => {
    const exporter = vi.spyOn(analyticsService, 'exportMonthly').mockResolvedValue()
    renderWithProviders(<AnalyticsPage />, { route: '/analytics' })
    await screen.findByText('₹42,300')

    await userEvent.click(screen.getByRole('button', { name: /CSV/ }))
    await userEvent.click(screen.getByRole('button', { name: /PDF/ }))

    expect(exporter).toHaveBeenNthCalledWith(1, expect.objectContaining({ month: expect.any(Number) }), 'csv')
    expect(exporter).toHaveBeenNthCalledWith(2, expect.anything(), 'pdf')
  })

  it('switches tabs with the keyboard and shows the category forecast', async () => {
    vi.spyOn(analyticsService, 'categoryTrend').mockResolvedValue({
      categoryId: 'cat-food',
      categoryName: 'Food',
      trend: [{ month: 'Sep', monthNumber: 9, year: 2026, amount: 8400, count: 24 }],
      average: 7516.67,
      percentageChange: 14.4,
      forecast: 7883.33,
      forecastMethod: 'Weighted average of the last three months',
    })
    renderWithProviders(<AnalyticsPage />, { route: '/analytics' })

    const monthlyTab = await screen.findByRole('tab', { name: 'Monthly' })
    monthlyTab.focus()
    await userEvent.keyboard('{ArrowRight}')

    expect(screen.getByRole('tab', { name: 'Category trends' })).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('₹7,883')).toBeInTheDocument()
    expect(screen.getByText('+14.4%')).toBeInTheDocument()
    expect(analyticsService.categoryTrend).toHaveBeenCalledWith('cat-food', 6)
  })

  it('opens the comparison tab from the URL', async () => {
    vi.spyOn(analyticsService, 'compare').mockResolvedValue({
      first: { period: monthly.period, income: 0, expense: 36700, savings: 0, savingsRate: 0 },
      second: { period: monthly.period, income: 0, expense: 42300, savings: 0, savingsRate: 0 },
      expenseChange: 5600,
      expenseChangePercentage: 15.3,
      categories: [],
    })
    renderWithProviders(<AnalyticsPage />, { route: '/analytics?tab=compare' })

    expect(await screen.findByText('+₹5,600')).toBeInTheDocument()
  })
})
