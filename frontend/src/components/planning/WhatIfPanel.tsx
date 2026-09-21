import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge, EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { simulationService } from '@/services/planningService'
import { errorMessage } from '@/services/api'
import { formatCurrencyRounded, formatDate } from '@/utils/formatters'
import { ScenarioBuilder } from './ScenarioBuilder'
import { SimulationResult } from './SimulationResult'
import type { Scenario, SimulationComparison } from '@/types/planning'

function ComparisonTable({ comparison, currency }: { comparison: SimulationComparison; currency: string }) {
  const money = (value: number) => formatCurrencyRounded(value, currency)
  const rows: { label: string; value: (s: SimulationComparison['simulations'][number]) => string }[] = [
    { label: 'Monthly spending', value: (s) => money(s.results.monthlyImpact.after) },
    { label: 'Monthly savings', value: (s) => money(s.results.monthlySavings.after) },
    { label: 'Yearly savings', value: (s) => money(s.results.annualSavings.after) },
    { label: 'Yearly savings change', value: (s) => money(s.results.annualSavings.change) },
  ]
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm" aria-label="Scenario comparison">
        <thead>
          <tr className="border-b">
            <th scope="col" className="py-2 text-left"><span className="sr-only">Measure</span></th>
            {comparison.simulations.map((s) => (
              <th key={s.id} scope="col" className="py-2 text-right font-medium">
                {s.simulationName}
                {s.id === comparison.bestForSavingsId && <Badge tone="success" className="ml-2">Best for savings</Badge>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label} className="border-b last:border-0">
              <th scope="row" className="py-2 text-left font-normal text-muted-foreground">{row.label}</th>
              {comparison.simulations.map((s) => <td key={s.id} className="py-2 text-right">{row.value(s)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Scenario builder, the latest result, saved scenarios and a side-by-side comparison. */
export function WhatIfPanel({ currency }: { currency: string }) {
  const queryClient = useQueryClient()
  const [latestId, setLatestId] = useState<string>()
  const [selected, setSelected] = useState<string[]>([])
  const saved = useQuery({ queryKey: ['simulations'], queryFn: simulationService.list })

  const create = useMutation({
    mutationFn: ({ name, scenarios }: { name: string; scenarios: Scenario[] }) =>
      simulationService.create(name, scenarios),
    onSuccess: (simulation) => {
      setLatestId(simulation.id)
      queryClient.invalidateQueries({ queryKey: ['simulations'] })
    },
  })
  const remove = useMutation({
    mutationFn: (id: string) => simulationService.remove(id),
    onSuccess: (_, id) => {
      setSelected((current) => current.filter((s) => s !== id))
      if (latestId === id) setLatestId(undefined)
      queryClient.invalidateQueries({ queryKey: ['simulations'] })
    },
  })
  const compare = useMutation({ mutationFn: (ids: string[]) => simulationService.compare(ids) })

  const latest = create.data?.id === latestId ? create.data : undefined
  const toggle = (id: string) =>
    setSelected((current) => (current.includes(id) ? current.filter((s) => s !== id) : [...current, id].slice(-4)))

  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>What if…?</CardTitle>
          <CardDescription>Try changes against your real average month. Nothing touches your actual data.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <ScenarioBuilder busy={create.isPending} serverError={create.isError ? errorMessage(create.error) : undefined}
            onSubmit={(name, scenarios) => create.mutate({ name, scenarios })} />
          {latest && (
            <section aria-label="Simulation result" className="border-t pt-4">
              <h3 className="mb-3 font-semibold">{latest.simulationName}</h3>
              <SimulationResult simulation={latest} currency={currency} />
            </section>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Saved scenarios</CardTitle>
          <CardDescription>Select two or more to compare them against your current numbers.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {saved.isLoading && <Spinner />}
          {saved.isError && <ErrorState message={errorMessage(saved.error)} />}
          {saved.data?.length === 0 && (
            <EmptyState title="No saved scenarios" description="Scenarios you run are saved here." />
          )}
          {saved.data && saved.data.length > 0 && (
            <ul className="divide-y" aria-label="Saved scenarios">
              {saved.data.map((simulation) => (
                <li key={simulation.id} className="flex items-center gap-3 py-2">
                  <input type="checkbox" id={`sim-${simulation.id}`} checked={selected.includes(simulation.id)}
                    onChange={() => toggle(simulation.id)} className="h-4 w-4 accent-[hsl(var(--primary))]" />
                  <label htmlFor={`sim-${simulation.id}`} className="flex-1 text-sm">
                    <span className="font-medium">{simulation.simulationName}</span>
                    <span className="block text-xs text-muted-foreground">
                      {simulation.results.monthlySavings.change >= 0 ? '+' : ''}
                      {formatCurrencyRounded(simulation.results.monthlySavings.change, currency)} savings a month · {formatDate(simulation.createdAt)}
                    </span>
                  </label>
                  <Button variant="ghost" size="icon" aria-label={`Delete ${simulation.simulationName}`}
                    disabled={remove.isPending} onClick={() => remove.mutate(simulation.id)}>
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </li>
              ))}
            </ul>
          )}
          <Button variant="outline" disabled={selected.length < 2 || compare.isPending} onClick={() => compare.mutate(selected)}>
            Compare {selected.length >= 2 ? selected.length : ''} scenarios
          </Button>
          {compare.isError && <p role="alert" className="text-sm text-destructive">{errorMessage(compare.error)}</p>}
          {compare.data && <ComparisonTable comparison={compare.data} currency={currency} />}
        </CardContent>
      </Card>
    </div>
  )
}
