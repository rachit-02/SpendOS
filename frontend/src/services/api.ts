import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { ApiEnvelope, ApiErrorEnvelope, Paged } from '@/types/common'
import type { RefreshResponse } from '@/types/auth'
import { useAuthStore } from '@/store/authStore'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

/** Error thrown by service functions: carries the backend's error code and HTTP status. */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly status: number,
    public readonly details?: Record<string, unknown>,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30_000,
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshInFlight: Promise<string | null> | null = null

/**
 * Exchanges the stored refresh token for a new token pair. Concurrent 401s share one request so a
 * single-use refresh token is never presented twice.
 */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    const refreshToken = useAuthStore.getState().refreshToken
    refreshInFlight = (async () => {
      if (!refreshToken) return null
      try {
        const response = await axios.post<ApiEnvelope<RefreshResponse>>(
          `${API_BASE_URL}/auth/refresh`,
          { refreshToken },
          { headers: { 'Content-Type': 'application/json' } },
        )
        const { accessToken, refreshToken: next } = response.data.data
        useAuthStore.getState().setTokens({ accessToken, refreshToken: next })
        return accessToken
      } catch {
        useAuthStore.getState().clear()
        return null
      }
    })().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined
    const isAuthCall = config?.url?.startsWith('/auth/')
    if (error.response?.status === 401 && config && !config._retried && !isAuthCall) {
      config._retried = true
      const token = await refreshAccessToken()
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
        return api(config)
      }
    }
    return Promise.reject(error)
  },
)

/** Converts any axios failure into an {@link ApiError} with the backend's code when available. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiErrorEnvelope | undefined
    if (body && body.success === false && body.error) {
      return new ApiError(body.error.message, body.error.code, error.response?.status ?? 0, body.error.details)
    }
    if (!error.response) {
      return new ApiError('Cannot reach the SpendOS server. Check your connection.', 'NETWORK_ERROR', 0)
    }
    return new ApiError(error.message, 'HTTP_ERROR', error.response.status)
  }
  return new ApiError('Something went wrong', 'UNKNOWN', 0)
}

export async function unwrap<T>(request: Promise<AxiosResponse<ApiEnvelope<T>>>): Promise<T> {
  try {
    const response = await request
    return response.data.data
  } catch (error) {
    throw toApiError(error)
  }
}

export async function unwrapPage<T>(request: Promise<AxiosResponse<ApiEnvelope<T[]>>>): Promise<Paged<T>> {
  try {
    const response = await request
    return {
      items: response.data.data,
      pagination: response.data.pagination ?? {
        totalItems: response.data.data.length,
        totalPages: 1,
        currentPage: 1,
        pageSize: response.data.data.length,
        hasNext: false,
        hasPrevious: false,
      },
    }
  } catch (error) {
    throw toApiError(error)
  }
}

/** Removes undefined/empty values so they are not sent as query parameters. */
export function cleanParams<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  ) as Partial<T>
}

/** Human-readable message for any error thrown by a service call. */
export function errorMessage(error: unknown): string {
  return toApiError(error).message
}

/** Downloads a file response (CSV/PDF) and saves it with the given name. */
export async function downloadFile(url: string, params: Record<string, unknown>, fileName: string): Promise<void> {
  try {
    const response = await api.get(url, { params: cleanParams(params), responseType: 'blob' })
    const objectUrl = URL.createObjectURL(response.data as Blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(objectUrl)
  } catch (error) {
    throw toApiError(error)
  }
}
