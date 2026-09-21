import { api, toApiError, unwrap } from './api'
import type { Budget, BudgetAlert, BudgetInput, BudgetProgress, RecurringPayment } from '@/types/budgets'

export const budgetService = {
  list: (active?: boolean) => unwrap<Budget[]>(api.get('/budgets', { params: active === undefined ? {} : { active } })),
  get: (id: string) => unwrap<Budget>(api.get(`/budgets/${id}`)),
  progress: (id: string) => unwrap<BudgetProgress>(api.get(`/budgets/${id}/progress`)),
  alerts: () => unwrap<BudgetAlert[]>(api.get('/budgets/alerts')),
  create: (input: BudgetInput) => unwrap<Budget>(api.post('/budgets', input)),
  update: (id: string, input: BudgetInput) => unwrap<Budget>(api.put(`/budgets/${id}`, input)),
  remove: async (id: string) => {
    try {
      await api.delete(`/budgets/${id}`)
    } catch (error) {
      throw toApiError(error)
    }
  },
}

export const recurringService = {
  list: (confirmed: 'all' | 'true' | 'false' | 'dismissed' = 'all', sortBy = 'nextDate') =>
    unwrap<RecurringPayment[]>(api.get('/recurring', { params: { confirmed, sortBy } })),
  detect: () => unwrap<{ detected: number; created: number; updated: number; active: number }>(api.post('/recurring/detect')),
  confirm: (id: string) => unwrap<{ id: string }>(api.post(`/recurring/${id}/confirm`)),
  reject: (id: string) => unwrap<{ id: string }>(api.post(`/recurring/${id}/reject`)),
}
