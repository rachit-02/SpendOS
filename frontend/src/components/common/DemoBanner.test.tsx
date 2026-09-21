import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DemoBanner } from './DemoBanner'
import { authService } from '@/services/authService'
import { userService } from '@/services/userService'
import { renderWithProviders } from '@/test/utils'
import type { Preferences } from '@/types/auth'

const preferences = (demoMode: boolean): Preferences => ({
  currencyCode: 'INR', timezone: 'Asia/Kolkata', fiscalYearStartMonth: 1, theme: 'light', language: 'en',
  financialHealthScoreEnabled: true, demoMode, emailReportsEnabled: false, emailAlertsEnabled: false,
})

describe('DemoBanner', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('is hidden for real accounts', async () => {
    const loaded = vi.spyOn(userService, 'preferences').mockResolvedValue(preferences(false))
    renderWithProviders(<DemoBanner />)
    await vi.waitFor(() => expect(loaded).toHaveBeenCalled())
    expect(screen.queryByRole('region', { name: 'Demo account' })).not.toBeInTheDocument()
  })

  it('explains demo data and resets it', async () => {
    vi.spyOn(userService, 'preferences').mockResolvedValue(preferences(true))
    const reset = vi.spyOn(authService, 'resetDemo').mockResolvedValue({ message: 'ok' })
    renderWithProviders(<DemoBanner />)

    expect(await screen.findByRole('region', { name: 'Demo account' })).toHaveTextContent('made-up data')
    await userEvent.click(screen.getByRole('button', { name: /Reset demo/ }))
    expect(reset).toHaveBeenCalled()
    expect(await screen.findByRole('status')).toHaveTextContent('Demo data restored.')
  })
})
