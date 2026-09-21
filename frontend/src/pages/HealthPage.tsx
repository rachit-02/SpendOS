import { lazy, Suspense, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Lightbulb } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { MonthSelector } from '@/components/dashboard/MonthSelector'
import { HealthScoreCard } from '@/components/dashboard/HealthScoreCard'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { healthMetricsService } from '@/services/healthMetricsService'
import { errorMessage } from '@/services/api'
import { currentMonth, monthLabel, type MonthValue } from '@/utils/months'
import { formatCurrencyRounded, formatPercent } from '@/utils/formatters'
import { useCurrency } from '@/hooks/useReferenceData'
import type { HealthMetrics } from '@/types/health'

const HealthHistoryChart = lazy(() => import('@/components/health/HealthHistoryChart'))

function KeyNumbers({ metrics, currency }: { metrics: NonNullable<HealthMetrics['metrics']>; currency: string }) {
  const items = [
    { label: 'Average monthly income', value: formatCurrencyRounded(metrics.averageMonthlyIncome, currency) },
    { label: 'Average monthly spending', value: formatCurrencyRounded(metrics.averageMonthlyExpense, currency) },
    { label: 'Savings rate', value: formatPercent(metrics.savingsRate) },
    { label: 'Recurring share of income', value: formatPercent(metrics.recurringBurden) },
    { label: 'Spending variation', value: formatPercent(metrics.spendingVolatility) },
    { label: 'Emergency buffer', value: metrics.emergencyBufferMonths != null ? `${metrics.emergencyBufferMonths} months` : '—' },
  ]
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {items.map((item) => (
        <div key={item.label} className="rounded-lg border p-3">
          <dt className="text-xs text-muted-foreground">{item.label}</dt>
          <dd className="text-lg font-semibold tabular-nums">{item.value}</dd>
        </div>
      ))}
    </dl>
  )
}

export default function HealthPage() {
  const currency = useCurrency()
  const [month, setMonth] = useState<MonthValue>(currentMonth)
  const current = useQuery({ queryKey: ['health-metrics'], queryFn: healthMetricsService.current })
  const history = useQuery({ queryKey: ['health-history'], queryFn: () => healthMetricsService.history(12) })
  const explanation = useQuery({
    queryKey: ['health-explanation', month.year, month.month],
    queryFn: () => healthMetricsService.explanation(month),
    placeholderData: keepPreviousData,
  })

  if (current.isLoading) return <Spinner label="Calculating your health score" />
  if (current.isError) return <ErrorState message={errorMessage(current.error)} />
  const data = current.data!
  if (!data.enabled) {
    return (
      <>
        <PageHeader title="Financial health" />
        <EmptyState title="Health score is turned off"
          description="You can turn it back on in Settings under Preferences."
          action={<Link to="/settings" className="text-sm text-primary underline">Open settings</Link>} />
      </>
    )
  }

  return (
    <>
      <PageHeader title="Financial health"
        description="A 0-100 score from five factors you can see and influence. Nothing here is guesswork: every factor is calculated from your transactions." />
      <div className="grid gap-6 lg:grid-cols-2">
        <HealthScoreCard health={{ score: data.score, summary: data.summary, breakdown: data.factors, changes: [], factors: {} }} />

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Lightbulb className="h-4 w-4 text-primary" aria-hidden="true" /> How to improve
              </CardTitle>
              <CardDescription>Ordered by how many points each could add.</CardDescription>
            </CardHeader>
            <CardContent>
              {data.recommendations.length === 0
                ? <p className="text-sm text-muted-foreground">Every factor is in good shape. Keep it up.</p>
                : (
                  <ol className="space-y-3" aria-label="Recommendations">
                    {data.recommendations.map((recommendation) => (
                      <li key={recommendation.factor} className="rounded-lg border p-3">
                        <div className="flex items-baseline justify-between gap-2">
                          <p className="font-medium">{recommendation.title}</p>
                          <span className="whitespace-nowrap text-xs text-success">up to +{Math.round(recommendation.potentialPoints)} pts</span>
                        </div>
                        <p className="mt-1 text-sm text-muted-foreground">{recommendation.action}</p>
                      </li>
                    ))}
                  </ol>
                )}
            </CardContent>
          </Card>
          {data.metrics && <KeyNumbers metrics={data.metrics} currency={currency} />}
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Score over time</CardTitle>
            <CardDescription>Each month scored on the three months ending with it.</CardDescription>
          </CardHeader>
          <CardContent>
            {history.isLoading && <Spinner />}
            {history.data && history.data.length < 2 && (
              <p className="text-sm text-muted-foreground">A trend appears once you have two months with a score.</p>
            )}
            {history.data && history.data.length >= 2 && (
              <Suspense fallback={<Spinner label="Loading chart" />}>
                <HealthHistoryChart history={history.data} />
              </Suspense>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row flex-wrap items-center justify-between gap-2 space-y-0">
            <div>
              <CardTitle>What changed</CardTitle>
              <CardDescription>{monthLabel(month)} compared with the month before.</CardDescription>
            </div>
            <MonthSelector value={month} onChange={setMonth} />
          </CardHeader>
          <CardContent className="space-y-3">
            {explanation.isLoading && <Spinner />}
            {explanation.isError && <ErrorState message={errorMessage(explanation.error)} />}
            {explanation.data && (
              <>
                <p className="text-sm font-medium" role="status">{explanation.data.summary}</p>
                {explanation.data.changes.length > 0 && (
                  <ul className="space-y-2" aria-label="Factor changes">
                    {explanation.data.changes.map((change) => (
                      <li key={change.factor} className="flex gap-3 text-sm">
                        <span className={`w-12 shrink-0 text-right tabular-nums ${change.change > 0 ? 'text-success' : change.change < 0 ? 'text-destructive' : ''}`}>
                          {change.change > 0 ? '+' : ''}{Math.round(change.change * 10) / 10}
                        </span>
                        <span>{change.reason}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </>
  )
}
