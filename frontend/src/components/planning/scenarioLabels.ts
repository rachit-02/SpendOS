import type { ScenarioType } from '@/types/planning'

export const scenarioTypes: { value: ScenarioType; label: string; hint: string }[] = [
  { value: 'spend_reduction', label: 'Spend less', hint: 'Cut monthly spending, overall or in one category' },
  { value: 'spend_increase', label: 'Spend more', hint: 'A new monthly cost, like a gym membership' },
  { value: 'income_change', label: 'Income change', hint: 'A raise (positive) or a pay cut (negative)' },
  { value: 'savings_increase', label: 'Save more', hint: 'Move a fixed amount into savings every month' },
  { value: 'one_time_purchase', label: 'One-time purchase', hint: 'A single big purchase this year' },
]

export const scenarioLabel = (type: ScenarioType) => scenarioTypes.find((t) => t.value === type)?.label ?? type
