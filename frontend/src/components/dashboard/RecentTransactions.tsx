import { Link } from 'react-router-dom'
import { Money } from '@/components/common/Money'
import { formatDate } from '@/utils/formatters'
import type { Dashboard } from '@/types/dashboard'

export function RecentTransactions({ transactions, currency }: {
  transactions: Dashboard['recentTransactions']
  currency: string
}) {
  return (
    <ul className="divide-y" aria-label="Recent transactions">
      {transactions.map((t) => (
        <li key={t.id}>
          <Link to={`/transactions/${t.id}`} className="flex items-center justify-between gap-3 py-2.5 hover:bg-muted/40">
            <div className="flex min-w-0 items-center gap-3">
              <span aria-hidden="true" className="h-2 w-2 shrink-0 rounded-full" style={{ backgroundColor: t.categoryColor ?? '#A9A9A9' }} />
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{t.merchantName ?? 'Unknown'}</p>
                <p className="text-xs text-muted-foreground">
                  {formatDate(t.transactionDate, 'd MMM')} · {t.categoryName ?? 'Uncategorized'}
                </p>
              </div>
            </div>
            <Money amount={t.amount} currency={currency} type={t.transactionType} className="text-sm font-medium" />
          </Link>
        </li>
      ))}
    </ul>
  )
}
