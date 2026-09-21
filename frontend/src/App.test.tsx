import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './App'
import { renderWithProviders } from './test/utils'
import { healthService } from './services/healthService'
import { useAuthStore } from './store/authStore'
import { dashboardService } from './services/dashboardService'
import { budgetService } from './services/budgetService'
import { userService } from './services/userService'

describe('AppRoutes', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    useAuthStore.getState().clear()
  })

  it('shows API and database status on the status page', async () => {
    vi.spyOn(healthService, 'get').mockResolvedValue({
      status: 'UP',
      timestamp: '2026-09-21T00:00:00Z',
      checks: { database: 'UP' },
    })

    renderWithProviders(<AppRoutes />, { route: '/status' })

    expect(await screen.findByText('database')).toBeInTheDocument()
    expect(screen.getAllByText('UP')).toHaveLength(2)
  })

  it('reports an unreachable API', async () => {
    vi.spyOn(healthService, 'get').mockRejectedValue(new Error('down'))

    renderWithProviders(<AppRoutes />, { route: '/status' })

    expect(await screen.findByText('API unreachable')).toBeInTheDocument()
  })

  it('renders the 404 page for unknown routes', async () => {
    renderWithProviders(<AppRoutes />, { route: '/does-not-exist' })

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  it('redirects anonymous users from protected pages to login', async () => {
    renderWithProviders(<AppRoutes />, { route: '/settings' })

    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  })

  it('sends signed-in users away from the login page', async () => {
    vi.spyOn(dashboardService, 'get').mockRejectedValue(new Error('offline'))
    vi.spyOn(budgetService, 'alerts').mockResolvedValue([])
    vi.spyOn(userService, 'preferences').mockRejectedValue(new Error('offline'))
    useAuthStore.getState().setSession({
      accessToken: 'token',
      refreshToken: 'refresh',
      user: { userId: '1', email: 'a@example.com', fullName: 'Asha Rao' },
    })

    renderWithProviders(<AppRoutes />, { route: '/login' })

    expect(await screen.findByRole('heading', { name: 'Welcome, Asha' })).toBeInTheDocument()
  })
})
