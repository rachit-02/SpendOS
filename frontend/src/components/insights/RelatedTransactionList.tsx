import { Link } from 'react-router-dom'
import { formatCurrency, formatDate } from '@/utils/formatters'
import type { RelatedTransaction } from '@/types/insights'

export function RelatedTransactionList({ transactions, currency, label }: {
  transactions: RelatedTransaction[]
  currency: string
  label: string
}) {
  if (transactions.length === 0) {
    return <p className="text-sm text-muted-foreground">This insight is based on your monthly totals.</p>
  }
  const total = transactions.reduce((sum, t) => sum + t.amount, 0)
  return (
    <div className="space-y-2">
      <ul className="divide-y rounded-md border" aria-label={label}>
        {transactions.map((t) => (
          <li key={t.id}>
            <Link to={`/transactions/${t.id}`} className="flex items-center justify-between px-3 py-2 text-sm hover:bg-muted/50">
              <span>
                <span className="font-medium">{t.merchant ?? 'Unknown'}</span>
                <span className="ml-2 text-xs text-muted-foreground">{formatDate(t.date, 'd MMM')}</span>
              </span>
              <span className="tabular-nums">{formatCurrency(t.amount, currency)}</span>
            </Link>
          </li>
        ))}
      </ul>
      <p className="text-right text-sm text-muted-foreground">
        {transactions.length} transactions · {formatCurrency(total, currency)}
      </p>
    </div>
  )
}
