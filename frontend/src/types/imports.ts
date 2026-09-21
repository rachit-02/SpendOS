export type ImportStatus = 'pending' | 'processing' | 'completed' | 'failed'

export interface ImportJob {
  id: string
  fileName: string
  fileSizeBytes?: number
  accountId?: string
  importStatus: ImportStatus
  importedCount: number
  duplicateCount: number
  invalidCount: number
  totalRowsProcessed: number
  totalRows: number
  errorSummary?: string
  createdAt: string
  startedAt?: string
  completedAt?: string
}

export interface ImportStatusResponse {
  importJobId: string
  status: ImportStatus
  progress: { processed: number; total: number; percentage: number }
}

export interface ImportRowError {
  id: string
  rowNumber: number
  rawData: string
  errorMessage: string
  errorCode: string
}

export interface UploadResponse {
  importJobId: string
  status: ImportStatus
  message: string
}
