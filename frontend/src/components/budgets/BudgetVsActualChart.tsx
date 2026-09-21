import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatCurrency, formatCurrencyRounded } from '@/utils/formatters'
import type { Budget } from '@/types/budgets'

export default function BudgetVsActualChart({ budget }: { budget: Budget }) {
  const data = budget.categories.length > 0
    ? budget.categories.map((c) => ({ name: c.categoryName, Budget: c.allocatedAmount, Spent: c.spentAmount }))
    : [{ name: budget.budgetName, Budget: budget.totalAmount, Spent: budget.spentAmount }]
  return (
    <div className="h-64" role="img" aria-label={`Budget versus actual spending for ${budget.budgetName}`}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ left: 8, right: 8, top: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
          <XAxis dataKey="name" fontSize={12} tickLine={false} axisLine={false} />
          <YAxis tickFormatter={(v: number) => formatCurrencyRounded(v, budget.currencyCode)} fontSize={12} width={70}
            tickLine={false} axisLine={false} />
          <Tooltip formatter={(value: number) => formatCurrency(value, budget.currencyCode)} />
          <Legend />
          <Bar dataKey="Budget" fill="hsl(var(--muted-foreground))" radius={[4, 4, 0, 0]} />
          <Bar dataKey="Spent" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
