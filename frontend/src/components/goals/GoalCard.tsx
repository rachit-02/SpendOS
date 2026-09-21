import { Pencil, PlusCircle, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/feedback'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ProgressBar } from '@/components/ui/progress'
import { formatCurrencyRounded, formatDate } from '@/utils/formatters'
import type { Goal } from '@/types/planning'

/** One goal: progress, what it takes per month, and when current savings would get there. */
export function GoalCard({ goal, onContribute, onEdit, onDelete }: {
  goal: Goal
  onContribute: () => void
  onEdit: () => void
  onDelete: () => void
}) {
  const money = (value: number) => formatCurrencyRounded(value, goal.currencyCode)
  const done = goal.remainingAmount <= 0
  return (
    <Card role="article" aria-label={goal.goalName}>
      <CardHeader className="flex-row items-start justify-between gap-2 space-y-0">
        <div>
          <CardTitle className="text-base">{goal.goalName}</CardTitle>
          <p className="text-xs text-muted-foreground">by {formatDate(goal.targetDate)}</p>
        </div>
        {done ? <Badge tone="success">Reached</Badge>
          : goal.onTrack ? <Badge tone="success">On track</Badge>
            : <Badge tone="warning">Behind</Badge>}
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-baseline justify-between text-sm">
          <span className="text-lg font-semibold">{money(goal.currentProgress)}</span>
          <span className="text-muted-foreground">of {money(goal.targetAmount)}</span>
        </div>
        <ProgressBar value={goal.progressPercentage} alertAt={101} label={`${goal.goalName} progress`} />
        {!done && (
          <ul className="space-y-1 text-sm text-muted-foreground">
            <li>Needs {money(goal.monthlyContributionNeeded)} a month to finish on time.</li>
            <li>
              {goal.projectedCompletionDate
                ? `At your current savings rate: ${formatDate(goal.projectedCompletionDate, 'MMM yyyy')} (${goal.monthsToTarget} months).`
                : 'You are not saving each month yet, so there is no projected date.'}
            </li>
          </ul>
        )}
        {goal.goalDescription && <p className="text-sm">{goal.goalDescription}</p>}
        <div className="flex gap-1">
          <Button variant="outline" size="sm" onClick={onContribute}>
            <PlusCircle className="h-4 w-4" aria-hidden="true" /> Add money
          </Button>
          <Button variant="ghost" size="icon" aria-label={`Edit ${goal.goalName}`} onClick={onEdit}>
            <Pencil className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" aria-label={`Delete ${goal.goalName}`} onClick={onDelete}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
