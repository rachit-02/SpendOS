import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { cn } from '@/utils/cn'

export interface TabItem<T extends string> {
  value: T
  label: string
}

/** WAI-ARIA tabs: arrow keys move between tabs, only the active tab is in the tab order. */
export function Tabs<T extends string>({ items, value, onChange, children, label }: {
  items: TabItem<T>[]
  value: T
  onChange: (value: T) => void
  children: ReactNode
  label: string
}) {
  const baseId = useId()
  const refs = useRef<(HTMLButtonElement | null)[]>([])

  function onKeyDown(event: KeyboardEvent, index: number) {
    const delta = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
    if (!delta) return
    event.preventDefault()
    const next = (index + delta + items.length) % items.length
    onChange(items[next].value)
    refs.current[next]?.focus()
  }

  return (
    <div className="space-y-4">
      <div role="tablist" aria-label={label} className="flex gap-1 overflow-x-auto border-b">
        {items.map((item, index) => {
          const selected = item.value === value
          return (
            <button
              key={item.value}
              ref={(el) => (refs.current[index] = el)}
              role="tab"
              type="button"
              id={`${baseId}-tab-${item.value}`}
              aria-selected={selected}
              aria-controls={`${baseId}-panel`}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(item.value)}
              onKeyDown={(event) => onKeyDown(event, index)}
              className={cn(
                '-mb-px whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium transition-colors',
                selected ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
              )}
            >
              {item.label}
            </button>
          )
        })}
      </div>
      <div role="tabpanel" id={`${baseId}-panel`} aria-labelledby={`${baseId}-tab-${value}`}>
        {children}
      </div>
    </div>
  )
}
