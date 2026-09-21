import { useMemo, useState, type FormEvent } from 'react'
import { format } from 'date-fns'
import { Button } from '@/components/ui/button'
import { FieldError, Input, Label, Select } from '@/components/ui/input'
import { useAccounts, useCategories } from '@/hooks/useReferenceData'
import { parseAmount } from '@/utils/validators'
import type { TransactionInput } from '@/services/transactionService'
import type { PaymentMethod, Transaction, TransactionType } from '@/types/transaction'

interface Props {
  initial?: Transaction
  submitLabel: string
  busy?: boolean
  serverError?: string
  onSubmit: (input: TransactionInput) => void
  onCancel?: () => void
}

type Errors = Partial<Record<'merchantName' | 'amount' | 'transactionDate' | 'accountId', string>>

/** Create/edit form with client-side validation matching the backend rules. */
export function TransactionForm({ initial, submitLabel, busy, serverError, onSubmit, onCancel }: Props) {
  const { data: accounts } = useAccounts()
  const { data: categories } = useCategories()
  const today = format(new Date(), 'yyyy-MM-dd')

  const [accountId, setAccountId] = useState(initial?.accountId ?? '')
  const [merchantName, setMerchantName] = useState(initial?.merchantName ?? '')
  const [amount, setAmount] = useState(initial ? String(initial.amount) : '')
  const [transactionType, setTransactionType] = useState<TransactionType>(initial?.transactionType ?? 'debit')
  const [transactionDate, setTransactionDate] = useState(initial?.transactionDate ?? today)
  const [categoryId, setCategoryId] = useState(initial?.categoryId ?? '')
  const [subcategoryId, setSubcategoryId] = useState(initial?.subcategoryId ?? '')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | ''>(initial?.paymentMethod ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [errors, setErrors] = useState<Errors>({})

  const effectiveAccountId = accountId || accounts?.find((a) => a.isPrimary)?.id || accounts?.[0]?.id || ''
  const subcategories = useMemo(
    () => categories?.find((c) => c.id === categoryId)?.subcategories ?? [],
    [categories, categoryId],
  )

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const next: Errors = {}
    const parsedAmount = parseAmount(amount)
    if (!merchantName.trim()) next.merchantName = 'Enter the merchant or payee'
    if (parsedAmount === null) next.amount = 'Enter a positive amount with at most 2 decimals'
    if (!transactionDate) next.transactionDate = 'Choose a date'
    else if (transactionDate > today) next.transactionDate = 'Date cannot be in the future'
    if (!effectiveAccountId) next.accountId = 'Add an account first (Settings → Accounts)'
    setErrors(next)
    if (Object.keys(next).length > 0) return
    onSubmit({
      accountId: effectiveAccountId,
      merchantName: merchantName.trim(),
      amount: parsedAmount!,
      transactionType,
      transactionDate,
      categoryId: categoryId || undefined,
      subcategoryId: subcategoryId || undefined,
      paymentMethod: paymentMethod || undefined,
      description: description.trim() || undefined,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-4" noValidate aria-label={submitLabel}>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5 sm:col-span-2">
          <Label htmlFor="tx-merchant">Merchant / payee</Label>
          <Input id="tx-merchant" value={merchantName} onChange={(e) => setMerchantName(e.target.value)}
            aria-invalid={Boolean(errors.merchantName)} aria-describedby="tx-merchant-error" />
          <FieldError id="tx-merchant-error" message={errors.merchantName} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="tx-amount">Amount</Label>
          <Input id="tx-amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)}
            aria-invalid={Boolean(errors.amount)} aria-describedby="tx-amount-error" />
          <FieldError id="tx-amount-error" message={errors.amount} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="tx-type">Type</Label>
          <Select id="tx-type" value={transactionType} onChange={(e) => setTransactionType(e.target.value as TransactionType)}>
            <option value="debit">Spending</option>
            <option value="credit">Income</option>
            <option value="transfer">Transfer</option>
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="tx-date">Date</Label>
          <Input id="tx-date" type="date" max={today} value={transactionDate} onChange={(e) => setTransactionDate(e.target.value)}
            aria-invalid={Boolean(errors.transactionDate)} aria-describedby="tx-date-error" />
          <FieldError id="tx-date-error" message={errors.transactionDate} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="tx-account">Account</Label>
          <Select id="tx-account" value={effectiveAccountId} onChange={(e) => setAccountId(e.target.value)}>
            {accounts?.map((account) => (
              <option key={account.id} value={account.id}>{account.accountName}</option>
            ))}
          </Select>
          <FieldError message={errors.accountId} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="tx-category">Category</Label>
          <Select id="tx-category" value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setSubcategoryId('') }}>
            <option value="">Detect automatically</option>
            {categories?.map((category) => (
              <option key={category.id} value={category.id}>{category.categoryName}</option>
            ))}
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="tx-subcategory">Subcategory</Label>
          <Select id="tx-subcategory" value={subcategoryId} onChange={(e) => setSubcategoryId(e.target.value)} disabled={subcategories.length === 0}>
            <option value="">None</option>
            {subcategories.map((sub) => (
              <option key={sub.id} value={sub.id}>{sub.subcategoryName}</option>
            ))}
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="tx-method">Payment method</Label>
          <Select id="tx-method" value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod | '')}>
            <option value="">Not specified</option>
            <option value="upi">UPI</option>
            <option value="card">Card</option>
            <option value="net_banking">Net banking</option>
            <option value="cash">Cash</option>
            <option value="wallet">Wallet</option>
          </Select>
        </div>
        <div className="space-y-1.5 sm:col-span-2">
          <Label htmlFor="tx-description">Note (optional)</Label>
          <Input id="tx-description" value={description} maxLength={1000} onChange={(e) => setDescription(e.target.value)} />
        </div>
      </div>
      <FieldError message={serverError} />
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>{busy ? 'Saving…' : submitLabel}</Button>
        {onCancel && <Button variant="ghost" onClick={onCancel}>Cancel</Button>}
      </div>
    </form>
  )
}
