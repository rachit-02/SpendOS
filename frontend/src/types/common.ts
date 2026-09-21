/** Envelope types mirroring API_DESIGN.md. */
export interface Pagination {
  totalItems: number
  totalPages: number
  currentPage: number
  pageSize: number
  hasNext: boolean
  hasPrevious: boolean
}

export interface ApiEnvelope<T> {
  success: true
  data: T
  pagination?: Pagination
  timestamp: string
  requestId: string
}

export interface ApiErrorBody {
  code: string
  message: string
  details?: Record<string, unknown>
}

export interface ApiErrorEnvelope {
  success: false
  error: ApiErrorBody
  timestamp: string
  requestId: string
}

export interface Paged<T> {
  items: T[]
  pagination: Pagination
}

/** Decimal amounts arrive as JSON numbers; they are display-only on the client. */
export type Amount = number
