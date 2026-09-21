import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Copy, XCircle } from 'lucide-react'
import { importService } from '@/services/importService'
import { Spinner } from '@/components/ui/feedback'
import { Button } from '@/components/ui/button'
import { Table, TBody, Td, Th, THead, Tr } from '@/components/ui/table'
import type { ImportJob } from '@/types/imports'

const errorLabels: Record<string, string> = {
  DUPLICATE: 'Duplicate',
  MISSING_DATE: 'Missing date',
  INVALID_DATE: 'Invalid date',
  MISSING_AMOUNT: 'Missing amount',
  INVALID_AMOUNT: 'Invalid amount',
  MISSING_DESCRIPTION: 'Missing description',
}

function Stat({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: number; tone: string }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border p-4">
      <span className={tone}>{icon}</span>
      <div>
        <p className="text-2xl font-semibold tabular-nums">{value}</p>
        <p className="text-xs text-muted-foreground">{label}</p>
      </div>
    </div>
  )
}

function failureMessage(job: ImportJob): string {
  try {
    return (JSON.parse(job.errorSummary ?? '{}') as { error?: string }).error ?? 'The import failed.'
  } catch {
    return 'The import failed.'
  }
}

/** Final counts for an import plus the paginated list of skipped rows and why they were skipped. */
export function ImportSummary({ jobId }: { jobId: string }) {
  const [page, setPage] = useState(1)
  const job = useQuery({ queryKey: ['import', jobId], queryFn: () => importService.get(jobId) })
  const errors = useQuery({
    queryKey: ['import-errors', jobId, page],
    queryFn: () => importService.errors(jobId, page, 10),
    enabled: job.data?.importStatus === 'completed',
  })

  if (!job.data) return <Spinner />
  if (job.data.importStatus === 'failed') {
    return (
      <div role="alert" className="flex items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
        <XCircle className="mt-0.5 h-5 w-5 text-destructive" aria-hidden="true" />
        <p className="text-sm">{failureMessage(job.data)}</p>
      </div>
    )
  }

  const { importedCount, duplicateCount, invalidCount } = job.data
  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <Stat icon={<CheckCircle2 className="h-6 w-6" />} label="Imported" value={importedCount} tone="text-success" />
        <Stat icon={<Copy className="h-6 w-6" />} label="Duplicates skipped" value={duplicateCount} tone="text-muted-foreground" />
        <Stat icon={<AlertTriangle className="h-6 w-6" />} label="Invalid rows" value={invalidCount} tone="text-warning" />
      </div>

      {errors.data && errors.data.items.length > 0 && (
        <div className="space-y-2">
          <h3 className="text-sm font-medium">Skipped rows</h3>
          <div className="rounded-md border">
            <Table aria-label="Skipped rows">
              <THead>
                <tr>
                  <Th>Row</Th>
                  <Th>Reason</Th>
                  <Th>Details</Th>
                  <Th>Raw data</Th>
                </tr>
              </THead>
              <TBody>
                {errors.data.items.map((error) => (
                  <Tr key={error.id}>
                    <Td className="tabular-nums">{error.rowNumber}</Td>
                    <Td className="whitespace-nowrap">{errorLabels[error.errorCode] ?? error.errorCode}</Td>
                    <Td className="text-muted-foreground">{error.errorMessage}</Td>
                    <Td className="max-w-xs truncate font-mono text-xs">{error.rawData}</Td>
                  </Tr>
                ))}
              </TBody>
            </Table>
          </div>
          {errors.data.pagination.totalPages > 1 && (
            <div className="flex items-center justify-end gap-2 text-sm">
              <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <span className="tabular-nums">
                {page} / {errors.data.pagination.totalPages}
              </span>
              <Button variant="outline" size="sm" disabled={!errors.data.pagination.hasNext} onClick={() => setPage(page + 1)}>
                Next
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
