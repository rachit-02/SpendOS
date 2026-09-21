import { lazy, Suspense, useState } from 'react'
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { Download, FileText } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { Input, Label, Select } from '@/components/ui/input'
import { Table, TBody, Td, Th, THead, Tr } from '@/components/ui/table'
import { MonthSelector } from '@/components/dashboard/MonthSelector'
import { ChangeTable } from './ChangeTable'
import { analyticsService } from '@/services/analyticsService'
import { errorMessage } from '@/services/api'
import { useCategories, useCurrency } from '@/hooks/useReferenceData'
import { formatCurrency, formatCurrencyRounded, formatPercent } from '@/utils/formatters'
import { currentMonth, monthLabel, shiftMonth, type MonthValue } from '@/utils/months'

const CategoryBars = lazy(() => import('./AnalyticsCharts').then((m) => ({ default: m.CategoryBars })))
const CategoryTrendChart = lazy(() => import('./AnalyticsCharts').then((m) => ({ default: m.CategoryTrendChart })))
const YearTrendChart = lazy(() => import('./AnalyticsCharts').then((m) => ({ default: m.YearTrendChart })))

const methodLabels: Record<string, string> = {
  upi: 'UPI', card: 'Card', net_banking: 'Net banking', cash: 'Cash', wallet: 'Wallet', other: 'Not specified',
}

function Kpi({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-lg border bg-card p-4">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="text-xl font-semibold tabular-nums">{value}</p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

export function MonthlyPanel() {
  const [month, setMonth] = useState<MonthValue>(currentMonth)
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['analytics-monthly', month.year, month.month],
    queryFn: () => analyticsService.monthly(month),
    placeholderData: keepPreviousData,
  })
  const exporter = useMutation({ mutationFn: (format: 'csv' | 'pdf') => analyticsService.exportMonthly(month, format) })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <MonthSelector value={month} onChange={setMonth} />
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => exporter.mutate('csv')} disabled={exporter.isPending}>
            <Download className="h-4 w-4" aria-hidden="true" /> CSV
          </Button>
          <Button variant="outline" size="sm" onClick={() => exporter.mutate('pdf')} disabled={exporter.isPending}>
            <FileText className="h-4 w-4" aria-hidden="true" /> PDF
          </Button>
        </div>
      </div>
      {exporter.isError && <p role="alert" className="text-sm text-destructive">{errorMessage(exporter.error)}</p>}
      {isLoading && <Spinner />}
      {isError && <ErrorState message={errorMessage(error)} />}
      {data && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Kpi label="Income" value={formatCurrencyRounded(data.income.total, data.currencyCode)} />
            <Kpi label="Spending" value={formatCurrencyRounded(data.expenses.total, data.currencyCode)}
              hint={data.previousMonthComparison.expenseChange !== undefined && data.previousMonthComparison.expenseChange !== null
                ? `${data.previousMonthComparison.expenseChange > 0 ? '+' : ''}${data.previousMonthComparison.expenseChange}% vs last month`
                : undefined} />
            <Kpi label="Savings" value={formatCurrencyRounded(data.savings, data.currencyCode)} />
            <Kpi label="Savings rate" value={formatPercent(data.savingsRate * 100)} />
          </div>
          {data.expenses.byCategory.length === 0 ? (
            <EmptyState title="No spending this month" />
          ) : (
            <div className="grid gap-4 lg:grid-cols-2">
              <Card>
                <CardHeader><CardTitle>Spending by category</CardTitle></CardHeader>
                <CardContent>
                  <Suspense fallback={<Spinner label="Loading chart" />}>
                    <CategoryBars categories={data.expenses.byCategory} currency={data.currencyCode} />
                  </Suspense>
                </CardContent>
              </Card>
              <div className="grid gap-4">
                <Card>
                  <CardHeader><CardTitle>By payment method</CardTitle></CardHeader>
                  <CardContent>
                    <ul className="space-y-2 text-sm" aria-label="Spending by payment method">
                      {data.expenses.byPaymentMethod.map((m) => (
                        <li key={m.method} className="flex justify-between">
                          <span>{methodLabels[m.method] ?? m.method}</span>
                          <span className="tabular-nums">{formatCurrency(m.amount, data.currencyCode)} · {m.percentage}%</span>
                        </li>
                      ))}
                    </ul>
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader><CardTitle>Income by source</CardTitle></CardHeader>
                  <CardContent>
                    {data.income.bySource.length === 0 ? (
                      <p className="text-sm text-muted-foreground">No income recorded this month.</p>
                    ) : (
                      <ul className="space-y-2 text-sm" aria-label="Income by source">
                        {data.income.bySource.map((s) => (
                          <li key={s.source} className="flex justify-between">
                            <span>{s.source}</span>
                            <span className="tabular-nums">{formatCurrency(s.amount, data.currencyCode)} · {s.percentage}%</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </CardContent>
                </Card>
              </div>
            </div>
          )}
          {data.previousMonthComparison.categoryChanges.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle>What changed since last month</CardTitle>
                <CardDescription>Largest changes first.</CardDescription>
              </CardHeader>
              <CardContent>
                <ChangeTable changes={data.previousMonthComparison.categoryChanges} currency={data.currencyCode}
                  previousLabel="Last month" currentLabel={data.period.month} caption="Category changes versus last month" />
              </CardContent>
            </Card>
          )}
        </>
      )}
    </div>
  )
}

export function CategoryTrendPanel() {
  const { data: categories } = useCategories()
  const currency = useCurrency()
  const [categoryId, setCategoryId] = useState('')
  const [months, setMonths] = useState(6)
  const selected = categoryId || categories?.[0]?.id || ''
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['category-trend', selected, months],
    queryFn: () => analyticsService.categoryTrend(selected, months),
    enabled: Boolean(selected),
  })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3">
        <div className="space-y-1.5">
          <Label htmlFor="trend-category">Category</Label>
          <Select id="trend-category" value={selected} onChange={(e) => setCategoryId(e.target.value)} className="w-48">
            {categories?.map((c) => <option key={c.id} value={c.id}>{c.categoryName}</option>)}
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="trend-months">Period</Label>
          <Select id="trend-months" value={months} onChange={(e) => setMonths(Number(e.target.value))} className="w-40">
            <option value={6}>Last 6 months</option>
            <option value={12}>Last 12 months</option>
            <option value={24}>Last 24 months</option>
          </Select>
        </div>
      </div>
      {isLoading && <Spinner />}
      {isError && <ErrorState message={errorMessage(error)} />}
      {data && (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <Kpi label="Monthly average" value={formatCurrencyRounded(data.average, currency)} />
            <Kpi label="This month vs average"
              value={data.percentageChange === undefined || data.percentageChange === null ? '—' : `${data.percentageChange > 0 ? '+' : ''}${data.percentageChange}%`} />
            <Kpi label="Next month forecast" value={formatCurrencyRounded(data.forecast, currency)} hint={data.forecastMethod} />
          </div>
          <Card>
            <CardContent className="pt-5">
              <Suspense fallback={<Spinner label="Loading chart" />}>
                <CategoryTrendChart data={data} currency={currency} />
              </Suspense>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}

export function MerchantPanel() {
  const currency = useCurrency()
  const today = new Date().toISOString().slice(0, 10)
  const ninetyDaysAgo = new Date(Date.now() - 89 * 86_400_000).toISOString().slice(0, 10)
  const [range, setRange] = useState({ startDate: ninetyDaysAgo, endDate: today })
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['analytics-merchants', range],
    queryFn: () => analyticsService.topMerchants({ ...range, limit: 20 }),
  })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3">
        <div className="space-y-1.5">
          <Label htmlFor="merchant-from">From</Label>
          <Input id="merchant-from" type="date" value={range.startDate} max={range.endDate}
            onChange={(e) => setRange((r) => ({ ...r, startDate: e.target.value }))} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="merchant-to">To</Label>
          <Input id="merchant-to" type="date" value={range.endDate} max={today}
            onChange={(e) => setRange((r) => ({ ...r, endDate: e.target.value }))} />
        </div>
      </div>
      {isLoading && <Spinner />}
      {isError && <ErrorState message={errorMessage(error)} />}
      {data && data.length === 0 && <EmptyState title="No spending in this period" />}
      {data && data.length > 0 && (
        <div className="rounded-md border">
          <Table aria-label="Top merchants">
            <THead>
              <tr>
                <Th>Merchant</Th>
                <Th className="text-right">Total</Th>
                <Th className="text-right">Payments</Th>
                <Th className="text-right">Average</Th>
                <Th className="text-right">Share</Th>
              </tr>
            </THead>
            <TBody>
              {data.map((m) => (
                <Tr key={m.merchantName}>
                  <Td className="font-medium">{m.merchantName}</Td>
                  <Td className="text-right tabular-nums">{formatCurrency(m.amount, currency)}</Td>
                  <Td className="text-right tabular-nums">{m.count}</Td>
                  <Td className="text-right tabular-nums">{formatCurrency(m.averageAmount, currency)}</Td>
                  <Td className="text-right tabular-nums">{m.percentage}%</Td>
                </Tr>
              ))}
            </TBody>
          </Table>
        </div>
      )}
    </div>
  )
}

export function ComparePanel() {
  const currency = useCurrency()
  const [second, setSecond] = useState<MonthValue>(currentMonth)
  const [first, setFirst] = useState<MonthValue>(() => shiftMonth(currentMonth(), -1))
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['analytics-compare', first, second],
    queryFn: () => analyticsService.compare(first, second),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <MonthSelector value={first} onChange={setFirst} />
        <span className="text-sm text-muted-foreground">compared with</span>
        <MonthSelector value={second} onChange={setSecond} />
      </div>
      {isLoading && <Spinner />}
      {isError && <ErrorState message={errorMessage(error)} />}
      {data && (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <Kpi label={`Spending ${monthLabel(first)}`} value={formatCurrencyRounded(data.first.expense, currency)} />
            <Kpi label={`Spending ${monthLabel(second)}`} value={formatCurrencyRounded(data.second.expense, currency)} />
            <Kpi label="Difference" value={`${data.expenseChange > 0 ? '+' : ''}${formatCurrencyRounded(data.expenseChange, currency)}`}
              hint={data.expenseChangePercentage !== undefined && data.expenseChangePercentage !== null ? `${data.expenseChangePercentage}%` : undefined} />
          </div>
          {data.categories.length > 0 && (
            <ChangeTable changes={data.categories} currency={currency} previousLabel={monthLabel(first)}
              currentLabel={monthLabel(second)} caption="Category comparison between the two months" />
          )}
        </>
      )}
    </div>
  )
}

export function YearPanel() {
  const currency = useCurrency()
  const [months, setMonths] = useState(12)
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['analytics-trends', months],
    queryFn: () => analyticsService.trends(months),
  })

  return (
    <div className="space-y-4">
      <Select aria-label="Period" value={months} onChange={(e) => setMonths(Number(e.target.value))} className="w-44">
        <option value={6}>Last 6 months</option>
        <option value={12}>Last 12 months</option>
        <option value={24}>Last 24 months</option>
      </Select>
      {isLoading && <Spinner />}
      {isError && <ErrorState message={errorMessage(error)} />}
      {data && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Kpi label="Average monthly income" value={formatCurrencyRounded(data.averageMonthlyIncome, currency)} />
            <Kpi label="Average monthly spending" value={formatCurrencyRounded(data.averageMonthlyExpense, currency)} />
            <Kpi label="Highest spending" value={data.highestSpendingMonth ? formatCurrencyRounded(data.highestSpendingMonth.expense, currency) : '—'}
              hint={data.highestSpendingMonth ? `${data.highestSpendingMonth.month} ${data.highestSpendingMonth.year}` : undefined} />
            <Kpi label="Lowest spending" value={data.lowestSpendingMonth ? formatCurrencyRounded(data.lowestSpendingMonth.expense, currency) : '—'}
              hint={data.lowestSpendingMonth ? `${data.lowestSpendingMonth.month} ${data.lowestSpendingMonth.year}` : undefined} />
          </div>
          <Card>
            <CardContent className="pt-5">
              <Suspense fallback={<Spinner label="Loading chart" />}>
                <YearTrendChart months={data.months} currency={currency} />
              </Suspense>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
