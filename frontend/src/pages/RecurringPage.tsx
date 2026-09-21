import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, RefreshCw, X } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge, EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { recurringService } from '@/services/budgetService'
import { errorMessage } from '@/services/api'
import { formatCurrency, formatDate } from '@/utils/formatters'
import type { RecurringPayment } from '@/types/budgets'

const frequencyLabels: Record<RecurringPayment['frequency'], string> = {
  daily: 'Daily', weekly: 'Weekly', biweekly: 'Every 2 weeks', monthly: 'Monthly', quarterly: 'Quarterly', annual: 'Yearly',
}

function RecurringRow({ payment, onConfirm, onDismiss, busy }: {
  payment: RecurringPayment
  onConfirm?: () => void
  onDismiss?: () => void
  busy?: boolean
}) {
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 py-3">
      <div className="min-w-0">
        <p className="font-medium">{payment.merchantName}</p>
        <p className="text-xs text-muted-foreground">
          {frequencyLabels[payment.frequency]} · seen {payment.occurrencesCount} times
          {payment.nextExpectedDate && payment.isActive ? ` · next around ${formatDate(payment.nextExpectedDate, 'd MMM')}` : ''}
        </p>
      </div>
      <div className="flex items-center gap-3">
        <div className="text-right">
          <p className="font-medium tabular-nums">{formatCurrency(payment.typicalAmount, payment.currencyCode)}</p>
          <p className="text-xs text-muted-foreground">{Math.round(payment.confidence * 100)}% confident</p>
        </div>
        {onConfirm && (
          <Button size="sm" variant="secondary" disabled={busy} onClick={onConfirm} aria-label={`Confirm ${payment.merchantName}`}>
            <Check className="h-4 w-4" aria-hidden="true" /> Confirm
          </Button>
        )}
        {onDismiss && (
          <Button size="sm" variant="ghost" disabled={busy} onClick={onDismiss} aria-label={`Not recurring: ${payment.merchantName}`}>
            <X className="h-4 w-4" aria-hidden="true" /> Not recurring
          </Button>
        )}
      </div>
    </li>
  )
}

export default function RecurringPage() {
  const queryClient = useQueryClient()
  const { data, isLoading, isError, error } = useQuery({ queryKey: ['recurring'], queryFn: () => recurringService.list('all') })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['recurring'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }
  const detect = useMutation({ mutationFn: recurringService.detect, onSuccess: invalidate })
  const confirm = useMutation({ mutationFn: recurringService.confirm, onSuccess: invalidate })
  const reject = useMutation({ mutationFn: recurringService.reject, onSuccess: invalidate })
  const busy = confirm.isPending || reject.isPending

  const pending = data?.filter((p) => p.status === 'pending') ?? []
  const confirmed = data?.filter((p) => p.status === 'confirmed') ?? []
  const lapsed = data?.filter((p) => p.status === 'lapsed') ?? []
  const monthlyTotal = [...pending, ...confirmed].reduce((sum, p) => sum + (p.monthlyCost ?? 0), 0)
  const currency = data?.[0]?.currencyCode ?? 'INR'

  return (
    <>
      <PageHeader
        title="Recurring payments"
        description="Subscriptions and bills we found in your transactions. We never cancel anything for you."
        actions={
          <Button variant="outline" onClick={() => detect.mutate()} disabled={detect.isPending}>
            <RefreshCw className={detect.isPending ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} aria-hidden="true" /> Scan transactions
          </Button>
        }
      />
      {detect.isSuccess && (
        <p role="status" className="mb-4 text-sm text-muted-foreground">
          Scan complete: {detect.data.detected} recurring payment{detect.data.detected === 1 ? '' : 's'} found.
        </p>
      )}
      {isLoading && <Spinner />}
      {isError && <ErrorState message={errorMessage(error)} />}
      {data && data.length === 0 && (
        <EmptyState title="No recurring payments found yet"
          description="Import a few months of statements, then scan again. Payments need to repeat at least 3 times to be detected."
          action={<Button onClick={() => detect.mutate()}>Scan now</Button>} />
      )}
      {data && data.length > 0 && (
        <div className="grid gap-6">
          <Card>
            <CardContent className="flex items-center justify-between p-5">
              <span className="text-sm text-muted-foreground">Estimated recurring cost per month</span>
              <span className="text-2xl font-semibold tabular-nums">{formatCurrency(monthlyTotal, currency)}</span>
            </CardContent>
          </Card>
          {pending.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">Needs your review <Badge tone="warning">{pending.length}</Badge></CardTitle>
                <CardDescription>Confirm the ones that are real subscriptions or bills.</CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="divide-y" aria-label="Recurring payments to review">
                  {pending.map((p) => (
                    <RecurringRow key={p.id} payment={p} busy={busy} onConfirm={() => confirm.mutate(p.id)} onDismiss={() => reject.mutate(p.id)} />
                  ))}
                </ul>
              </CardContent>
            </Card>
          )}
          {confirmed.length > 0 && (
            <Card>
              <CardHeader><CardTitle>Confirmed</CardTitle></CardHeader>
              <CardContent>
                <ul className="divide-y" aria-label="Confirmed recurring payments">
                  {confirmed.map((p) => <RecurringRow key={p.id} payment={p} busy={busy} onDismiss={() => reject.mutate(p.id)} />)}
                </ul>
              </CardContent>
            </Card>
          )}
          {lapsed.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>Possibly cancelled</CardTitle>
                <CardDescription>Not seen since the expected date.</CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="divide-y" aria-label="Lapsed recurring payments">
                  {lapsed.map((p) => <RecurringRow key={p.id} payment={p} />)}
                </ul>
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </>
  )
}

