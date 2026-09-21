import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/feedback'
import { CategoryDot, Money } from '@/components/common/Money'
import { TransactionForm } from './TransactionForm'
import { transactionService, type TransactionInput } from '@/services/transactionService'
import { errorMessage } from '@/services/api'
import { formatDate, formatDateTime } from '@/utils/formatters'
import type { Transaction } from '@/types/transaction'

const sourceLabels: Record<string, string> = {
  user: 'Set by you',
  merchant_mapping: 'From merchant',
  rule: 'Automatic rule',
  ml: 'Model',
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-2 text-sm">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-right">{children}</dd>
    </div>
  )
}

/** Only fields the user actually changed, so an untouched category is not re-marked as user-set. */
function changedFields(original: Transaction, input: TransactionInput): Partial<TransactionInput> {
  const changes: Partial<TransactionInput> = {}
  if (input.merchantName !== original.merchantName) changes.merchantName = input.merchantName
  if (input.amount !== original.amount) changes.amount = input.amount
  if (input.transactionType !== original.transactionType) changes.transactionType = input.transactionType
  if (input.transactionDate !== original.transactionDate) changes.transactionDate = input.transactionDate
  if (input.accountId !== original.accountId) changes.accountId = input.accountId
  if ((input.categoryId ?? '') !== (original.categoryId ?? '') || (input.subcategoryId ?? '') !== (original.subcategoryId ?? '')) {
    changes.categoryId = input.categoryId
    changes.subcategoryId = input.subcategoryId
  }
  if ((input.paymentMethod ?? '') !== (original.paymentMethod ?? '')) changes.paymentMethod = input.paymentMethod
  if ((input.description ?? '') !== (original.description ?? '')) changes.description = input.description ?? ''
  return changes
}

/** Full transaction details with inline edit (category/merchant correction) and delete. */
export function TransactionDetail({ transaction, onDeleted }: { transaction: Transaction; onDeleted: () => void }) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [current, setCurrent] = useState(transaction)

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['transactions'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const update = useMutation({
    mutationFn: (input: Parameters<typeof transactionService.update>[1]) => transactionService.update(current.id, input),
    onSuccess: (updated) => {
      setCurrent(updated)
      setEditing(false)
      invalidate()
    },
  })

  const remove = useMutation({
    mutationFn: () => transactionService.remove(current.id),
    onSuccess: () => {
      invalidate()
      onDeleted()
    },
  })

  if (editing) {
    return (
      <TransactionForm
        initial={current}
        submitLabel="Save changes"
        busy={update.isPending}
        serverError={update.isError ? errorMessage(update.error) : undefined}
        onSubmit={(input) => update.mutate(changedFields(current, input))}
        onCancel={() => setEditing(false)}
      />
    )
  }

  const confidence = current.categorizationConfidence
  return (
    <div className="space-y-5">
      <div className="text-center">
        <p className="text-sm text-muted-foreground">{current.merchantName ?? current.rawDescription}</p>
        <p className="text-3xl font-semibold">
          <Money amount={current.amount} currency={current.currencyCode} type={current.transactionType} />
        </p>
        <p className="text-sm text-muted-foreground">{formatDate(current.transactionDate, 'EEEE, d MMMM yyyy')}</p>
      </div>
      <dl className="divide-y rounded-lg border px-4">
        <Row label="Category">
          <CategoryDot color={current.categoryColor} name={current.subcategoryName ? `${current.categoryName} · ${current.subcategoryName}` : current.categoryName} />
        </Row>
        <Row label="Categorized">
          {sourceLabels[current.categorizationSource ?? ''] ?? '—'}
          {confidence !== undefined && confidence !== null && confidence < 0.6 && (
            <Badge tone="warning" className="ml-2">Please review</Badge>
          )}
        </Row>
        <Row label="Type"><span className="capitalize">{current.transactionType}</span></Row>
        <Row label="Payment method"><span className="uppercase">{current.paymentMethod?.replace('_', ' ') ?? '—'}</span></Row>
        {current.description && <Row label="Note">{current.description}</Row>}
        {current.rawDescription && <Row label="Statement text"><span className="font-mono text-xs">{current.rawDescription}</span></Row>}
        {current.externalReference && <Row label="Reference"><span className="font-mono text-xs">{current.externalReference}</span></Row>}
        <Row label="Recurring">{current.isRecurring ? 'Yes' : 'No'}</Row>
        <Row label="Added">{formatDateTime(current.createdAt)}</Row>
      </dl>
      {remove.isError && <p role="alert" className="text-sm text-destructive">{errorMessage(remove.error)}</p>}
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => setEditing(true)}>
          <Pencil className="h-4 w-4" aria-hidden="true" /> Edit / recategorize
        </Button>
        {confirmDelete ? (
          <>
            <Button variant="destructive" disabled={remove.isPending} onClick={() => remove.mutate()}>Confirm delete</Button>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>Cancel</Button>
          </>
        ) : (
          <Button variant="outline" className="text-destructive" onClick={() => setConfirmDelete(true)}>
            <Trash2 className="h-4 w-4" aria-hidden="true" /> Delete
          </Button>
        )}
      </div>
    </div>
  )
}
