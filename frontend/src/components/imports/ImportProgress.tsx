import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { importService } from '@/services/importService'
import type { ImportStatusResponse } from '@/types/imports'

interface Props {
  jobId: string
  onFinished: (status: ImportStatusResponse) => void
  pollIntervalMs?: number
}

/** Polls the job status until it completes or fails, showing an accessible progress bar. */
export function ImportProgress({ jobId, onFinished, pollIntervalMs = 800 }: Props) {
  const { data } = useQuery({
    queryKey: ['import-status', jobId],
    queryFn: () => importService.status(jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'completed' || status === 'failed' ? false : pollIntervalMs
    },
  })

  useEffect(() => {
    if (data && (data.status === 'completed' || data.status === 'failed')) onFinished(data)
  }, [data, onFinished])

  const percentage = data?.progress.percentage ?? 0
  return (
    <div className="space-y-2">
      <div className="flex justify-between text-sm">
        <span className="font-medium">{data?.status === 'pending' ? 'Queued…' : 'Importing transactions…'}</span>
        <span className="tabular-nums text-muted-foreground">
          {data ? `${data.progress.processed} / ${data.progress.total} rows` : ''}
        </span>
      </div>
      <div
        role="progressbar"
        aria-label="Import progress"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percentage}
        className="h-2.5 w-full overflow-hidden rounded-full bg-muted"
      >
        <div className="h-full rounded-full bg-primary transition-all duration-500" style={{ width: `${percentage}%` }} />
      </div>
    </div>
  )
}
