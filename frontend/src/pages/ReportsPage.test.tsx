import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ReportsPage from './ReportsPage'
import { reportService } from '@/services/reportService'
import { renderWithProviders } from '@/test/utils'
import { currentMonth, shiftMonth } from '@/utils/months'
import type { MonthlyAutopsy } from '@/types/reports'

const report: MonthlyAutopsy = {
  period: 'August 2026', year: 2026, month: 8, startDate: '2026-08-01', endDate: '2026-08-31', isComplete: true,
  currencyCode: 'INR', income: 75000, expenses: 42300, savings: 32700, savingsRate: 0.436, previousExpenses: 36700,
  expenseChangePercentage: 15.3, healthScore: 82,
  spendingByCategory: [{ categoryName: 'Food', colorHex: '#FF6B6B', amount: 8400, percentage: 100 }],
  changes: {
    largestIncreases: [{ category: 'Food', previous: 7100, current: 8400, amount: 1300, percentageChange: 18.3 }],
    largestDecreases: [{ category: 'Transport', previous: 4800, current: 3600, amount: -1200, percentageChange: -25 }],
  },
  largestMerchants: [{ merchantName: 'Zomato', amount: 8400, count: 24 }],
  recurringPayments: [{ merchantName: 'Netflix', amount: 499, frequency: 'monthly', monthlyCost: 499 }],
  unusualTransactions: [{ description: 'You spent ₹8,500 at Amazon on Shopping, 5x your usual Shopping purchase of ₹1,700.', amount: 8500, date: '2026-08-18', categoryName: 'Shopping' }],
  budgetPerformance: { Food: { budget: 9000, spent: 8550, percentage: 95, exceeded: false } },
  mostImportantInsight: 'Food delivery increased 42%.',
  suggestedAction: 'Replace half of food delivery with home cooking.',
  nextMonthWatchlist: [{ kind: 'recurring', message: 'About ₹499 of recurring payments (1) will be due next month.' }],
  transactionCount: 40, generatedAt: '2026-09-01T00:00:00Z',
}

describe('ReportsPage', () => {
  beforeEach(() => {
    vi.spyOn(reportService, 'autopsy').mockResolvedValue(report)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('defaults to last month and shows every autopsy section', async () => {
    renderWithProviders(<ReportsPage />)
    const article = await screen.findByRole('article', { name: 'Money autopsy for August 2026' })

    expect(reportService.autopsy).toHaveBeenCalledWith(shiftMonth(currentMonth(), -1))
    expect(within(article).getByText('₹32,700')).toBeInTheDocument()
    expect(within(article).getByText('43.6%')).toBeInTheDocument()
    expect(within(article).getByText('Food delivery increased 42%.')).toBeInTheDocument()
    const changes = within(article).getByRole('list', { name: 'Changes versus last month' })
    expect(within(changes).getByText('(+18.3%)')).toBeInTheDocument()
    expect(within(changes).getByText('(-25%)')).toBeInTheDocument()
    expect(within(article).getByText(/5x your usual Shopping purchase/)).toBeInTheDocument()
    expect(within(article).getByRole('progressbar', { name: 'Food budget used' })).toHaveAttribute('aria-valuenow', '95')
    expect(within(article).getByText(/recurring payments \(1\) will be due next month/)).toBeInTheDocument()
  })

  it('downloads the PDF', async () => {
    const download = vi.spyOn(reportService, 'downloadPdf').mockResolvedValue()
    renderWithProviders(<ReportsPage />)
    await screen.findByRole('article')

    await userEvent.click(screen.getByRole('button', { name: /PDF/ }))
    expect(download).toHaveBeenCalledWith(shiftMonth(currentMonth(), -1))
  })
})
