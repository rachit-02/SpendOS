import { api, unwrap } from './api'
import type { MonthValue } from '@/utils/months'
import type { HealthExplanation, HealthHistoryPoint, HealthMetrics } from '@/types/health'

export const healthMetricsService = {
  current: () => unwrap<HealthMetrics>(api.get('/health-metrics')),
  history: (months = 12) => unwrap<HealthHistoryPoint[]>(api.get('/health-metrics/history', { params: { months } })),
  explanation: ({ month, year }: MonthValue) =>
    unwrap<HealthExplanation>(api.get('/health-metrics/explanation', { params: { month, year } })),
}
