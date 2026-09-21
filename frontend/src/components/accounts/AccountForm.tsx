import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { FieldError, Input, Label, Select } from '@/components/ui/input'
import { accountService } from '@/services/accountService'
import { errorMessage } from '@/services/api'
import type { Account, AccountType } from '@/types/transaction'

const accountTypes: { value: AccountType; label: string }[] = [
  { value: 'savings', label: 'Savings' },
  { value: 'checking', label: 'Current / checking' },
  { value: 'credit', label: 'Credit card' },
  { value: 'digital_wallet', label: 'Digital wallet' },
]

export function AccountForm({ onCreated, onCancel }: { onCreated?: (account: Account) => void; onCancel?: () => void }) {
  const queryClient = useQueryClient()
  const [accountName, setAccountName] = useState('')
  const [accountType, setAccountType] = useState<AccountType>('savings')
  const [bankName, setBankName] = useState('')
  const [last4, setLast4] = useState('')
  const [error, setError] = useState<string>()

  const mutation = useMutation({
    mutationFn: () =>
      accountService.create({
        accountName: accountName.trim(),
        accountType,
        bankName: bankName.trim() || undefined,
        accountNumberLast4: last4 || undefined,
      }),
    onSuccess: (account) => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
      onCreated?.(account)
    },
    onError: (err) => setError(errorMessage(err)),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(undefined)
    if (!accountName.trim()) return setError('Give the account a name')
    if (last4 && !/^\d{4}$/.test(last4)) return setError('Enter exactly the last 4 digits (never the full number)')
    mutation.mutate()
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-3 sm:grid-cols-2" aria-label="New account">
      <div className="space-y-1.5">
        <Label htmlFor="account-name">Account name</Label>
        <Input id="account-name" value={accountName} onChange={(e) => setAccountName(e.target.value)} placeholder="HDFC Savings" />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="account-type">Type</Label>
        <Select id="account-type" value={accountType} onChange={(e) => setAccountType(e.target.value as AccountType)}>
          {accountTypes.map((type) => (
            <option key={type.value} value={type.value}>{type.label}</option>
          ))}
        </Select>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="account-bank">Bank (optional)</Label>
        <Input id="account-bank" value={bankName} onChange={(e) => setBankName(e.target.value)} />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="account-last4">Last 4 digits (optional)</Label>
        <Input id="account-last4" inputMode="numeric" maxLength={4} value={last4}
          onChange={(e) => setLast4(e.target.value.replace(/\D/g, ''))} />
      </div>
      <div className="sm:col-span-2">
        <FieldError message={error} />
      </div>
      <div className="flex gap-2 sm:col-span-2">
        <Button type="submit" disabled={mutation.isPending}>{mutation.isPending ? 'Saving…' : 'Add account'}</Button>
        {onCancel && <Button variant="ghost" onClick={onCancel}>Cancel</Button>}
      </div>
    </form>
  )
}
