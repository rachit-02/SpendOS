import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatCurrency, formatCurrencyRounded } from '@/utils/formatters'
import type { Dashboard } from '@/types/dashboard'

const FALLBACK_COLORS = ['#6366F1', '#F59E0B', '#10B981', '#EF4444', '#3B82F6', '#8B5CF6', '#EC4899', '#14B8A6']

interface CategoryChartProps {
  categories: Dashboard['spending']['byCategory']
  currency: string
  onSelect: (categoryId?: string) => void
}

/** Donut of spending by category; clicking a slice opens the matching transactions. */
export function CategoryDonut({ categories, currency, onSelect }: CategoryChartProps) {
  const data = categories.map((c, i) => ({ ...c, fill: c.colorHex ?? FALLBACK_COLORS[i % FALLBACK_COLORS.length] }))
  return (
    <div className="h-64" role="img" aria-label="Spending by category chart. The list beside it has the same data.">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={data}
            dataKey="amount"
            nameKey="categoryName"
            innerRadius="58%"
            outerRadius="90%"
            paddingAngle={2}
            onClick={(entry: { categoryId?: string }) => onSelect(entry.categoryId)}
            className="cursor-pointer"
          >
            {data.map((entry) => (
              <Cell key={entry.categoryName} fill={entry.fill} stroke="hsl(var(--card))" />
            ))}
          </Pie>
          <Tooltip formatter={(value: number) => formatCurrency(value, currency)} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}

/** Income vs spending over the last six months. */
export function TrendChart({ trend, currency }: { trend: Dashboard['trend']; currency: string }) {
  return (
    <div className="h-64" role="img" aria-label="Income and spending over the last six months">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={trend} margin={{ left: 8, right: 8, top: 8 }}>
          <defs>
            <linearGradient id="income" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="hsl(var(--success))" stopOpacity={0.3} />
              <stop offset="100%" stopColor="hsl(var(--success))" stopOpacity={0} />
            </linearGradient>
            <linearGradient id="expense" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity={0.3} />
              <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
          <XAxis dataKey="month" tickLine={false} axisLine={false} fontSize={12} />
          <YAxis tickLine={false} axisLine={false} fontSize={12} width={60}
            tickFormatter={(value: number) => formatCurrencyRounded(value, currency).replace(/\.00$/, '')} />
          <Tooltip formatter={(value: number) => formatCurrency(value, currency)} />
          <Legend />
          <Area type="monotone" dataKey="income" name="Income" stroke="hsl(var(--success))" fill="url(#income)" strokeWidth={2} />
          <Area type="monotone" dataKey="expense" name="Spending" stroke="hsl(var(--primary))" fill="url(#expense)" strokeWidth={2} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}
