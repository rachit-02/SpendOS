import { formatCurrency } from '@/utils/formatters'
import type { Dashboard } from '@/types/dashboard'

/** Horizontal bars relative to the largest merchant; clicking opens that merchant's transactions. */
export function TopMerchants({ merchants, currency, onSelect }: {
  merchants: Dashboard['spending']['topMerchants']
  currency: string
  onSelect: (merchantId?: string) => void
}) {
  const max = Math.max(...merchants.map((m) => m.amount), 1)
  return (
    <ol className="space-y-3" aria-label="Top merchants">
      {merchants.map((merchant) => (
        <li key={merchant.merchantName}>
          <button type="button" onClick={() => onSelect(merchant.merchantId)} className="w-full space-y-1 text-left">
            <div className="flex justify-between text-sm">
              <span className="font-medium hover:underline">{merchant.merchantName}</span>
              <span className="tabular-nums">{formatCurrency(merchant.amount, currency)}</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                <div className="h-full rounded-full bg-primary/80" style={{ width: `${(merchant.amount / max) * 100}%` }} />
              </div>
              <span className="w-20 text-right text-xs text-muted-foreground">
                {merchant.count} {merchant.count === 1 ? 'payment' : 'payments'}
              </span>
            </div>
          </button>
        </li>
      ))}
    </ol>
  )
}
