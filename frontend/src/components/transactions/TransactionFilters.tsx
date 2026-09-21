import { useEffect, useId, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search, SlidersHorizontal, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input, Label, Select } from '@/components/ui/input'
import { useCategories } from '@/hooks/useReferenceData'
import { transactionService, type TransactionQuery } from '@/services/transactionService'

interface Props {
  query: TransactionQuery
  onChange: (changes: Record<string, string | undefined>) => void
  onClear: () => void
  activeCount: number
}

function useDebounced<T>(value: T, delay = 250): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}

/** Search box with merchant/category suggestions plus an expandable panel of filters. */
export function TransactionFilters({ query, onChange, onClear, activeCount }: Props) {
  const { data: categories } = useCategories()
  const [search, setSearch] = useState(query.searchText ?? '')
  const [showSuggestions, setShowSuggestions] = useState(false)
  const [expanded, setExpanded] = useState(activeCount > (query.searchText ? 1 : 0))
  const debouncedSearch = useDebounced(search)
  const listId = useId()
  const wrapperRef = useRef<HTMLDivElement>(null)

  useEffect(() => setSearch(query.searchText ?? ''), [query.searchText])

  useEffect(() => {
    if ((query.searchText ?? '') !== debouncedSearch) onChange({ searchText: debouncedSearch || undefined })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch])

  const suggestions = useQuery({
    queryKey: ['suggestions', debouncedSearch],
    queryFn: () => transactionService.suggestions(debouncedSearch),
    enabled: showSuggestions,
    staleTime: 60_000,
  })

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!wrapperRef.current?.contains(event.target as Node)) setShowSuggestions(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const hasSuggestions = Boolean(
    suggestions.data && (suggestions.data.merchants.length > 0 || suggestions.data.categories.length > 0),
  )

  return (
    <div className="space-y-3">
      <div className="flex flex-col gap-2 sm:flex-row">
        <div ref={wrapperRef} className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <Input
            type="search"
            role="combobox"
            aria-label="Search transactions"
            aria-expanded={showSuggestions && hasSuggestions}
            aria-controls={listId}
            aria-autocomplete="list"
            placeholder="Search merchant or description"
            className="pl-9"
            value={search}
            onFocus={() => setShowSuggestions(true)}
            onChange={(event) => {
              setSearch(event.target.value)
              setShowSuggestions(true)
            }}
            onKeyDown={(event) => event.key === 'Escape' && setShowSuggestions(false)}
          />
          {showSuggestions && hasSuggestions && (
            <ul id={listId} role="listbox" className="absolute z-20 mt-1 w-full overflow-hidden rounded-md border bg-card py-1 shadow-lg">
              {suggestions.data!.merchants.map((merchant) => (
                <li key={merchant.merchantId} role="option" aria-selected="false">
                  <button
                    type="button"
                    className="flex w-full justify-between px-3 py-2 text-left text-sm hover:bg-muted"
                    onClick={() => {
                      onChange({ merchantId: merchant.merchantId, searchText: undefined })
                      setSearch('')
                      setShowSuggestions(false)
                      setExpanded(true)
                    }}
                  >
                    <span>{merchant.merchantName}</span>
                    <span className="text-xs text-muted-foreground">{merchant.count} transactions</span>
                  </button>
                </li>
              ))}
              {suggestions.data!.categories.map((category) => (
                <li key={category.categoryId} role="option" aria-selected="false">
                  <button
                    type="button"
                    className="flex w-full justify-between px-3 py-2 text-left text-sm hover:bg-muted"
                    onClick={() => {
                      onChange({ categoryId: category.categoryId, searchText: undefined })
                      setSearch('')
                      setShowSuggestions(false)
                      setExpanded(true)
                    }}
                  >
                    <span>Category: {category.categoryName}</span>
                    <span className="text-xs text-muted-foreground">{category.count}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setExpanded((v) => !v)} aria-expanded={expanded} aria-controls="transaction-filter-panel">
            <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
            Filters{activeCount > 0 ? ` (${activeCount})` : ''}
          </Button>
          {activeCount > 0 && (
            <Button variant="ghost" onClick={onClear}>
              <X className="h-4 w-4" aria-hidden="true" />
              Clear
            </Button>
          )}
        </div>
      </div>

      {expanded && (
        <div id="transaction-filter-panel" className="grid gap-3 rounded-lg border bg-card p-4 sm:grid-cols-2 lg:grid-cols-4">
          <div className="space-y-1.5">
            <Label htmlFor="filter-start">From</Label>
            <Input id="filter-start" type="date" value={query.startDate ?? ''} onChange={(e) => onChange({ startDate: e.target.value })} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-end">To</Label>
            <Input id="filter-end" type="date" value={query.endDate ?? ''} onChange={(e) => onChange({ endDate: e.target.value })} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-category">Category</Label>
            <Select id="filter-category" value={query.categoryId ?? ''} onChange={(e) => onChange({ categoryId: e.target.value })}>
              <option value="">All categories</option>
              {categories?.map((category) => (
                <option key={category.id} value={category.id}>{category.categoryName}</option>
              ))}
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-type">Type</Label>
            <Select id="filter-type" value={query.transactionType ?? ''} onChange={(e) => onChange({ transactionType: e.target.value })}>
              <option value="">Income & spending</option>
              <option value="debit">Spending</option>
              <option value="credit">Income</option>
              <option value="transfer">Transfers</option>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-method">Payment method</Label>
            <Select id="filter-method" value={query.paymentMethod ?? ''} onChange={(e) => onChange({ paymentMethod: e.target.value })}>
              <option value="">Any method</option>
              <option value="upi">UPI</option>
              <option value="card">Card</option>
              <option value="net_banking">Net banking</option>
              <option value="cash">Cash</option>
              <option value="wallet">Wallet</option>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-min">Min amount</Label>
            <Input id="filter-min" type="number" min="0" inputMode="decimal" value={query.minAmount ?? ''}
              onChange={(e) => onChange({ minAmount: e.target.value })} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="filter-max">Max amount</Label>
            <Input id="filter-max" type="number" min="0" inputMode="decimal" value={query.maxAmount ?? ''}
              onChange={(e) => onChange({ maxAmount: e.target.value })} />
          </div>
          {query.merchantId && (
            <div className="flex items-end">
              <Button variant="outline" size="sm" onClick={() => onChange({ merchantId: undefined })}>
                <X className="h-3 w-3" aria-hidden="true" /> Merchant filter
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
