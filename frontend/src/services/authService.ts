import { api, unwrap } from './api'
import { useAuthStore } from '@/store/authStore'
import type { LoginResponse, User } from '@/types/auth'

export interface RegisterInput {
  email: string
  password: string
  fullName: string
}

export const authService = {
  register: (input: RegisterInput) => unwrap<User>(api.post('/auth/register', input)),

  async login(email: string, password: string): Promise<LoginResponse> {
    const response = await unwrap<LoginResponse>(api.post('/auth/login', { email, password }))
    useAuthStore.getState().setSession(response)
    return response
  },

  /** Creates a demo account filled with synthetic data and signs into it. */
  async startDemo(): Promise<LoginResponse> {
    const response = await unwrap<LoginResponse>(api.post('/auth/demo'))
    useAuthStore.getState().setSession(response)
    return response
  },

  resetDemo: () => unwrap<{ message: string }>(api.post('/demo/reset')),

  /** Revokes tokens server-side; local state is cleared even if the server call fails. */
  async logout(): Promise<void> {
    const { refreshToken } = useAuthStore.getState()
    try {
      await api.post('/auth/logout', { refreshToken })
    } catch {
      // Already expired/revoked tokens are fine; the local session is dropped regardless.
    } finally {
      useAuthStore.getState().clear()
    }
  },
}
