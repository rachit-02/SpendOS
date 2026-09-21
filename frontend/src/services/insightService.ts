import { api, unwrap } from './api'
import type { Anomaly, Insight, InsightType } from '@/types/insights'

export const insightService = {
  list: (type: InsightType | 'all' = 'all', period = 'current_month') =>
    unwrap<Insight[]>(api.get('/insights', { params: { type, period } })),
  get: (id: string) => unwrap<Insight>(api.get(`/insights/${id}`)),
  anomalies: (sensitivityLevel: 'low' | 'medium' | 'high', categoryId?: string) =>
    unwrap<Anomaly[]>(api.get('/insights/anomalies', { params: { sensitivityLevel, categoryId } })),
  history: (months = 6) => unwrap<Insight[]>(api.get('/insights/history', { params: { months } })),
  regenerate: (period = 'current_month') => unwrap<Insight[]>(api.post('/insights/generate', null, { params: { period } })),
}
