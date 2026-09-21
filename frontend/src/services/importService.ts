import { api, unwrap, unwrapPage } from './api'
import type { ImportJob, ImportRowError, ImportStatusResponse, UploadResponse } from '@/types/imports'

export const importService = {
  upload: (file: File, accountId?: string, dateFormat?: string) => {
    const form = new FormData()
    form.append('file', file)
    if (accountId) form.append('accountId', accountId)
    if (dateFormat) form.append('dateFormat', dateFormat)
    return unwrap<UploadResponse>(
      api.post('/imports/upload', form, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120_000 }),
    )
  },
  status: (jobId: string) => unwrap<ImportStatusResponse>(api.get(`/imports/${jobId}/status`)),
  get: (jobId: string) => unwrap<ImportJob>(api.get(`/imports/${jobId}`)),
  errors: (jobId: string, page = 1, pageSize = 20) =>
    unwrapPage<ImportRowError>(api.get(`/imports/${jobId}/errors`, { params: { page, pageSize } })),
  history: (page = 1, pageSize = 10) =>
    unwrapPage<ImportJob>(api.get('/imports/history', { params: { page, pageSize } })),
}
