import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import GoalsPage from '@/pages/GoalsPage'
import AssistantPage from '@/pages/AssistantPage'
import MerchantsPage from '@/pages/MerchantsPage'
import HealthPage from '@/pages/HealthPage'
import { goalService } from '@/services/planningService'
import { assistantService } from '@/services/assistantService'
import { merchantService } from '@/services/merchantService'
import { healthMetricsService } from '@/services/healthMetricsService'
import { categoryService } from '@/services/accountService'
import { userService } from '@/services/userService'
import { renderWithProviders } from './utils'
import { a11yViolations } from './a11y'
import { categories, page } from './fixtures'

/** WCAG 2.1 AA checks (axe-core) on representative pages in their loaded state. */
describe('accessibility', () => {
  beforeEach(() => {
    vi.spyOn(categoryService, 'list').mockResolvedValue(categories)
    vi.spyOn(userService, 'preferences').mockRejectedValue(new Error('offline'))
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('the checker itself reports real problems', async () => {
    const { container } = renderWithProviders(<div><img src="x.png" /><button type="button" /></div>)
    const violations = await a11yViolations(container)
    expect(violations.some((v) => v.startsWith('image-alt'))).toBe(true)
    expect(violations.some((v) => v.startsWith('button-name'))).toBe(true)
  })

  it('sign-in and registration forms', async () => {
    const login = renderWithProviders(<LoginPage />)
    expect(await a11yViolations(login.container)).toEqual([])
    login.unmount()
    const register = renderWithProviders(<RegisterPage />)
    expect(await a11yViolations(register.container)).toEqual([])
  })

  it('goals page', async () => {
    vi.spyOn(goalService, 'list').mockResolvedValue([{
      id: 'g-1', goalName: 'Emergency Fund', goalType: 'savings', targetAmount: 150000, currentProgress: 45000,
      remainingAmount: 105000, progressPercentage: 30, targetDate: '2027-06-30', isActive: true, monthsToTarget: 3,
      monthlyContributionNeeded: 11667, projectedCompletionDate: '2026-12-21', onTrack: true, currencyCode: 'INR',
      createdAt: '2026-09-01T00:00:00Z',
    }])
    const { container } = renderWithProviders(<GoalsPage />)
    await screen.findByRole('article', { name: 'Emergency Fund' })
    expect(await a11yViolations(container)).toEqual([])
  })

  it('assistant page', async () => {
    vi.spyOn(assistantService, 'suggestions').mockResolvedValue([{ question: 'Where did most of my money go?', intent: 'top_categories' }])
    const { container } = renderWithProviders(<AssistantPage />)
    await screen.findByRole('button', { name: 'Where did most of my money go?' })
    expect(await a11yViolations(container)).toEqual([])
  })

  it('merchants page', async () => {
    vi.spyOn(merchantService, 'list').mockResolvedValue(page([{
      id: 'm-1', merchantName: 'Zomato', isVerified: true, userCategory: false, transactionCount: 4, categoryId: 'cat-food',
    }]))
    vi.spyOn(merchantService, 'suggestions').mockResolvedValue([])
    const { container } = renderWithProviders(<MerchantsPage />)
    await screen.findByRole('combobox', { name: 'Category for Zomato' })
    expect(await a11yViolations(container)).toEqual([])
  })

  it('health page', async () => {
    vi.spyOn(healthMetricsService, 'current').mockResolvedValue({
      enabled: true, period: '2026-09', score: 64, summary: 'Good, with room to improve.',
      factors: [{ key: 'savingsRate', label: 'Savings rate', weight: 30, points: 20, metric: 20, scored: true, explanation: 'x' }],
      recommendations: [{ factor: 'savingsRate', title: 'Improve savings rate', action: 'Spend less.', potentialPoints: 10 }],
    })
    vi.spyOn(healthMetricsService, 'history').mockResolvedValue([])
    vi.spyOn(healthMetricsService, 'explanation').mockResolvedValue({
      enabled: true, period: '2026-09', previousPeriod: '2026-08', summary: 'Your score stayed at 64.', changes: [],
      recommendations: [],
    })
    const { container } = renderWithProviders(<HealthPage />)
    await screen.findByRole('status')
    expect(await a11yViolations(container)).toEqual([])
  })
})
