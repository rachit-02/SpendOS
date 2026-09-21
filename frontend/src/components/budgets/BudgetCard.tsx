import { Pencil, Trash2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/feedback'
import { Button } from '@/components/ui/button'
import { ProgressBar } from '@/components/ui/progress'
import { formatCurrency, formatDate } from '@/utils/formatters'
import type { Budget } from '@/types/budgets'

const statusBadge = {
  on_track: <Badge tone="success">On track</Badge>,
  warning: <Badge tone="warning">Near limit</Badge>,
  exceeded: <Badge tone="danger">Over budget</Badge>,
}

export function BudgetCard({ budget, onEdit, onDelete, onSelect }: {
  budget: Budget
  onEdit: () => void
  onDelete: () => void
  onSelect: () => void
}) {
  const currency = budget.currencyCode
  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-2">
        <div>
          <CardTitle>
            <button type="button" className="text-left hover:underline" onClick={onSelect}>{budget.budgetName}</button>
          </CardTitle>
          <p className="text-xs text-muted-foreground">
            {formatDate(budget.startDate, 'd MMM')} – {formatDate(budget.endDate, 'd MMM yyyy')}
          </p>
        </div>
        <div className="flex items-center gap-1">
          {statusBadge[budget.status]}
          <Button variant="ghost" size="icon" aria-label={`Edit ${budget.budgetName}`} onClick={onEdit}>
            <Pencil className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" aria-label={`Delete ${budget.budgetName}`} onClick={onDelete}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-1.5">
          <div className="flex justify-between text-sm">
            <span className="tabular-nums">
              {formatCurrency(budget.spentAmount, currency)} <span className="text-muted-foreground">of {formatCurrency(budget.totalAmount, currency)}</span>
            </span>
            <span className="tabular-nums font-medium">{budget.percentage}%</span>
          </div>
          <ProgressBar value={budget.percentage} alertAt={budget.alertThreshold} label={`${budget.budgetName} spent`} />
          <p className="text-xs text-muted-foreground">
            {budget.remainingAmount >= 0
              ? `${formatCurrency(budget.remainingAmount, currency)} left`
              : `${formatCurrency(-budget.remainingAmount, currency)} over`}
          </p>
        </div>
        {budget.categories.length > 0 && (
          <ul className="space-y-3" aria-label={`${budget.budgetName} categories`}>
            {budget.categories.map((category) => (
              <li key={category.categoryId} className="space-y-1">
                <div className="flex justify-between text-xs">
                  <span>{category.categoryName}</span>
                  <span className="tabular-nums text-muted-foreground">
                    {formatCurrency(category.spentAmount, currency)} / {formatCurrency(category.allocatedAmount, currency)}
                  </span>
                </div>
                <ProgressBar value={category.percentage} alertAt={budget.alertThreshold} label={`${category.categoryName} spent`} className="h-1.5" />
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
