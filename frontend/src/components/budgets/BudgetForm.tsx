import { useState, type FormEvent } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { FieldError, Input, Label, Select } from '@/components/ui/input'
import { useCategories } from '@/hooks/useReferenceData'
import { parseAmount } from '@/utils/validators'
import { formatCurrency } from '@/utils/formatters'
import { currentMonth } from '@/utils/months'
import type { Budget, BudgetInput, BudgetType } from '@/types/budgets'

interface Row {
  categoryId: string
  amount: string
}

interface Props {
  initial?: Budget
  busy?: boolean
  serverError?: string
  currency: string
  onSubmit: (input: BudgetInput) => void
  onCancel: () => void
}

function firstOfMonth(): string {
  const { month, year } = currentMonth()
  return `${year}-${String(month).padStart(2, '0')}-01`
}

/** Budget editor: period, total, alert threshold and optional category allocations. */
export function BudgetForm({ initial, busy, serverError, currency, onSubmit, onCancel }: Props) {
  const { data: categories } = useCategories()
  const [budgetName, setBudgetName] = useState(initial?.budgetName ?? '')
  const [budgetType, setBudgetType] = useState<BudgetType>(initial?.budgetType ?? 'monthly')
  const [startDate, setStartDate] = useState(initial?.startDate ?? firstOfMonth())
  const [endDate, setEndDate] = useState(initial?.budgetType === 'custom' ? initial.endDate : '')
  const [total, setTotal] = useState(initial ? String(initial.totalAmount) : '')
  const [threshold, setThreshold] = useState(initial?.alertThreshold ?? 90)
  const [rows, setRows] = useState<Row[]>(
    initial?.categories.map((c) => ({ categoryId: c.categoryId, amount: String(c.allocatedAmount) })) ?? [],
  )
  const [error, setError] = useState<string>()

  const allocated = rows.reduce((sum, row) => sum + (parseAmount(row.amount) ?? 0), 0)
  const totalValue = parseAmount(total)
  const usedCategoryIds = new Set(rows.map((r) => r.categoryId))

  function updateRow(index: number, change: Partial<Row>) {
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...change } : row)))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(undefined)
    if (!budgetName.trim()) return setError('Give the budget a name')
    if (totalValue === null) return setError('Enter a positive total amount')
    if (budgetType === 'custom' && (!endDate || endDate <= startDate)) return setError('Choose an end date after the start date')
    if (rows.some((row) => !row.categoryId || parseAmount(row.amount) === null)) {
      return setError('Each category needs a category and a positive amount')
    }
    if (allocated > totalValue) return setError('Category allocations add up to more than the total')
    onSubmit({
      budgetName: budgetName.trim(),
      budgetType,
      totalAmount: totalValue,
      startDate,
      endDate: budgetType === 'custom' ? endDate : undefined,
      alertThreshold: threshold,
      categories: rows.map((row) => ({ categoryId: row.categoryId, allocatedAmount: parseAmount(row.amount)! })),
    })
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-4" noValidate aria-label="Budget">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5 sm:col-span-2">
          <Label htmlFor="budget-name">Name</Label>
          <Input id="budget-name" value={budgetName} onChange={(e) => setBudgetName(e.target.value)} placeholder="Monthly spending" />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="budget-type">Period</Label>
          <Select id="budget-type" value={budgetType} onChange={(e) => setBudgetType(e.target.value as BudgetType)}>
            <option value="monthly">Monthly</option>
            <option value="quarterly">Quarterly</option>
            <option value="annual">Annual</option>
            <option value="custom">Custom dates</option>
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="budget-start">Starts</Label>
          <Input id="budget-start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        </div>
        {budgetType === 'custom' && (
          <div className="space-y-1.5">
            <Label htmlFor="budget-end">Ends</Label>
            <Input id="budget-end" type="date" value={endDate} min={startDate} onChange={(e) => setEndDate(e.target.value)} />
          </div>
        )}
        <div className="space-y-1.5">
          <Label htmlFor="budget-total">Total limit</Label>
          <Input id="budget-total" inputMode="decimal" value={total} onChange={(e) => setTotal(e.target.value)} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="budget-threshold">Alert me at {threshold}% spent</Label>
          <input id="budget-threshold" type="range" min={50} max={100} step={5} value={threshold}
            onChange={(e) => setThreshold(Number(e.target.value))} className="w-full accent-[hsl(var(--primary))]" />
        </div>
      </div>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Category limits (optional)</legend>
        <p className="text-xs text-muted-foreground">
          Without categories the budget covers all spending. With categories it covers only those.
        </p>
        {rows.map((row, index) => (
          <div key={index} className="flex gap-2">
            <Select aria-label={`Category ${index + 1}`} value={row.categoryId} onChange={(e) => updateRow(index, { categoryId: e.target.value })}>
              <option value="">Choose category</option>
              {categories
                ?.filter((c) => c.categoryName !== 'Income' && (c.id === row.categoryId || !usedCategoryIds.has(c.id)))
                .map((c) => <option key={c.id} value={c.id}>{c.categoryName}</option>)}
            </Select>
            <Input aria-label={`Limit for category ${index + 1}`} inputMode="decimal" className="w-36" value={row.amount}
              onChange={(e) => updateRow(index, { amount: e.target.value })} />
            <Button variant="ghost" size="icon" aria-label={`Remove category ${index + 1}`}
              onClick={() => setRows((current) => current.filter((_, i) => i !== index))}>
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        ))}
        <div className="flex items-center justify-between">
          <Button variant="outline" size="sm" onClick={() => setRows((current) => [...current, { categoryId: '', amount: '' }])}>
            <Plus className="h-4 w-4" aria-hidden="true" /> Add category limit
          </Button>
          {rows.length > 0 && (
            <span className={allocated > (totalValue ?? 0) ? 'text-sm text-destructive' : 'text-sm text-muted-foreground'}>
              Allocated {formatCurrency(allocated, currency)} of {formatCurrency(totalValue ?? 0, currency)}
            </span>
          )}
        </div>
      </fieldset>

      <FieldError message={error ?? serverError} />
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>{busy ? 'Saving…' : initial ? 'Save budget' : 'Create budget'}</Button>
        <Button variant="ghost" onClick={onCancel}>Cancel</Button>
      </div>
    </form>
  )
}
