import axios, { AxiosError, type AxiosResponse } from 'axios'
import type { ApiEnvelope, ApiErrorEnvelope, Paged } from '@/types/common'

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
