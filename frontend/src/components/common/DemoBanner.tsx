import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { usePreferences } from '@/hooks/useReferenceData'
import { authService } from '@/services/authService'
import { errorMessage } from '@/services/api'

/** Shown on demo accounts: explains the data is synthetic and lets the visitor start over. */
export function DemoBanner() {
  const queryClient = useQueryClient()
  const { data: preferences } = usePreferences()
  const reset = useMutation({
    mutationFn: authService.resetDemo,
    onSuccess: () => queryClient.invalidateQueries(),
  })
  if (!preferences?.demoMode) return null
  return (
    <div role="region" aria-label="Demo account"
      className="flex flex-wrap items-center justify-between gap-2 border-b bg-accent px-4 py-2 text-sm text-accent-foreground print:hidden">
      <p>
        You are exploring a <strong>demo account</strong> with made-up data. It is deleted after a day.{' '}
        <Link to="/register" className="font-medium underline">Create your own account</Link>
      </p>
      <div className="flex items-center gap-2">
        {reset.isError && <span role="alert" className="text-destructive">{errorMessage(reset.error)}</span>}
        {reset.isSuccess && <span role="status">Demo data restored.</span>}
        <Button size="sm" variant="outline" disabled={reset.isPending} onClick={() => reset.mutate()}>
          <RotateCcw className="h-4 w-4" aria-hidden="true" /> {reset.isPending ? 'Resetting…' : 'Reset demo'}
        </Button>
      </div>
    </div>
  )
}
