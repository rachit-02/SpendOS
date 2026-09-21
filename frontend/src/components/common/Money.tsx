import { cn } from '@/utils/cn'
import { formatCurrency } from '@/utils/formatters'
import type { TransactionType } from '@/types/transaction'

/** Signed amount: spending is shown with a minus, income in green with a plus. */
export function Money({ amount, currency = 'INR', type, className }: {
  amount: number
  currency?: string
  type?: TransactionType
  className?: string
}) {
  const sign = type === 'debit' ? '−' : type === 'credit' ? '+' : ''
  return (
    <span className={cn('tabular-nums', type === 'credit' && 'text-success', className)}>
      {sign}
      {formatCurrency(amount, currency)}
    </span>
  )
}

export function CategoryDot({ color, name }: { color?: string; name?: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span aria-hidden="true" className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: color ?? '#A9A9A9' }} />
      <span>{name ?? 'Uncategorized'}</span>
    </span>
  )
}
