import { ArrowDown, ArrowUp, ArrowUpDown, Repeat } from 'lucide-react'
import { Table, TBody, Td, Th, THead, Tr } from '@/components/ui/table'
import { CategoryDot, Money } from '@/components/common/Money'
import { formatDate } from '@/utils/formatters'
import { cn } from '@/utils/cn'
import type { Transaction } from '@/types/transaction'
import type { TransactionQuery } from '@/services/transactionService'

type SortField = NonNullable<TransactionQuery['sortBy']>

interface Props {
  transactions: Transaction[]
  selected: Set<string>
  onToggle: (id: string) => void
  onToggleAll: () => void
  onOpen: (transaction: Transaction) => void
  sortBy: SortField
  sortOrder: 'asc' | 'desc'
  onSort: (field: SortField) => void
}

function SortHeader({ field, label, sortBy, sortOrder, onSort, className }: {
  field: SortField
  label: string
  sortBy: SortField
  sortOrder: 'asc' | 'desc'
  onSort: (field: SortField) => void
  className?: string
}) {
  const active = sortBy === field
  const Icon = !active ? ArrowUpDown : sortOrder === 'asc' ? ArrowUp : ArrowDown
  return (
    <Th className={className} aria-sort={active ? (sortOrder === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button type="button" onClick={() => onSort(field)} className="inline-flex items-center gap-1 uppercase hover:text-foreground">
        {label}
        <Icon className="h-3 w-3" aria-hidden="true" />
      </button>
    </Th>
  )
}

/** Sortable, selectable table on desktop; stacked cards on small screens. */
export function TransactionList({ transactions, selected, onToggle, onToggleAll, onOpen, sortBy, sortOrder, onSort }: Props) {
  const allSelected = transactions.length > 0 && transactions.every((t) => selected.has(t.id))

  return (
    <>
      <div className="hidden rounded-lg border bg-card md:block">
        <Table aria-label="Transactions">
          <THead>
            <tr>
              <Th className="w-10">
                <input type="checkbox" aria-label="Select all on this page" checked={allSelected} onChange={onToggleAll} />
              </Th>
              <SortHeader field="date" label="Date" sortBy={sortBy} sortOrder={sortOrder} onSort={onSort} />
              <SortHeader field="merchant" label="Merchant" sortBy={sortBy} sortOrder={sortOrder} onSort={onSort} />
              <Th>Category</Th>
              <Th>Method</Th>
              <SortHeader field="amount" label="Amount" sortBy={sortBy} sortOrder={sortOrder} onSort={onSort} className="text-right" />
            </tr>
          </THead>
          <TBody>
            {transactions.map((t) => (
              <Tr key={t.id} className={cn('cursor-pointer', selected.has(t.id) && 'bg-accent/50')} onClick={() => onOpen(t)}>
                <Td onClick={(event) => event.stopPropagation()}>
                  <input
                    type="checkbox"
                    aria-label={`Select ${t.merchantName ?? 'transaction'} on ${formatDate(t.transactionDate)}`}
                    checked={selected.has(t.id)}
                    onChange={() => onToggle(t.id)}
                  />
                </Td>
                <Td className="whitespace-nowrap text-muted-foreground">{formatDate(t.transactionDate)}</Td>
                <Td>
                  <button type="button" className="text-left font-medium hover:underline" onClick={(e) => { e.stopPropagation(); onOpen(t) }}>
                    {t.merchantName ?? t.rawDescription ?? 'Unknown'}
                  </button>
                  {t.isRecurring && <Repeat className="ml-1.5 inline h-3 w-3 text-muted-foreground" aria-label="Recurring" />}
                  {t.description && t.description !== t.merchantName && (
                    <p className="max-w-xs truncate text-xs text-muted-foreground">{t.description}</p>
                  )}
                </Td>
                <Td>
                  <CategoryDot color={t.categoryColor} name={t.subcategoryName ? `${t.categoryName} · ${t.subcategoryName}` : t.categoryName} />
                </Td>
                <Td className="uppercase text-xs text-muted-foreground">{t.paymentMethod?.replace('_', ' ') ?? '—'}</Td>
                <Td className="text-right font-medium">
                  <Money amount={t.amount} currency={t.currencyCode} type={t.transactionType} />
                </Td>
              </Tr>
            ))}
          </TBody>
        </Table>
      </div>

      <ul className="space-y-2 md:hidden" aria-label="Transactions">
        {transactions.map((t) => (
          <li key={t.id} className={cn('flex items-center gap-3 rounded-lg border bg-card p-3', selected.has(t.id) && 'border-primary')}>
            <input
              type="checkbox"
              aria-label={`Select ${t.merchantName ?? 'transaction'} on ${formatDate(t.transactionDate)}`}
              checked={selected.has(t.id)}
              onChange={() => onToggle(t.id)}
            />
            <button type="button" className="flex flex-1 items-center justify-between gap-3 text-left" onClick={() => onOpen(t)}>
              <div className="min-w-0">
                <p className="truncate font-medium">{t.merchantName ?? t.rawDescription ?? 'Unknown'}</p>
                <p className="text-xs text-muted-foreground">
                  {formatDate(t.transactionDate)} · {t.categoryName ?? 'Uncategorized'}
                </p>
              </div>
              <Money amount={t.amount} currency={t.currencyCode} type={t.transactionType} className="font-medium" />
            </button>
          </li>
        ))}
      </ul>
    </>
  )
}
