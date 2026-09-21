import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { importService } from '@/services/importService'
import { Badge, EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { Button } from '@/components/ui/button'
import { Table, TBody, Td, Th, THead, Tr } from '@/components/ui/table'
import { formatBytes, formatDateTime } from '@/utils/formatters'
import type { ImportStatus } from '@/types/imports'

const statusTone: Record<ImportStatus, 'success' | 'danger' | 'warning' | 'neutral'> = {
  completed: 'success',
  failed: 'danger',
  processing: 'warning',
  pending: 'neutral',
}

export function ImportHistory({ onSelect }: { onSelect?: (jobId: string) => void }) {
  const [page, setPage] = useState(1)
  const { data, isLoading, isError } = useQuery({
    queryKey: ['import-history', page],
    queryFn: () => importService.history(page, 10),
  })

  if (isLoading) return <Spinner />
  if (isError) return <ErrorState message="Could not load import history." />
  if (!data || data.items.length === 0) {
    return <EmptyState title="No imports yet" description="Your uploaded statements will appear here." />
  }

  return (
    <div className="space-y-3">
      <div className="rounded-md border">
        <Table aria-label="Import history">
          <THead>
            <tr>
              <Th>File</Th>
              <Th>Uploaded</Th>
              <Th>Status</Th>
              <Th className="text-right">Imported</Th>
              <Th className="text-right">Duplicates</Th>
              <Th className="text-right">Invalid</Th>
            </tr>
          </THead>
          <TBody>
            {data.items.map((job) => (
              <Tr key={job.id} className={onSelect ? 'cursor-pointer' : undefined} onClick={() => onSelect?.(job.id)}>
                <Td>
                  <p className="font-medium">{job.fileName}</p>
                  <p className="text-xs text-muted-foreground">{formatBytes(job.fileSizeBytes)}</p>
                </Td>
                <Td className="whitespace-nowrap text-muted-foreground">{formatDateTime(job.createdAt)}</Td>
                <Td>
                  <Badge tone={statusTone[job.importStatus]} className="capitalize">
                    {job.importStatus}
                  </Badge>
                </Td>
                <Td className="text-right tabular-nums">{job.importedCount}</Td>
                <Td className="text-right tabular-nums">{job.duplicateCount}</Td>
                <Td className="text-right tabular-nums">{job.invalidCount}</Td>
              </Tr>
            ))}
          </TBody>
        </Table>
      </div>
      {data.pagination.totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 text-sm">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>
            Previous
          </Button>
          <span className="tabular-nums">
            {page} / {data.pagination.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={!data.pagination.hasNext} onClick={() => setPage(page + 1)}>
            Next
          </Button>
        </div>
      )}
    </div>
  )
}
