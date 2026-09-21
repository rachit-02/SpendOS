import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { CheckCircle2, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { FieldError, Input, Label } from '@/components/ui/input'
import { planningService } from '@/services/planningService'
import { errorMessage } from '@/services/api'
import { formatCurrencyRounded } from '@/utils/formatters'
import { parseAmount } from '@/utils/validators'

/** "Can I afford this?": weighs a purchase against this month's income, spending, budgets and goals. */
export function AffordabilityCheck({ currency }: { currency: string }) {
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string>()
  const check = useMutation({
    mutationFn: (value: number) => planningService.affordability(value, description.trim() || undefined),
  })
  const money = (value: number) => formatCurrencyRounded(value, currency)

  function submit(event: FormEvent) {
    event.preventDefault()
    const value = parseAmount(amount)
    if (value === null) return setError('Enter a positive amount')
    setError(undefined)
    check.mutate(value)
  }

  const result = check.data
  const verdictClass = result?.affordability.isAffordable ? 'text-success' : 'text-destructive'
  return (
    <Card>
      <CardHeader>
        <CardTitle>Can I afford this?</CardTitle>
        <CardDescription>Checks a purchase against this month&apos;s expected income, spending and goals.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form onSubmit={submit} noValidate aria-label="Affordability check"
          className="grid gap-3 sm:grid-cols-[1fr_10rem_auto] sm:items-end">
          <div className="space-y-1.5">
            <Label htmlFor="afford-description">What is it?</Label>
            <Input id="afford-description" value={description} maxLength={255} placeholder="New laptop"
              onChange={(e) => setDescription(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="afford-amount">Price</Label>
            <Input id="afford-amount" inputMode="decimal" value={amount} aria-invalid={Boolean(error)}
              onChange={(e) => setAmount(e.target.value)} />
          </div>
          <Button type="submit" disabled={check.isPending}>{check.isPending ? 'Checking…' : 'Check'}</Button>
        </form>
        <FieldError message={error ?? (check.isError ? errorMessage(check.error) : undefined)} />

        {result && (
          <div role="status" className="space-y-3 rounded-lg border p-4">
            <p className={`flex items-center gap-2 font-semibold ${verdictClass}`}>
              {result.affordability.isAffordable
                ? <CheckCircle2 className="h-5 w-5" aria-hidden="true" />
                : <XCircle className="h-5 w-5" aria-hidden="true" />}
              {result.affordability.isAffordable ? 'Likely affordable' : 'Not affordable this month'}
              <span className="text-xs font-normal text-muted-foreground">({result.affordability.confidence} confidence)</span>
            </p>
            <p className="text-sm">{result.explanation}</p>
            <dl className="grid gap-2 text-sm sm:grid-cols-2">
              <div className="flex justify-between gap-2">
                <dt className="text-muted-foreground">Savings without it</dt><dd>{money(result.analysis.projectedSavingsWithoutPurchase)}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-muted-foreground">Savings with it</dt><dd>{money(result.analysis.projectedSavingsWithPurchase)}</dd>
              </div>
            </dl>
            {result.considerations.length > 0 && (
              <ul className="list-disc space-y-1 pl-5 text-sm" aria-label="Considerations">
                {result.considerations.map((item) => <li key={item}>{item}</li>)}
              </ul>
            )}
            <p className="text-xs text-muted-foreground">{result.disclaimer}</p>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
