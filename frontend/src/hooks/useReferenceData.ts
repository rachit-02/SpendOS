import { useQuery } from '@tanstack/react-query'
import { accountService, categoryService } from '@/services/accountService'
import { userService } from '@/services/userService'

export function useAccounts() {
  return useQuery({ queryKey: ['accounts'], queryFn: accountService.list })
}

/** Categories are static reference data; cache them for the whole session. */
export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: categoryService.list, staleTime: Infinity })
}

export function usePreferences() {
  return useQuery({ queryKey: ['preferences'], queryFn: userService.preferences, staleTime: 5 * 60_000 })
}

/** The signed-in user's display currency (INR until preferences load). */
export function useCurrency(): string {
  return usePreferences().data?.currencyCode ?? 'INR'
}
