import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardPage from './DashboardPage'
import { dashboardService } from '@/services/dashboardService'
import { renderWithProviders } from '@/test/utils'
import { shiftMonth, monthRange } from '@/utils/months'
import type { Dashboard } from '@/types/dashboard'

const dashboard: Dashboard = {
  period: { startDate: '2026-09-01', endDate: '2026-09-21', month: 'September', monthNumber: 9, year: 2026, isCurrentMonth: true },
  summary: {
    totalIncome: 75000,
    totalExpense: 42300,
    netSavings: 32700,
    savingsRate: 0.436,
    currencyCode: 'INR',
    transactionCount: 40,
    expenseChangePercentage: 15.3,
    previousMonthExpense: 36700,
  },
  financialHealth: {
    score: 82,
    factors: { savingsRate: 30 },
    breakdown: [
      { key: 'savingsRate', label: 'Savings rate', weight: 30, points: 30, scored: true, explanation: 'You saved 44% of income.' },
      { key: 'budgetAdherence', label: 'Budget adherence', weight: 25, points: 0, scored: false, explanation: 'No budgets set.' },
    ],
    changes: [{ factor: 'savingsRate', change: 6, reason: 'Savings rate improved compared with last month' }],
    summary: 'Excellent: your finances are in strong shape.',
  },
  spending: {
    byCategory: [
      { categoryId: 'cat-food', categoryName: 'Food', colorHex: '#FF6B6B', amount: 8400, percentage: 60, count: 24, trend: 'up' },
      { categoryId: 'cat-transport', categoryName: 'Transport', colorHex: '#4ECDC4', amount: 5600, percentage: 40, count: 9, trend: 'down' },
    ],
    topMerchants: [{ merchantId: 'm-zomato', merchantName: 'Zomato', amount: 4200, count: 12 }],
  },
  trend: [{ month: 'Sep', monthNumber: 9, year: 2026, income: 75000, expense: 42300 }],
  recentTransactions: [
    { id: 'tx-1', merchantName: 'Zomato', categoryName: 'Food', amount: 450, transactionDate: '2026-09-05', transactionType: 'debit' },
  ],
  insights: [],
  recurringPayments: [],
  budgets: [],
}

function LocationProbe() {
  const location = useLocation()
  return <p data-testid="location">{location.pathname + location.search}</p>
}

function renderDashboard() {
  return renderWithProviders(
    <Routes>
      <Route path="/dashboard" element={<DashboardPage />} />
      <Route path="/transactions" element={<LocationProbe />} />
    </Routes>,
    { route: '/dashboard' },
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.spyOn(dashboardService, 'get').mockResolvedValue(dashboard)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows totals, change vs last month and the health score breakdown', async () => {
    renderDashboard()

    expect(await screen.findByText('₹75,000')).toBeInTheDocument()
    expect(screen.getByText('₹42,300')).toBeInTheDocument()
    expect(screen.getByText('₹32,700')).toBeInTheDocument()
    expect(screen.getByText('15.3% more than last month')).toBeInTheDocument()
    expect(screen.getByText('Savings rate 43.6%')).toBeInTheDocument()
    expect(screen.getByText('82')).toBeInTheDocument()
    const breakdown = screen.getByRole('list', { name: 'Score breakdown' })
    expect(within(breakdown).getByText('Not scored')).toBeInTheDocument()
    expect(screen.getByText(/\+6 · Savings rate improved/)).toBeInTheDocument()
  })

  it('opens the category transactions for the selected month when a category is clicked', async () => {
    renderDashboard()
    const list = await screen.findByRole('list', { name: 'Spending by category' })
    await userEvent.click(within(list).getByRole('button', { name: /Food/ }))

    const location = await screen.findByTestId('location')
    expect(location.textContent).toContain('/transactions?')
    expect(location.textContent).toContain('categoryId=cat-food')
    expect(location.textContent).toContain('startDate=2026-09-01')
  })

  it('opens merchant transactions from the top merchants list', async () => {
    renderDashboard()
    const merchants = await screen.findByRole('list', { name: 'Top merchants' })
    await userEvent.click(within(merchants).getByRole('button', { name: /Zomato/ }))
    expect((await screen.findByTestId('location')).textContent).toContain('merchantId=m-zomato')
  })

  it('requests the previous month when stepping back', async () => {
    const get = vi.mocked(dashboardService.get)
    renderDashboard()
    await screen.findByText('₹75,000')
    await userEvent.click(screen.getByRole('button', { name: 'Previous month' }))
    const now = new Date()
    const previous = shiftMonth({ month: now.getMonth() + 1, year: now.getFullYear() }, -1)
    await waitFor(() => expect(get).toHaveBeenLastCalledWith(previous.month, previous.year))
    expect(screen.getByRole('button', { name: 'Next month' })).toBeEnabled()
  })

  it('shows an empty state when the month has no transactions', async () => {
    vi.mocked(dashboardService.get).mockResolvedValue({
      ...dashboard,
      summary: { ...dashboard.summary, transactionCount: 0 },
    })
    renderDashboard()
    expect(await screen.findByText('No transactions in September 2026')).toBeInTheDocument()
  })
})

describe('month helpers', () => {
  it('shifts across year boundaries and computes month ranges', () => {
    expect(shiftMonth({ month: 1, year: 2026 }, -1)).toEqual({ month: 12, year: 2025 })
    expect(shiftMonth({ month: 12, year: 2026 }, 1)).toEqual({ month: 1, year: 2027 })
    expect(monthRange({ month: 2, year: 2028 })).toEqual({ startDate: '2028-02-01', endDate: '2028-02-29' })
  })
})
