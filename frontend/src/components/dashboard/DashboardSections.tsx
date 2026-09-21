import { Link } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/feedback'
import { ProgressBar } from '@/components/ui/progress'
import { formatCurrency, formatDate } from '@/utils/formatters'
import type { Dashboard } from '@/types/dashboard'

export function BudgetsPreview({ budgets, currency }: { budgets: Dashboard['budgets']; currency: string }) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Budgets</CardTitle>
        <Link to="/budgets" className="text-sm font-medium text-primary hover:underline">Manage</Link>
      </CardHeader>
      <CardContent>
        <ul className="space-y-4" aria-label="Budget progress">
          {budgets.map((budget) => (
            <li key={budget.id} className="space-y-1.5">
              <div className="flex justify-between text-sm">
                <span className="font-medium">{budget.budgetName}</span>
                <span className="tabular-nums text-muted-foreground">
                  {formatCurrency(budget.spentAmount, currency)} / {formatCurrency(budget.totalAmount, currency)}
                </span>
              </div>
              <ProgressBar value={budget.percentage} alertAt={budget.alertThreshold} label={`${budget.budgetName} spent`} />
              {budget.isExceeded && <p className="text-xs text-destructive">Over budget by {formatCurrency(-budget.remainingAmount, currency)}</p>}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}

export function RecurringPreview({ payments, currency }: { payments: Dashboard['recurringPayments']; currency: string }) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Upcoming recurring</CardTitle>
        <Link to="/recurring" className="text-sm font-medium text-primary hover:underline">View all</Link>
      </CardHeader>
      <CardContent>
        <ul className="divide-y" aria-label="Upcoming recurring payments">
          {payments.map((p) => (
            <li key={p.id} className="flex items-center justify-between py-2.5 text-sm">
              <div>
                <p className="font-medium">
                  {p.merchantName} {!p.isUserConfirmed && <Badge tone="warning" className="ml-1">Review</Badge>}
                </p>
                <p className="text-xs capitalize text-muted-foreground">
                  {p.frequency}{p.nextExpectedDate ? ` · next ${formatDate(p.nextExpectedDate, 'd MMM')}` : ''}
                </p>
              </div>
              <span className="tabular-nums">{formatCurrency(p.typicalAmount, currency)}</span>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
