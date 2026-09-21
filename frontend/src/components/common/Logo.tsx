import { cn } from '@/utils/cn'

export function Logo({ inverted = false, className }: { inverted?: boolean; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-2 text-lg font-semibold tracking-tight', className)}>
      <span
        aria-hidden="true"
        className={cn(
          'grid h-8 w-8 place-items-center rounded-lg text-sm font-bold',
          inverted ? 'bg-white text-indigo-700' : 'bg-primary text-primary-foreground',
        )}
      >
        S
      </span>
      SpendOS
    </span>
  )
}
