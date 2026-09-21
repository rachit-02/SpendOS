import { ArrowDownRight, ArrowUpRight, PiggyBank, TrendingDown, TrendingUp, Wallet } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { formatCurrencyRounded, formatPercent } from '@/utils/formatters'
import { cn } from '@/utils/cn'
import type { Dashboard } from '@/types/dashboard'

function StatCard({ label, value, icon, hint, tone }: {
  label: string
  value: string
  icon: React.ReactNode
  hint?: React.ReactNode
  tone?: string
}) {
  return (
    <Card>
      <CardContent className="flex items-start justify-between gap-3 p-5">
        <div className="space-y-1">
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className={cn('text-2xl font-semibold tabular-nums tracking-tight', tone)}>{value}</p>
          {hint && <div className="text-xs text-muted-foreground">{hint}</div>}
        </div>
        <span className="rounded-lg bg-muted p-2 text-muted-foreground" aria-hidden="true">{icon}</span>
      </CardContent>
    </Card>
  )
}

export function OverviewCards({ summary }: { summary: Dashboard['summary'] }) {
  const currency = summary.currencyCode
  const change = summary.expenseChangePercentage
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <StatCard label="Income" value={formatCurrencyRounded(summary.totalIncome, currency)} icon={<TrendingUp className="h-5 w-5" />} />
      <StatCard
        label="Spending"
        value={formatCurrencyRounded(summary.totalExpense, currency)}
        icon={<Wallet className="h-5 w-5" />}
        hint={
          change === undefined || change === null ? (
            'No spending last month to compare'
          ) : (
            <span className={cn('inline-flex items-center gap-0.5', change > 0 ? 'text-destructive' : 'text-success')}>
              {change > 0 ? <ArrowUpRight className="h-3 w-3" /> : <ArrowDownRight className="h-3 w-3" />}
              {formatPercent(Math.abs(change))} {change > 0 ? 'more' : 'less'} than last month
            </span>
          )
        }
      />
      <StatCard
        label="Net savings"
        value={formatCurrencyRounded(summary.netSavings, currency)}
        tone={summary.netSavings < 0 ? 'text-destructive' : undefined}
        icon={summary.netSavings < 0 ? <TrendingDown className="h-5 w-5" /> : <PiggyBank className="h-5 w-5" />}
        hint={summary.totalIncome > 0 ? `Savings rate ${formatPercent(summary.savingsRate * 100)}` : 'No income recorded this month'}
      />
    </div>
  )
}
