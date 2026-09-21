import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { FieldError, Input, Label } from '@/components/ui/input'
import { GoalCard } from '@/components/goals/GoalCard'
import { GoalForm } from '@/components/goals/GoalForm'
import { goalService } from '@/services/planningService'
import { errorMessage } from '@/services/api'
import { parseAmount } from '@/utils/validators'
import type { Goal, GoalInput } from '@/types/planning'

function ContributionForm({ goal, busy, serverError, onSubmit, onCancel }: {
  goal: Goal
  busy: boolean
  serverError?: string
  onSubmit: (amount: number) => void
  onCancel: () => void
}) {
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string>()
  function submit(event: FormEvent) {
    event.preventDefault()
    const value = parseAmount(amount)
    if (value === null) return setError('Enter a positive amount')
    onSubmit(value)
  }
  return (
    <form onSubmit={submit} noValidate aria-label={`Add money to ${goal.goalName}`} className="space-y-3">
      <div className="space-y-1.5">
        <Label htmlFor="contribution-amount">Amount</Label>
        <Input id="contribution-amount" inputMode="decimal" value={amount} autoFocus onChange={(e) => setAmount(e.target.value)} />
      </div>
      <FieldError message={error ?? serverError} />
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>Add</Button>
        <Button variant="ghost" onClick={onCancel}>Cancel</Button>
      </div>
    </form>
  )
}

export default function GoalsPage() {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<Goal | 'new' | null>(null)
  const [contributing, setContributing] = useState<Goal | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Goal | null>(null)
  const goals = useQuery({ queryKey: ['goals'], queryFn: goalService.list })
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['goals'] })
    queryClient.invalidateQueries({ queryKey: ['simulations'] })
  }

  const save = useMutation({
    mutationFn: (input: GoalInput) =>
      editing && editing !== 'new' ? goalService.update(editing.id, input) : goalService.create(input),
    onSuccess: () => {
      setEditing(null)
      invalidate()
    },
  })
  const contribute = useMutation({
    mutationFn: ({ id, amount }: { id: string; amount: number }) => goalService.contribute(id, amount),
    onSuccess: () => {
      setContributing(null)
      invalidate()
    },
  })
  const remove = useMutation({
    mutationFn: (id: string) => goalService.remove(id),
    onSuccess: () => {
      setConfirmDelete(null)
      invalidate()
    },
  })

  const openNew = () => {
    save.reset()
    setEditing('new')
  }

  return (
    <>
      <PageHeader
        title="Goals"
        description="What you are saving for, and when your real savings rate gets you there."
        actions={<Button onClick={openNew}><Plus className="h-4 w-4" aria-hidden="true" /> New goal</Button>}
      />
      {goals.isLoading && <Spinner />}
      {goals.isError && <ErrorState message={errorMessage(goals.error)} />}
      {goals.data?.length === 0 && (
        <EmptyState title="No goals yet"
          description="An emergency fund of three to six months of spending is a good first goal."
          action={<Button onClick={openNew}>Create your first goal</Button>} />
      )}
      {goals.data && goals.data.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {goals.data.map((goal) => (
            <GoalCard key={goal.id} goal={goal}
              onContribute={() => { contribute.reset(); setContributing(goal) }}
              onEdit={() => { save.reset(); setEditing(goal) }}
              onDelete={() => setConfirmDelete(goal)} />
          ))}
        </div>
      )}

      <Dialog open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'New goal' : 'Edit goal'}>
        {editing !== null && (
          <GoalForm initial={editing === 'new' ? undefined : editing} busy={save.isPending}
            serverError={save.isError ? errorMessage(save.error) : undefined}
            onSubmit={(input) => save.mutate(input)} onCancel={() => setEditing(null)} />
        )}
      </Dialog>

      <Dialog open={contributing !== null} onClose={() => setContributing(null)} title={`Add money to ${contributing?.goalName ?? 'goal'}`}>
        {contributing && (
          <ContributionForm goal={contributing} busy={contribute.isPending}
            serverError={contribute.isError ? errorMessage(contribute.error) : undefined}
            onSubmit={(amount) => contribute.mutate({ id: contributing.id, amount })}
            onCancel={() => setContributing(null)} />
        )}
      </Dialog>

      <Dialog open={confirmDelete !== null} onClose={() => setConfirmDelete(null)} title="Delete goal?"
        description="Your transactions are not affected.">
        <div className="flex gap-2">
          <Button variant="destructive" disabled={remove.isPending} onClick={() => confirmDelete && remove.mutate(confirmDelete.id)}>
            Delete {confirmDelete?.goalName}
          </Button>
          <Button variant="ghost" onClick={() => setConfirmDelete(null)}>Cancel</Button>
        </div>
      </Dialog>
    </>
  )
}
