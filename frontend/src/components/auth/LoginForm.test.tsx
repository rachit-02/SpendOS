import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LoginForm } from './LoginForm'
import { authService } from '@/services/authService'
import { ApiError } from '@/services/api'
import { renderWithProviders } from '@/test/utils'

function renderLogin() {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginForm />} />
      <Route path="/dashboard" element={<p>Dashboard page</p>} />
    </Routes>,
    { route: '/login' },
  )
}

describe('LoginForm', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('validates fields before calling the API', async () => {
    const login = vi.spyOn(authService, 'login')
    renderLogin()

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
    expect(login).not.toHaveBeenCalled()
  })

  it('signs in and navigates to the dashboard', async () => {
    const login = vi.spyOn(authService, 'login').mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      expiresIn: 3600,
      user: { userId: '1', email: 'a@example.com', fullName: 'A' },
    })
    renderLogin()

    await userEvent.type(screen.getByLabelText('Email'), 'a@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'Tr0pic@lThund3r!')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Dashboard page')).toBeInTheDocument()
    expect(login).toHaveBeenCalledWith('a@example.com', 'Tr0pic@lThund3r!')
  })

  it('shows the server error for bad credentials', async () => {
    vi.spyOn(authService, 'login').mockRejectedValue(new ApiError('Invalid email or password', 'UNAUTHORIZED', 401))
    renderLogin()

    await userEvent.type(screen.getByLabelText('Email'), 'a@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
  })
  it('starts a demo without signing up', async () => {
    const demo = vi.spyOn(authService, 'startDemo').mockResolvedValue({
      accessToken: 'a', refreshToken: 'r', expiresIn: 3600,
      user: { userId: 'u-demo', email: 'demo-1@demo.spendos.invalid', fullName: 'Demo User', emailVerified: false,
        createdAt: '2026-09-21T00:00:00Z', updatedAt: '2026-09-21T00:00:00Z' },
    })
    renderLogin()

    await userEvent.click(screen.getByRole('button', { name: /Try the demo/ }))

    expect(demo).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('Dashboard page')).toBeInTheDocument()
  })
})
