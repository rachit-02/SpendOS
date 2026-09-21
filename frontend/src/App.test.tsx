import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './App'
import { renderWithProviders } from './test/utils'
import { healthService } from './services/healthService'

describe('AppRoutes', () => {
  afterEach(() => {
    vi.restoreAllMocks()
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

  it('renders the 404 page for unknown routes', () => {
    renderWithProviders(<AppRoutes />, { route: '/does-not-exist' })

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
