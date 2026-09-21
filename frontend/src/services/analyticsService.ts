import { api, downloadFile, unwrap } from './api'
import type { CategoryTrend, MerchantStat, MonthComparison, MonthlyAnalytics, Trends } from '@/types/analytics'
import type { MonthValue } from '@/utils/months'

export const analyticsService = {
  monthly: ({ month, year }: MonthValue) => unwrap<MonthlyAnalytics>(api.get('/analytics/monthly', { params: { month, year } })),
  categoryTrend: (categoryId: string, months: number) =>
    unwrap<CategoryTrend>(api.get('/analytics/categories/trends', { params: { categoryId, months } })),
  topMerchants: (params: { startDate?: string; endDate?: string; categoryId?: string; limit?: number }) =>
    unwrap<MerchantStat[]>(api.get('/analytics/merchants/top', { params })),
  trends: (months: number) => unwrap<Trends>(api.get('/analytics/trends', { params: { months } })),
  compare: (first: MonthValue, second: MonthValue) =>
    unwrap<MonthComparison>(
      api.get('/analytics/compare', {
        params: { month1: first.month, year1: first.year, month2: second.month, year2: second.year },
      }),
    ),
  exportMonthly: ({ month, year }: MonthValue, format: 'csv' | 'pdf') =>
    downloadFile('/analytics/monthly/export', { month, year, format },
      `spendos-analytics-${year}-${String(month).padStart(2, '0')}.${format}`),
}
