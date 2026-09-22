import { formatBytes } from '@/utils/formatters'

export const MAX_UPLOAD_BYTES = 50 * 1024 * 1024

export function isPdfFile(file: File): boolean {
  return file.name.toLowerCase().endsWith('.pdf') || file.type === 'application/pdf'
}

/** Returns an error message when the file cannot be imported, otherwise null. CSV and PDF statements are accepted. */
export function validateStatementFile(file: File): string | null {
  const name = file.name.toLowerCase()
  if (!name.endsWith('.csv') && !name.endsWith('.txt') && !name.endsWith('.pdf')) {
    return 'Only CSV or PDF bank statements (.csv, .pdf) can be imported.'
  }
  if (file.size === 0) return 'The file is empty.'
  if (file.size > MAX_UPLOAD_BYTES) return `The file is ${formatBytes(file.size)}; the limit is 50 MB.`
  return null
}
