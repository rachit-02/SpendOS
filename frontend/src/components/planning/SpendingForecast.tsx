import { Badge } from '@/components/ui/feedback'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatCurrencyRounded } from '@/utils/formatters'
import type { SpendingPrediction } from '@/types/planning'

function confidenceTone(confidence: number) {
  return confidence >= 0.75 ? 'success' : confidence >= 0.5 ? 'warning' : 'danger'
}

/** Month-end forecast: where spending is now, the likely landing range and how sure we are. */
export function SpendingForecast({ prediction }: { prediction: SpendingPrediction }) {
  const { projectedMonthEnd: range, currencyCode: currency } = prediction
  const money = (value: number) => formatCurrencyRounded(value, currency)
  const span = Math.max(range.max, prediction.monthlyAverage, 1)
  const position = (value: number) => `${Math.min(100, (value / span) * 100)}%`
  const confidence = Math.round(prediction.confidence * 100)

  return (
    <Card role="region" aria-label="Spending forecast">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle>Month-end forecast</CardTitle>
          <Badge tone={confidenceTone(prediction.confidence)}>{confidence}% confidence</Badge>
        </div>
        <CardDescription>{prediction.confidenceReason}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-lg border p-3">
            <p className="text-xs text-muted-foreground">Spent so far</p>
            <p className="text-xl font-semibold">{money(prediction.currentSpending)}</p>
            <p className="text-xs text-muted-foreground">
              {prediction.daysElapsed} days in, {prediction.remainingDaysInMonth} to go
            </p>
          </div>
          <div className="rounded-lg border p-3">
            <p className="text-xs text-muted-foreground">Likely month-end total</p>
            <p className="text-xl font-semibold">{money(range.median)}</p>
            <p className="text-xs text-muted-foreground">between {money(range.min)} and {money(range.max)}</p>
          </div>
          <div className="rounded-lg border p-3">
            <p className="text-xs text-muted-foreground">Your usual month</p>
            <p className="text-xl font-semibold">{money(prediction.monthlyAverage)}</p>
            <p className="text-xs text-muted-foreground">last month {money(prediction.previousMonthTotal)}</p>
          </div>
        </div>

        <div aria-hidden="true" className="relative h-3 rounded-full bg-muted">
          <div className="absolute inset-y-0 rounded-full bg-primary/25"
            style={{ left: position(range.min), right: `calc(100% - ${position(range.max)})` }} />
          <div className="absolute inset-y-0 left-0 rounded-full bg-primary" style={{ width: position(prediction.currentSpending) }} />
          <div className="absolute -top-1 h-5 w-0.5 bg-foreground" style={{ left: position(prediction.monthlyAverage) }} />
        </div>
        <p className="text-xs text-muted-foreground">
          Solid bar: spent so far. Shaded: likely month-end range. Line: your usual month.
        </p>

        {prediction.upcomingRecurring > 0 && (
          <p className="text-sm">About {money(prediction.upcomingRecurring)} of recurring payments are still due this month.</p>
        )}
        {!prediction.sufficientData && (
          <p className="text-sm text-warning">There is not much history yet, so treat this forecast as rough.</p>
        )}
        <p className="text-xs text-muted-foreground">{prediction.methodology}</p>
      </CardContent>
    </Card>
  )
}
