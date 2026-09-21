import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import { Button } from './button'
import { cn } from '@/utils/cn'

interface DialogProps {
  open: boolean
  onClose: () => void
  title: string
  description?: string
  children: ReactNode
  /** "drawer" slides in from the right on large screens; both are full-width sheets on mobile. */
  variant?: 'modal' | 'drawer'
}

/** Accessible modal: labelled, closes on Escape/backdrop, returns focus to the opener. */
export function Dialog({ open, onClose, title, description, children, variant = 'modal' }: DialogProps) {
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const previouslyFocused = document.activeElement as HTMLElement | null
    panelRef.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
      previouslyFocused?.focus?.()
    }
  }, [open, onClose])

  if (!open) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} aria-hidden="true" />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        aria-describedby={description ? 'dialog-description' : undefined}
        tabIndex={-1}
        className={cn(
          'relative z-10 max-h-[92vh] w-full overflow-y-auto bg-card p-5 shadow-xl outline-none',
          variant === 'drawer'
            ? 'rounded-t-xl sm:rounded-xl lg:fixed lg:inset-y-0 lg:right-0 lg:max-h-none lg:max-w-md lg:rounded-none'
            : 'rounded-t-xl sm:max-w-lg sm:rounded-xl',
        )}
      >
        <div className="mb-4 flex items-start justify-between gap-4">
          <div>
            <h2 id="dialog-title" className="text-lg font-semibold">{title}</h2>
            {description && <p id="dialog-description" className="text-sm text-muted-foreground">{description}</p>}
          </div>
          <Button variant="ghost" size="icon" aria-label="Close" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  )
}
