import { formatCurrencyRounded, formatPercent } from '@/utils/formatters'
import { scenarioLabel } from './scenarioLabels'
import type { Impact, Simulation } from '@/types/planning'

function ImpactRow({ label, impact, currency, goodWhenLower }: {
  label: string
  impact: Impact
  currency: string
  goodWhenLower?: boolean
}) {
  const better = goodWhenLower ? impact.change < 0 : impact.change > 0
  const tone = impact.change === 0 ? 'text-muted-foreground' : better ? 'text-success' : 'text-destructive'
  const sign = impact.change > 0 ? '+' : ''
  return (
    <tr className="border-b last:border-0">
      <th scope="row" className="py-2 text-left font-normal text-muted-foreground">{label}</th>
      <td className="py-2 text-right">{formatCurrencyRounded(impact.before, currency)}</td>
      <td className="py-2 text-right font-medium">{formatCurrencyRounded(impact.after, currency)}</td>
      <td className={`py-2 text-right ${tone}`}>
        {sign}{formatCurrencyRounded(impact.change, currency)}
        {impact.changePercentage != null && ` (${sign}${formatPercent(impact.changePercentage)})`}
      </td>
    </tr>
  )
}

/** Before/after table for one simulation, plus how it moves each goal. */
export function SimulationResult({ simulation, currency }: { simulation: Simulation; currency: string }) {
  const { results } = simulation
  return (
    <div className="space-y-4">
      <ul className="flex flex-wrap gap-2 text-xs" aria-label="Changes in this scenario">
        {simulation.scenarios.map((scenario, index) => (
          <li key={index} className="rounded-full bg-muted px-2 py-1">
            {scenarioLabel(scenario.type)} {formatCurrencyRounded(scenario.amount, currency)}
            {scenario.period === 'one_time' ? ' once' : ' / month'}
          </li>
        ))}
      </ul>
      <div className="overflow-x-auto">
        <table className="w-full text-sm" aria-label={`Impact of ${simulation.simulationName}`}>
          <thead>
            <tr className="border-b text-xs text-muted-foreground">
              <th scope="col" className="py-2 text-left font-medium"><span className="sr-only">Measure</span></th>
              <th scope="col" className="py-2 text-right font-medium">Now</th>
              <th scope="col" className="py-2 text-right font-medium">With changes</th>
              <th scope="col" className="py-2 text-right font-medium">Difference</th>
            </tr>
          </thead>
          <tbody>
            <ImpactRow label="Monthly spending" impact={results.monthlyImpact} currency={currency} goodWhenLower />
            <ImpactRow label="Monthly savings" impact={results.monthlySavings} currency={currency} />
            <ImpactRow label="Yearly spending" impact={results.annualImpact} currency={currency} goodWhenLower />
            <ImpactRow label="Yearly savings" impact={results.annualSavings} currency={currency} />
          </tbody>
        </table>
      </div>
      {results.goalImpact.length > 0 && (
        <ul className="space-y-1 text-sm" aria-label="Goal impact">
          {results.goalImpact.map((goal) => (
            <li key={goal.goalId}>
              <span className="font-medium">{goal.goalName}:</span>{' '}
              {goal.currentMonthsToCompletion ?? '—'} → {goal.projectedMonthsWithSimulation ?? 'never'} months
            </li>
          ))}
        </ul>
      )}
      {results.notes.map((note) => <p key={note} className="text-sm text-warning">{note}</p>)}
      <p className="text-xs text-muted-foreground">
        Based on your average over the last {results.baselineMonths} complete month{results.baselineMonths === 1 ? '' : 's'}.
      </p>
    </div>
  )
}
