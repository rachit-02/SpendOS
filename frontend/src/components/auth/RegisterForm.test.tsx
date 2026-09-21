import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RegisterForm } from './RegisterForm'
import { authService } from '@/services/authService'
import { ApiError } from '@/services/api'
import { renderWithProviders } from '@/test/utils'

async function fillForm(password = 'Tr0pic@lThund3r!') {
  await userEvent.type(screen.getByLabelText('Full name'), 'Asha Rao')
  await userEvent.type(screen.getByLabelText('Email'), 'asha@example.com')
  await userEvent.type(screen.getByLabelText('Password'), password)
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('RegisterForm', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('blocks weak passwords client-side', async () => {
    const register = vi.spyOn(authService, 'register')
    renderWithProviders(<RegisterForm />)

    await fillForm('short')

    expect(screen.getByText('Password does not meet the requirements')).toBeInTheDocument()
    expect(register).not.toHaveBeenCalled()
  })

  it('maps DUPLICATE_EMAIL to the email field', async () => {
    vi.spyOn(authService, 'register').mockRejectedValue(
      new ApiError('Email is already registered', 'DUPLICATE_EMAIL', 400),
    )
    renderWithProviders(<RegisterForm />)

    await fillForm()

    expect(await screen.findByText('An account with this email already exists')).toBeInTheDocument()
  })

  it('registers then signs in', async () => {
    const register = vi.spyOn(authService, 'register').mockResolvedValue({
      userId: '1',
      email: 'asha@example.com',
      fullName: 'Asha Rao',
    })
    const login = vi.spyOn(authService, 'login').mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      expiresIn: 3600,
      user: { userId: '1', email: 'asha@example.com', fullName: 'Asha Rao' },
    })
    renderWithProviders(<RegisterForm />)

    await fillForm()

    expect(register).toHaveBeenCalledWith({ fullName: 'Asha Rao', email: 'asha@example.com', password: 'Tr0pic@lThund3r!' })
    expect(login).toHaveBeenCalledWith('asha@example.com', 'Tr0pic@lThund3r!')
  })
})
