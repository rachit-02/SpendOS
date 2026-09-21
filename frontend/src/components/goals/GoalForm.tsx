import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { FieldError, Input, Label, Select } from '@/components/ui/input'
import { parseAmount } from '@/utils/validators'
import type { Goal, GoalInput, GoalType } from '@/types/planning'

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

/** Goal editor: name, type, target, what is already saved and the target date. */
export function GoalForm({ initial, busy, serverError, onSubmit, onCancel }: {
  initial?: Goal
  busy?: boolean
  serverError?: string
  onSubmit: (input: GoalInput) => void
  onCancel: () => void
}) {
  const [goalName, setGoalName] = useState(initial?.goalName ?? '')
  const [goalType, setGoalType] = useState<GoalType>(initial?.goalType ?? 'savings')
  const [target, setTarget] = useState(initial ? String(initial.targetAmount) : '')
  const [progress, setProgress] = useState(initial ? String(initial.currentProgress) : '')
  const [targetDate, setTargetDate] = useState(initial?.targetDate ?? '')
  const [description, setDescription] = useState(initial?.goalDescription ?? '')
  const [error, setError] = useState<string>()

  function submit(event: FormEvent) {
    event.preventDefault()
    setError(undefined)
    const targetAmount = parseAmount(target)
    const currentProgress = progress.trim() === '' || Number(progress) === 0 ? 0 : parseAmount(progress)
    if (!goalName.trim()) return setError('Give the goal a name')
    if (targetAmount === null) return setError('Enter a positive target amount')
    if (currentProgress === null) return setError('Saved so far must be zero or a positive amount')
    if (!targetDate || targetDate <= today()) return setError('Choose a target date in the future')
    onSubmit({
      goalName: goalName.trim(),
      goalDescription: description.trim() || undefined,
      goalType,
      targetAmount,
      currentProgress,
      targetDate,
    })
  }

  return (
    <form onSubmit={submit} noValidate aria-label="Goal" className="grid gap-4 sm:grid-cols-2">
      <div className="space-y-1.5 sm:col-span-2">
        <Label htmlFor="goal-name">Name</Label>
        <Input id="goal-name" value={goalName} maxLength={255} placeholder="Emergency fund"
          onChange={(e) => setGoalName(e.target.value)} />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="goal-type">Type</Label>
        <Select id="goal-type" value={goalType} onChange={(e) => setGoalType(e.target.value as GoalType)}>
          <option value="savings">Save up</option>
          <option value="debt_payoff">Pay off debt</option>
          <option value="expense_reduction">Cut spending</option>
        </Select>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="goal-date">Target date</Label>
        <Input id="goal-date" type="date" value={targetDate} min={today()} onChange={(e) => setTargetDate(e.target.value)} />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="goal-target">Target amount</Label>
        <Input id="goal-target" inputMode="decimal" value={target} onChange={(e) => setTarget(e.target.value)} />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="goal-progress">Saved so far</Label>
        <Input id="goal-progress" inputMode="decimal" value={progress} placeholder="0"
          onChange={(e) => setProgress(e.target.value)} />
      </div>
      <div className="space-y-1.5 sm:col-span-2">
        <Label htmlFor="goal-description">Notes (optional)</Label>
        <Input id="goal-description" value={description} maxLength={1000} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div className="sm:col-span-2 space-y-3">
        <FieldError message={error ?? serverError} />
        <div className="flex gap-2">
          <Button type="submit" disabled={busy}>{busy ? 'Saving…' : initial ? 'Save goal' : 'Create goal'}</Button>
          <Button variant="ghost" onClick={onCancel}>Cancel</Button>
        </div>
      </div>
    </form>
  )
}
