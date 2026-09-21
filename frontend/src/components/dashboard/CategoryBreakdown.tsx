import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react'
import { formatCurrency } from '@/utils/formatters'
import type { Dashboard } from '@/types/dashboard'

const trendIcon = {
  up: <ArrowUpRight className="h-3.5 w-3.5 text-destructive" aria-label="Up from last month" />,
  down: <ArrowDownRight className="h-3.5 w-3.5 text-success" aria-label="Down from last month" />,
  stable: <Minus className="h-3.5 w-3.5 text-muted-foreground" aria-label="About the same as last month" />,
}

/** Accessible list version of the category chart; each row opens the category's transactions. */
export function CategoryBreakdown({ categories, currency, onSelect }: {
  categories: Dashboard['spending']['byCategory']
  currency: string
  onSelect: (categoryId?: string) => void
}) {
  return (
    <ul className="space-y-1" aria-label="Spending by category">
      {categories.map((category) => (
        <li key={category.categoryName}>
          <button
            type="button"
            onClick={() => onSelect(category.categoryId)}
            className="flex w-full items-center gap-3 rounded-md px-2 py-1.5 text-left text-sm hover:bg-muted"
          >
            <span aria-hidden="true" className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: category.colorHex ?? '#A9A9A9' }} />
            <span className="flex-1 truncate">{category.categoryName}</span>
            {trendIcon[category.trend]}
            <span className="w-12 text-right tabular-nums text-muted-foreground">{category.percentage.toFixed(1)}%</span>
            <span className="w-24 text-right font-medium tabular-nums">{formatCurrency(category.amount, currency)}</span>
          </button>
        </li>
      ))}
    </ul>
  )
}
