import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { currentMonth, monthLabel, shiftMonth, type MonthValue } from '@/utils/months'

/** Previous/next month stepper; the future is not selectable. */
export function MonthSelector({ value, onChange }: { value: MonthValue; onChange: (value: MonthValue) => void }) {
  const now = currentMonth()
  const atCurrent = value.year === now.year && value.month === now.month
  return (
    <div className="inline-flex items-center gap-1 rounded-lg border bg-card p-1" role="group" aria-label="Choose month">
      <Button variant="ghost" size="icon" aria-label="Previous month" onClick={() => onChange(shiftMonth(value, -1))}>
        <ChevronLeft className="h-4 w-4" />
      </Button>
      <span className="min-w-[9rem] text-center text-sm font-medium" aria-live="polite">{monthLabel(value)}</span>
      <Button variant="ghost" size="icon" aria-label="Next month" disabled={atCurrent} onClick={() => onChange(shiftMonth(value, 1))}>
        <ChevronRight className="h-4 w-4" />
      </Button>
    </div>
  )
}
