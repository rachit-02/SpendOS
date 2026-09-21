import { formatBytes } from '@/utils/formatters'

export const MAX_UPLOAD_BYTES = 50 * 1024 * 1024

/** Returns an error message when the file cannot be imported, otherwise null. */
export function validateCsvFile(file: File): string | null {
  const name = file.name.toLowerCase()
  if (!name.endsWith('.csv') && !name.endsWith('.txt')) return 'Only CSV files (.csv) can be imported.'
  if (file.size === 0) return 'The file is empty.'
  if (file.size > MAX_UPLOAD_BYTES) return `The file is ${formatBytes(file.size)}; the limit is 50 MB.`
  return null
}
