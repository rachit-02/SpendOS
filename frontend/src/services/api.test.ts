import axios from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { refreshAccessToken, toApiError } from './api'
import { useAuthStore } from '@/store/authStore'
import { AxiosError, AxiosHeaders } from 'axios'

describe('refreshAccessToken', () => {
  beforeEach(() => {
    useAuthStore.getState().setSession({
      accessToken: 'old-access',
      refreshToken: 'old-refresh',
      user: { userId: '1', email: 'a@example.com', fullName: 'A' },
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    useAuthStore.getState().clear()
  })

  it('shares one refresh request between concurrent callers and stores the new pair', async () => {
    const post = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { success: true, data: { accessToken: 'new-access', refreshToken: 'new-refresh', expiresIn: 3600 } },
    })

    const [first, second] = await Promise.all([refreshAccessToken(), refreshAccessToken()])

    expect(first).toBe('new-access')
    expect(second).toBe('new-access')
    expect(post).toHaveBeenCalledTimes(1)
    expect(post.mock.calls[0][1]).toEqual({ refreshToken: 'old-refresh' })
    expect(useAuthStore.getState().refreshToken).toBe('new-refresh')
  })

  it('clears the session when refresh fails', async () => {
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('401'))

    expect(await refreshAccessToken()).toBeNull()
    expect(useAuthStore.getState().accessToken).toBeNull()
  })
})

describe('toApiError', () => {
  it('extracts the backend error code and message', () => {
    const error = new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config: { headers: new AxiosHeaders() },
      data: { success: false, error: { code: 'DUPLICATE_EMAIL', message: 'Email is already registered' } },
    })

    const apiError = toApiError(error)

    expect(apiError.code).toBe('DUPLICATE_EMAIL')
    expect(apiError.status).toBe(400)
    expect(apiError.message).toBe('Email is already registered')
  })

  it('reports network failures clearly', () => {
    expect(toApiError(new AxiosError('Network Error')).code).toBe('NETWORK_ERROR')
  })
})
