import type { ReactNode } from 'react'
import { ArrowDownRight, ArrowUpRight, Eye, Lightbulb, Target } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { ProgressBar } from '@/components/ui/progress'
import { formatCurrency, formatCurrencyRounded, formatDate, formatPercent } from '@/utils/formatters'
import type { CategoryDelta, MonthlyAutopsy as Autopsy } from '@/types/reports'

function Section({ title, children, defaultOpen = true }: { title: string; children: ReactNode; defaultOpen?: boolean }) {
  return (
    <details open={defaultOpen} className="group rounded-lg border bg-card print:break-inside-avoid">
      <summary className="cursor-pointer list-none px-5 py-3 font-semibold marker:hidden">
        <span className="mr-2 inline-block transition-transform group-open:rotate-90" aria-hidden="true">›</span>
        {title}
      </summary>
      <div className="px-5 pb-5">{children}</div>
    </details>
  )
}

function DeltaRow({ delta, currency, up }: { delta: CategoryDelta; currency: string; up: boolean }) {
  const Icon = up ? ArrowUpRight : ArrowDownRight
  return (
    <li className="flex items-center justify-between text-sm">
      <span className="flex items-center gap-2">
        <Icon className={up ? 'h-4 w-4 text-destructive' : 'h-4 w-4 text-success'} aria-hidden="true" />
        {delta.category}
      </span>
      <span className="tabular-nums">
        {up ? '+' : ''}{formatCurrency(delta.amount, currency)}{' '}
        <span className="text-muted-foreground">
          {delta.percentageChange === undefined || delta.percentageChange === null ? '(new)' : `(${delta.percentageChange > 0 ? '+' : ''}${delta.percentageChange}%)`}
        </span>
      </span>
    </li>
  )
}

/** The end-of-month report: every number here comes straight from the user's transactions. */
export function MonthlyAutopsy({ report }: { report: Autopsy }) {
  const currency = report.currencyCode
  const budgets = Object.entries(report.budgetPerformance)
  return (
    <article className="space-y-4" aria-label={`Money autopsy for ${report.period}`}>
      {!report.isComplete && (
        <p role="status" className="rounded-md border border-warning/40 bg-warning/5 p-3 text-sm">
          {report.period} is still in progress; this report covers up to {formatDate(report.endDate)}.
        </p>
      )}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {[
          ['Income', formatCurrencyRounded(report.income, currency)],
          ['Expenses', formatCurrencyRounded(report.expenses, currency)],
          ['Savings', formatCurrencyRounded(report.savings, currency)],
          ['Savings rate', formatPercent(report.savingsRate * 100)],
        ].map(([label, value]) => (
          <Card key={label}>
            <CardContent className="p-4">
              <p className="text-sm text-muted-foreground">{label}</p>
              <p className="text-xl font-semibold tabular-nums">{value}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card className="border-primary/30 bg-accent/40">
        <CardContent className="space-y-2 p-5">
          <p className="flex items-center gap-2 text-sm font-semibold text-accent-foreground">
            <Lightbulb className="h-4 w-4" aria-hidden="true" /> Most important insight
          </p>
          <p>{report.mostImportantInsight}</p>
          <p className="flex items-center gap-2 text-sm text-muted-foreground">
            <Target className="h-4 w-4" aria-hidden="true" /> {report.suggestedAction}
          </p>
        </CardContent>
      </Card>

      <Section title="What changed vs last month">
        {report.changes.largestIncreases.length === 0 && report.changes.largestDecreases.length === 0 ? (
          <p className="text-sm text-muted-foreground">Spending was the same as last month.</p>
        ) : (
          <ul className="space-y-2" aria-label="Changes versus last month">
            {report.changes.largestIncreases.map((d) => <DeltaRow key={`up-${d.category}`} delta={d} currency={currency} up />)}
            {report.changes.largestDecreases.map((d) => <DeltaRow key={`down-${d.category}`} delta={d} currency={currency} up={false} />)}
          </ul>
        )}
      </Section>

      <Section title="Where the money went">
        <ul className="space-y-2" aria-label="Spending by category">
          {report.spendingByCategory.map((c) => (
            <li key={c.categoryName} className="space-y-1">
              <div className="flex justify-between text-sm">
                <span>{c.categoryName}</span>
                <span className="tabular-nums">{formatCurrency(c.amount, currency)} · {c.percentage}%</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                <div className="h-full rounded-full" style={{ width: `${c.percentage}%`, backgroundColor: c.colorHex ?? 'hsl(var(--primary))' }} />
              </div>
            </li>
          ))}
        </ul>
      </Section>

      <div className="grid gap-4 lg:grid-cols-2">
        <Section title="Largest merchants">
          <ol className="space-y-1.5 text-sm" aria-label="Largest merchants">
            {report.largestMerchants.map((m, i) => (
              <li key={m.merchantName} className="flex justify-between">
                <span>{i + 1}. {m.merchantName}</span>
                <span className="tabular-nums">{formatCurrency(m.amount, currency)}</span>
              </li>
            ))}
          </ol>
        </Section>
        <Section title="Recurring payments">
          {report.recurringPayments.length === 0 ? <p className="text-sm text-muted-foreground">None detected.</p> : (
            <ul className="space-y-1.5 text-sm" aria-label="Recurring payments">
              {report.recurringPayments.map((r) => (
                <li key={r.merchantName} className="flex justify-between">
                  <span>{r.merchantName}</span>
                  <span className="tabular-nums">{formatCurrency(r.amount, currency)} / {r.frequency}</span>
                </li>
              ))}
            </ul>
          )}
        </Section>
      </div>

      {report.unusualTransactions.length > 0 && (
        <Section title="Unusual transactions">
          <ul className="space-y-1.5 text-sm" aria-label="Unusual transactions">
            {report.unusualTransactions.map((u) => <li key={`${u.date}-${u.amount}`}>{u.description}</li>)}
          </ul>
        </Section>
      )}

      {budgets.length > 0 && (
        <Section title="Budget performance">
          <ul className="space-y-3" aria-label="Budget performance">
            {budgets.map(([name, line]) => (
              <li key={name} className="space-y-1">
                <div className="flex justify-between text-sm">
                  <span>{name}</span>
                  <span className={line.exceeded ? 'tabular-nums text-destructive' : 'tabular-nums'}>
                    {line.percentage}% of {formatCurrency(line.budget, currency)}
                  </span>
                </div>
                <ProgressBar value={line.percentage} label={`${name} budget used`} />
              </li>
            ))}
          </ul>
        </Section>
      )}

      {report.nextMonthWatchlist.length > 0 && (
        <Section title="Watch next month">
          <ul className="space-y-2 text-sm" aria-label="Watch next month">
            {report.nextMonthWatchlist.map((item) => (
              <li key={item.message} className="flex gap-2">
                <Eye className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" /> {item.message}
              </li>
            ))}
          </ul>
        </Section>
      )}
      <p className="text-xs text-muted-foreground">
        Based on {report.transactionCount} transactions. A planning aid, not professional financial advice.
      </p>
    </article>
  )
}
