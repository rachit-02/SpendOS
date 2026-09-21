import { api, unwrap } from './api'
import type { Dashboard } from '@/types/dashboard'

export const dashboardService = {
  get: (month?: number, year?: number) => unwrap<Dashboard>(api.get('/dashboard', { params: month && year ? { month, year } : {} })),
}
