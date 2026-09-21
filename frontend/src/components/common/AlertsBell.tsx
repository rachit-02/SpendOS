import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Bell } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { budgetService } from '@/services/budgetService'

/** Header bell listing budget alerts (at threshold or exceeded), refreshed every few minutes. */
export function AlertsBell() {
  const [open, setOpen] = useState(false)
  const wrapper = useRef<HTMLDivElement>(null)
  const { data: alerts } = useQuery({
    queryKey: ['budget-alerts'],
    queryFn: budgetService.alerts,
    refetchInterval: 5 * 60_000,
  })

  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (!wrapper.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const count = alerts?.length ?? 0
  return (
    <div ref={wrapper} className="relative">
      <Button variant="ghost" size="icon" aria-label={count ? `${count} budget alerts` : 'No budget alerts'}
        aria-expanded={open} aria-haspopup="true" onClick={() => setOpen((v) => !v)}>
        <Bell className="h-4 w-4" />
        {count > 0 && (
          <span aria-hidden="true" className="absolute right-1 top-1 grid h-4 min-w-[1rem] place-items-center rounded-full bg-destructive px-1 text-[10px] font-bold text-destructive-foreground">
            {count}
          </span>
        )}
      </Button>
      {open && (
        <div className="absolute right-0 z-40 mt-2 w-80 rounded-lg border bg-card p-3 shadow-lg" role="region" aria-label="Budget alerts">
          {count === 0 ? (
            <p className="text-sm text-muted-foreground">You are within all your budgets.</p>
          ) : (
            <ul className="space-y-2 text-sm">
              {alerts!.map((alert) => (
                <li key={`${alert.budgetId}-${alert.categoryId ?? 'all'}`} className={alert.level === 'exceeded' ? 'text-destructive' : ''}>
                  {alert.message}
                </li>
              ))}
            </ul>
          )}
          <Link to="/budgets" className="mt-3 block text-sm font-medium text-primary hover:underline" onClick={() => setOpen(false)}>
            Manage budgets
          </Link>
        </div>
      )}
    </div>
  )
}
