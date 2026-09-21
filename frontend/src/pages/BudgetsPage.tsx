import { lazy, Suspense, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Plus } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { BudgetCard } from '@/components/budgets/BudgetCard'
import { BudgetForm } from '@/components/budgets/BudgetForm'
import { budgetService } from '@/services/budgetService'
import { errorMessage } from '@/services/api'
import { useCurrency } from '@/hooks/useReferenceData'
import { formatCurrency } from '@/utils/formatters'
import type { Budget, BudgetInput } from '@/types/budgets'

const BudgetVsActualChart = lazy(() => import('@/components/budgets/BudgetVsActualChart'))

const paceText = {
  not_started: 'This budget has not started yet.',
  ahead: 'You are spending faster than planned.',
  on_track: 'Your spending is on pace.',
  behind: 'You are spending slower than planned.',
}

function BudgetDetail({ budgetId }: { budgetId: string }) {
  const { data } = useQuery({ queryKey: ['budget-progress', budgetId], queryFn: () => budgetService.progress(budgetId) })
  if (!data) return <Spinner />
  const currency = data.budget.currencyCode
  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="rounded-lg border p-3">
          <p className="text-xs text-muted-foreground">Days left</p>
          <p className="text-lg font-semibold">{data.daysRemaining} of {data.daysTotal}</p>
        </div>
        <div className="rounded-lg border p-3">
          <p className="text-xs text-muted-foreground">Can still spend per day</p>
          <p className="text-lg font-semibold">{formatCurrency(data.dailyAllowanceRemaining, currency)}</p>
        </div>
        <div className="rounded-lg border p-3">
          <p className="text-xs text-muted-foreground">Projected at period end</p>
          <p className={data.projectedToExceed ? 'text-lg font-semibold text-destructive' : 'text-lg font-semibold'}>
            {formatCurrency(data.projectedSpend, currency)}
          </p>
        </div>
      </div>
      <p className="text-sm text-muted-foreground">{paceText[data.pace]}</p>
      <Suspense fallback={<Spinner label="Loading chart" />}>
        <BudgetVsActualChart budget={data.budget} />
      </Suspense>
    </div>
  )
}

export default function BudgetsPage() {
  const queryClient = useQueryClient()
  const currency = useCurrency()
  const [editing, setEditing] = useState<Budget | 'new' | null>(null)
  const [selected, setSelected] = useState<Budget | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Budget | null>(null)

  const budgets = useQuery({ queryKey: ['budgets'], queryFn: () => budgetService.list() })
  const alerts = useQuery({ queryKey: ['budget-alerts'], queryFn: budgetService.alerts })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['budgets'] })
    queryClient.invalidateQueries({ queryKey: ['budget-alerts'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const save = useMutation({
    mutationFn: (input: BudgetInput) =>
      editing && editing !== 'new' ? budgetService.update(editing.id, input) : budgetService.create(input),
    onSuccess: () => {
      setEditing(null)
      invalidate()
    },
  })
  const remove = useMutation({
    mutationFn: (id: string) => budgetService.remove(id),
    onSuccess: () => {
      setConfirmDelete(null)
      invalidate()
    },
  })

  return (
    <>
      <PageHeader
        title="Budgets"
        description="Set spending limits and get warned before you go over."
        actions={<Button onClick={() => { save.reset(); setEditing('new') }}><Plus className="h-4 w-4" aria-hidden="true" /> New budget</Button>}
      />

      {alerts.data && alerts.data.length > 0 && (
        <Card className="mb-6 border-warning/40 bg-warning/5">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <AlertTriangle className="h-4 w-4 text-warning" aria-hidden="true" /> Budget alerts
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-1 text-sm" aria-label="Budget alerts">
              {alerts.data.map((alert) => (
                <li key={`${alert.budgetId}-${alert.categoryId ?? 'all'}`} className={alert.level === 'exceeded' ? 'text-destructive' : ''}>
                  {alert.message}
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      {budgets.isLoading && <Spinner />}
      {budgets.isError && <ErrorState message={errorMessage(budgets.error)} />}
      {budgets.data && budgets.data.length === 0 && (
        <EmptyState
          title="No budgets yet"
          description="A budget helps you keep spending in check. Start with a monthly limit for your biggest category."
          action={<Button onClick={() => setEditing('new')}>Create your first budget</Button>}
        />
      )}
      {budgets.data && budgets.data.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {budgets.data.map((budget) => (
            <BudgetCard key={budget.id} budget={budget} onSelect={() => setSelected(budget)}
              onEdit={() => { save.reset(); setEditing(budget) }} onDelete={() => setConfirmDelete(budget)} />
          ))}
        </div>
      )}

      <Dialog open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'New budget' : 'Edit budget'}>
        {editing !== null && (
          <BudgetForm
            initial={editing === 'new' ? undefined : editing}
            currency={currency}
            busy={save.isPending}
            serverError={save.isError ? errorMessage(save.error) : undefined}
            onSubmit={(input) => save.mutate(input)}
            onCancel={() => setEditing(null)}
          />
        )}
      </Dialog>

      <Dialog open={selected !== null} onClose={() => setSelected(null)} title={selected?.budgetName ?? 'Budget'} variant="drawer">
        {selected && <BudgetDetail budgetId={selected.id} />}
      </Dialog>

      <Dialog open={confirmDelete !== null} onClose={() => setConfirmDelete(null)} title="Delete budget?"
        description="Your transactions are not affected.">
        <div className="flex gap-2">
          <Button variant="destructive" disabled={remove.isPending} onClick={() => confirmDelete && remove.mutate(confirmDelete.id)}>
            Delete {confirmDelete?.budgetName}
          </Button>
          <Button variant="ghost" onClick={() => setConfirmDelete(null)}>Cancel</Button>
        </div>
      </Dialog>

    </>
  )
}
