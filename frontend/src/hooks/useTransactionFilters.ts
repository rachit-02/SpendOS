import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { TransactionQuery } from '@/services/transactionService'

const FILTER_KEYS = [
  'startDate',
  'endDate',
  'categoryId',
  'merchantId',
  'accountId',
  'minAmount',
  'maxAmount',
  'transactionType',
  'paymentMethod',
  'searchText',
  'sortBy',
  'sortOrder',
  'page',
] as const

/**
 * Transaction filters live in the URL so a filtered view can be bookmarked, shared, and reached from
 * other pages (e.g. clicking a category on the dashboard opens /transactions?categoryId=...).
 */
export function useTransactionFilters() {
  const [params, setParams] = useSearchParams()

  const query: TransactionQuery = useMemo(() => {
    const result: Record<string, string | number> = { page: 1, pageSize: 25, sortBy: 'date', sortOrder: 'desc' }
    for (const key of FILTER_KEYS) {
      const value = params.get(key)
      if (value) result[key] = key === 'page' ? Math.max(1, Number(value) || 1) : value
    }
    return result as TransactionQuery
  }, [params])

  /** Merges changes into the URL; any change other than paging resets to page 1. */
  const update = useCallback(
    (changes: Partial<Record<(typeof FILTER_KEYS)[number], string | number | undefined>>) => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current)
          for (const [key, value] of Object.entries(changes)) {
            if (value === undefined || value === '' || value === null) next.delete(key)
            else next.set(key, String(value))
          }
          if (!('page' in changes)) next.delete('page')
          return next
        },
        { replace: true },
      )
    },
    [setParams],
  )

  const clear = useCallback(() => setParams(new URLSearchParams(), { replace: true }), [setParams])

  const activeCount = FILTER_KEYS.filter(
    (key) => !['sortBy', 'sortOrder', 'page'].includes(key) && params.get(key),
  ).length

  return { query, update, clear, activeCount }
}
