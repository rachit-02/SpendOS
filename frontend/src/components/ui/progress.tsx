import { cn } from '@/utils/cn'

/** Accessible progress bar that turns amber at the alert threshold and red when exceeded. */
export function ProgressBar({ value, alertAt = 90, label, className }: {
  value: number
  alertAt?: number
  label: string
  className?: string
}) {
  const tone = value > 100 ? 'bg-destructive' : value >= alertAt ? 'bg-warning' : 'bg-success'
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(Math.min(value, 100))}
      aria-valuetext={`${value.toFixed(1)}%`}
      className={cn('h-2 w-full overflow-hidden rounded-full bg-muted', className)}
    >
      <div className={cn('h-full rounded-full transition-all', tone)} style={{ width: `${Math.min(value, 100)}%` }} />
    </div>
  )
}
