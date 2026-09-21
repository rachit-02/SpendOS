import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge, EmptyState, Spinner } from '@/components/ui/feedback'
import { AccountForm } from '@/components/accounts/AccountForm'
import { useAccounts } from '@/hooks/useReferenceData'
import { accountService } from '@/services/accountService'

const typeLabels: Record<string, string> = {
  savings: 'Savings',
  checking: 'Current',
  credit: 'Credit card',
  digital_wallet: 'Wallet',
}

export function AccountsSettings() {
  const queryClient = useQueryClient()
  const { data: accounts, isLoading } = useAccounts()
  const [adding, setAdding] = useState(false)
  const makePrimary = useMutation({
    mutationFn: (id: string) => accountService.update(id, { isPrimary: true }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  })

  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between">
        <div>
          <CardTitle>Accounts</CardTitle>
          <CardDescription>Bank accounts, cards and wallets you import statements for.</CardDescription>
        </div>
        {!adding && <Button variant="outline" size="sm" onClick={() => setAdding(true)}>Add account</Button>}
      </CardHeader>
      <CardContent className="space-y-4">
        {adding && <AccountForm onCreated={() => setAdding(false)} onCancel={() => setAdding(false)} />}
        {isLoading && <Spinner />}
        {accounts && accounts.length === 0 && !adding && (
          <EmptyState title="No accounts yet" description="One is created automatically on your first import." />
        )}
        {accounts && accounts.length > 0 && (
          <ul className="divide-y rounded-md border">
            {accounts.map((account) => (
              <li key={account.id} className="flex flex-wrap items-center justify-between gap-2 p-3">
                <div>
                  <p className="font-medium">
                    {account.accountName}{' '}
                    {account.accountNumberMasked && (
                      <span className="text-sm font-normal text-muted-foreground">{account.accountNumberMasked}</span>
                    )}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {typeLabels[account.accountType] ?? account.accountType}
                    {account.bankName ? ` · ${account.bankName}` : ''}
                  </p>
                </div>
                {account.isPrimary ? (
                  <Badge tone="primary">Primary</Badge>
                ) : (
                  <Button variant="ghost" size="sm" onClick={() => makePrimary.mutate(account.id)}>
                    Make primary
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
