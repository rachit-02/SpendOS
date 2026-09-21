import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatCurrency, formatCurrencyRounded } from '@/utils/formatters'
import type { CategoryTrend, MonthlyAnalytics, TrendMonth } from '@/types/analytics'

const axisMoney = (currency: string) => (value: number) => formatCurrencyRounded(value, currency)

export function CategoryBars({ categories, currency }: {
  categories: MonthlyAnalytics['expenses']['byCategory']
  currency: string
}) {
  const height = Math.max(160, categories.length * 36)
  return (
    <div style={{ height }} role="img" aria-label="Spending by category bar chart; the table below lists the same values">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={categories} layout="vertical" margin={{ left: 8, right: 16 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" horizontal={false} />
          <XAxis type="number" tickFormatter={axisMoney(currency)} fontSize={12} tickLine={false} axisLine={false} />
          <YAxis type="category" dataKey="categoryName" width={110} fontSize={12} tickLine={false} axisLine={false} />
          <Tooltip formatter={(value: number) => formatCurrency(value, currency)} />
          <Bar dataKey="amount" name="Spent" radius={[0, 4, 4, 0]}>
            {categories.map((c) => (
              <Cell key={c.categoryName} fill={c.colorHex ?? 'hsl(var(--primary))'} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

export function CategoryTrendChart({ data, currency }: { data: CategoryTrend; currency: string }) {
  const points = data.trend.map((p) => ({ ...p, label: `${p.month} ${String(p.year).slice(2)}` }))
  return (
    <div className="h-72" role="img" aria-label={`${data.categoryName} spending by month with the average marked`}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={points} margin={{ left: 8, right: 8, top: 16 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
          <XAxis dataKey="label" fontSize={12} tickLine={false} axisLine={false} />
          <YAxis tickFormatter={axisMoney(currency)} fontSize={12} width={70} tickLine={false} axisLine={false} />
          <Tooltip formatter={(value: number) => formatCurrency(value, currency)} />
          <ReferenceLine y={data.average} stroke="hsl(var(--warning))" strokeDasharray="4 4"
            label={{ value: 'Average', position: 'insideTopRight', fontSize: 11 }} />
          <Bar dataKey="amount" name="Spent" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

export function YearTrendChart({ months, currency }: { months: TrendMonth[]; currency: string }) {
  const points = months.map((m) => ({ ...m, label: `${m.month} ${String(m.year).slice(2)}` }))
  return (
    <div className="h-80" role="img" aria-label="Monthly income and spending, with last year's spending for comparison">
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={points} margin={{ left: 8, right: 8, top: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
          <XAxis dataKey="label" fontSize={12} tickLine={false} axisLine={false} />
          <YAxis tickFormatter={axisMoney(currency)} fontSize={12} width={70} tickLine={false} axisLine={false} />
          <Tooltip formatter={(value: number) => formatCurrency(value, currency)} />
          <Legend />
          <Bar dataKey="income" name="Income" fill="hsl(var(--success))" radius={[4, 4, 0, 0]} />
          <Bar dataKey="expense" name="Spending" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
          <Line dataKey="lastYearExpense" name="Spending last year" stroke="hsl(var(--warning))" strokeDasharray="4 4" dot={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  )
}
