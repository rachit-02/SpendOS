import { api, downloadFile, unwrap } from './api'
import type { MonthlyAutopsy } from '@/types/reports'
import type { MonthValue } from '@/utils/months'

export const reportService = {
  autopsy: ({ month, year }: MonthValue) =>
    unwrap<MonthlyAutopsy>(api.get('/reports/monthly-autopsy', { params: { month, year } })),
  regenerate: ({ month, year }: MonthValue) =>
    unwrap<MonthlyAutopsy>(api.post('/reports/generate-autopsy', null, { params: { month, year } })),
  downloadPdf: ({ month, year }: MonthValue) =>
    downloadFile('/reports/monthly-autopsy/pdf', { month, year },
      `spendos-autopsy-${year}-${String(month).padStart(2, '0')}.pdf`),
}
