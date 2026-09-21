import { lazy, Suspense, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/components/common/PageHeader'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { MonthSelector } from '@/components/dashboard/MonthSelector'
import { OverviewCards } from '@/components/dashboard/OverviewCards'
import { HealthScoreCard } from '@/components/dashboard/HealthScoreCard'
import { CategoryBreakdown } from '@/components/dashboard/CategoryBreakdown'
import { TopMerchants } from '@/components/dashboard/TopMerchants'
import { RecentTransactions } from '@/components/dashboard/RecentTransactions'
import { BudgetsPreview, RecurringPreview } from '@/components/dashboard/DashboardSections'
import { dashboardService } from '@/services/dashboardService'
import { errorMessage } from '@/services/api'
import { useAuthStore } from '@/store/authStore'
import { currentMonth, monthRange, type MonthValue } from '@/utils/months'

// Recharts is heavy; load it only when the dashboard renders charts.
const CategoryDonut = lazy(() => import('@/components/dashboard/DashboardCharts').then((m) => ({ default: m.CategoryDonut })))
const TrendChart = lazy(() => import('@/components/dashboard/DashboardCharts').then((m) => ({ default: m.TrendChart })))

export default function DashboardPage() {
  const user = useAuthStore((state) => state.user)
  const navigate = useNavigate()
  const [month, setMonth] = useState<MonthValue>(currentMonth)

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['dashboard', month.year, month.month],
    queryFn: () => dashboardService.get(month.month, month.year),
    placeholderData: keepPreviousData,
  })

  const range = monthRange(month)
  const openTransactions = (params: Record<string, string | undefined>) => {
    const search = new URLSearchParams({ ...range })
    Object.entries(params).forEach(([key, value]) => value && search.set(key, value))
    navigate(`/transactions?${search.toString()}`)
  }

  const firstName = user?.fullName?.split(' ')[0]
  const header = (
    <PageHeader
      title={firstName ? `Welcome, ${firstName}` : 'Dashboard'}
      description="How much did you spend, where did it go, and is anything unusual?"
      actions={<MonthSelector value={month} onChange={setMonth} />}
    />
  )

  if (isLoading) return <>{header}<Spinner label="Loading your dashboard" /></>
  if (isError || !data) return <>{header}<ErrorState message={errorMessage(error)} action={<Button onClick={() => refetch()}>Retry</Button>} /></>

  const currency = data.summary.currencyCode
  const hasActivity = data.summary.transactionCount > 0

  return (
    <>
      {header}
      <div className="space-y-6">
        <OverviewCards summary={data.summary} />

        {!hasActivity ? (
          <EmptyState
            title={`No transactions in ${data.period.month} ${data.period.year}`}
            description="Import a statement or add transactions to see where your money goes."
            action={<Link to="/import"><Button>Import a statement</Button></Link>}
          />
        ) : (
          <div className="grid gap-6 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>Where your money went</CardTitle>
                <CardDescription>Select a category to see its transactions.</CardDescription>
              </CardHeader>
              <CardContent className="grid gap-4 md:grid-cols-2 md:items-center">
                <Suspense fallback={<Spinner label="Loading chart" />}>
                  <CategoryDonut categories={data.spending.byCategory} currency={currency}
                    onSelect={(categoryId) => openTransactions({ categoryId, transactionType: 'debit' })} />
                </Suspense>
                <CategoryBreakdown categories={data.spending.byCategory} currency={currency}
                  onSelect={(categoryId) => openTransactions({ categoryId, transactionType: 'debit' })} />
              </CardContent>
            </Card>

            <HealthScoreCard health={data.financialHealth} />

            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>Monthly trend</CardTitle>
                <CardDescription>Income and spending over the last six months.</CardDescription>
              </CardHeader>
              <CardContent>
                <Suspense fallback={<Spinner label="Loading chart" />}>
                  <TrendChart trend={data.trend} currency={currency} />
                </Suspense>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Top merchants</CardTitle>
              </CardHeader>
              <CardContent>
                <TopMerchants merchants={data.spending.topMerchants} currency={currency}
                  onSelect={(merchantId) => openTransactions({ merchantId })} />
              </CardContent>
            </Card>

            {data.budgets.length > 0 && <BudgetsPreview budgets={data.budgets} currency={currency} />}
            {data.recurringPayments.length > 0 && <RecurringPreview payments={data.recurringPayments} currency={currency} />}

            <Card className="lg:col-span-3">
              <CardHeader className="flex-row items-center justify-between">
                <CardTitle>Recent transactions</CardTitle>
                <Link to="/transactions" className="text-sm font-medium text-primary hover:underline">View all</Link>
              </CardHeader>
              <CardContent>
                <RecentTransactions transactions={data.recentTransactions} currency={currency} />
              </CardContent>
            </Card>
          </div>
        )}
      </div>
    </>
  )
}
