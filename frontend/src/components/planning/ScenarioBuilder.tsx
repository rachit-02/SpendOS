import { useState, type FormEvent } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { FieldError, Input, Label, Select } from '@/components/ui/input'
import { useCategories } from '@/hooks/useReferenceData'
import { scenarioTypes } from './scenarioLabels'
import type { Scenario, ScenarioType } from '@/types/planning'

interface Row {
  type: ScenarioType
  categoryId: string
  amount: string
}

const blankRow: Row = { type: 'spend_reduction', categoryId: '', amount: '' }

/** Parses a signed amount; only income changes may be negative. */
function parseSigned(value: string, allowNegative: boolean): number | null {
  const normalized = value.replace(/[,\s₹$]/g, '')
  if (!/^-?\d+(\.\d{1,2})?$/.test(normalized)) return null
  const amount = Number(normalized)
  if (amount === 0 || (!allowNegative && amount < 0)) return null
  return amount
}

/** Builds up to ten what-if changes and submits them as one named simulation. */
export function ScenarioBuilder({ busy, serverError, onSubmit }: {
  busy?: boolean
  serverError?: string
  onSubmit: (name: string, scenarios: Scenario[]) => void
}) {
  const { data: categories } = useCategories()
  const [name, setName] = useState('')
  const [rows, setRows] = useState<Row[]>([blankRow])
  const [error, setError] = useState<string>()

  function updateRow(index: number, change: Partial<Row>) {
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...change } : row)))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    setError(undefined)
    if (!name.trim()) return setError('Give the scenario a name')
    const scenarios: Scenario[] = []
    for (const row of rows) {
      const amount = parseSigned(row.amount, row.type === 'income_change')
      if (amount === null) {
        return setError(row.type === 'income_change'
          ? 'Enter a non-zero amount for each change'
          : 'Enter a positive amount for each change')
      }
      const hasCategory = row.type === 'spend_reduction' || row.type === 'spend_increase'
      scenarios.push({
        type: row.type,
        amount,
        categoryId: hasCategory && row.categoryId ? row.categoryId : undefined,
        period: row.type === 'one_time_purchase' ? 'one_time' : 'monthly',
      })
    }
    onSubmit(name.trim(), scenarios)
  }

  return (
    <form onSubmit={submit} noValidate aria-label="What-if scenario" className="space-y-4">
      <div className="space-y-1.5">
        <Label htmlFor="scenario-name">Scenario name</Label>
        <Input id="scenario-name" value={name} maxLength={255} placeholder="Cook at home more"
          onChange={(e) => setName(e.target.value)} />
      </div>
      <fieldset className="space-y-3">
        <legend className="text-sm font-medium">Changes</legend>
        {rows.map((row, index) => {
          const hint = scenarioTypes.find((t) => t.value === row.type)?.hint
          const hasCategory = row.type === 'spend_reduction' || row.type === 'spend_increase'
          return (
            <div key={index} className="space-y-1">
              <div className="flex flex-wrap gap-2">
                <Select aria-label={`Change ${index + 1} type`} className="w-44" value={row.type}
                  onChange={(e) => updateRow(index, { type: e.target.value as ScenarioType })}>
                  {scenarioTypes.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                </Select>
                {hasCategory && (
                  <Select aria-label={`Change ${index + 1} category`} className="w-44" value={row.categoryId}
                    onChange={(e) => updateRow(index, { categoryId: e.target.value })}>
                    <option value="">All spending</option>
                    {categories?.filter((c) => c.categoryName !== 'Income')
                      .map((c) => <option key={c.id} value={c.id}>{c.categoryName}</option>)}
                  </Select>
                )}
                <Input aria-label={`Change ${index + 1} amount`} inputMode="decimal" className="w-36" value={row.amount}
                  placeholder={row.type === 'one_time_purchase' ? 'Amount' : 'Per month'}
                  onChange={(e) => updateRow(index, { amount: e.target.value })} />
                {rows.length > 1 && (
                  <Button variant="ghost" size="icon" aria-label={`Remove change ${index + 1}`}
                    onClick={() => setRows((current) => current.filter((_, i) => i !== index))}>
                    <Trash2 className="h-4 w-4" />
                  </Button>
                )}
              </div>
              <p className="text-xs text-muted-foreground">{hint}</p>
            </div>
          )
        })}
        {rows.length < 10 && (
          <Button variant="outline" size="sm" onClick={() => setRows((current) => [...current, blankRow])}>
            <Plus className="h-4 w-4" aria-hidden="true" /> Add another change
          </Button>
        )}
      </fieldset>
      <FieldError message={error ?? serverError} />
      <Button type="submit" disabled={busy}>{busy ? 'Calculating…' : 'Run simulation'}</Button>
    </form>
  )
}
