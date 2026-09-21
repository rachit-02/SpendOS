import { api, cleanParams, downloadFile, toApiError, unwrap, unwrapPage } from './api'
import type { PaymentMethod, Transaction, TransactionType } from '@/types/transaction'

export interface TransactionQuery {
  page?: number
  pageSize?: number
  startDate?: string
  endDate?: string
  categoryId?: string
  merchantId?: string
  accountId?: string
  minAmount?: string
  maxAmount?: string
  transactionType?: TransactionType | ''
  paymentMethod?: PaymentMethod | ''
  searchText?: string
  sortBy?: 'date' | 'amount' | 'merchant'
  sortOrder?: 'asc' | 'desc'
}

export interface TransactionInput {
  accountId: string
  merchantName: string
  categoryId?: string
  subcategoryId?: string
  amount: number
  transactionType: TransactionType
  transactionDate: string
  description?: string
  paymentMethod?: PaymentMethod
}

export interface Suggestions {
  merchants: { merchantId: string; merchantName: string; count: number }[]
  categories: { categoryId: string; categoryName: string; count: number }[]
  searchTerms: string[]
}

export const transactionService = {
  list: (query: TransactionQuery) => unwrapPage<Transaction>(api.get('/transactions', { params: cleanParams(query) })),
  get: (id: string) => unwrap<Transaction>(api.get(`/transactions/${id}`)),
  create: (input: TransactionInput) => unwrap<Transaction>(api.post('/transactions', input)),
  update: (id: string, input: Partial<TransactionInput> & { isRecurring?: boolean }) =>
    unwrap<Transaction>(api.put(`/transactions/${id}`, input)),
  remove: async (id: string) => {
    try {
      await api.delete(`/transactions/${id}`)
    } catch (error) {
      throw toApiError(error)
    }
  },
  bulkUpdate: (transactionIds: string[], updates: { categoryId?: string; subcategoryId?: string }) =>
    unwrap<{ updated: number; failed: number }>(api.post('/transactions/bulk-update', { transactionIds, updates })),
  bulkDelete: (transactionIds: string[]) =>
    unwrap<{ deleted: number; failed: number }>(api.post('/transactions/bulk-delete', { transactionIds })),
  suggestions: (q: string) => unwrap<Suggestions>(api.get('/transactions/suggestions', { params: cleanParams({ q }) })),

  /** Downloads a CSV of the given selection, or of everything matching the filters. */
  exportCsv(query: TransactionQuery, ids?: string[]): Promise<void> {
    const { page, pageSize, sortBy, sortOrder, ...filters } = query
    return downloadFile('/transactions/export', { ...filters, ids: ids?.join(',') },
      `spendos-transactions-${new Date().toISOString().slice(0, 10)}.csv`)
  },
}
