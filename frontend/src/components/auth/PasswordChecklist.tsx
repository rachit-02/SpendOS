import { Check, X } from 'lucide-react'
import { passwordChecks } from '@/utils/validators'
import { cn } from '@/utils/cn'

export function PasswordChecklist({ password }: { password: string }) {
  return (
    <ul className="grid grid-cols-2 gap-1 text-xs" aria-label="Password requirements">
      {passwordChecks(password).map((check) => (
        <li key={check.label} className={cn('flex items-center gap-1', check.passed ? 'text-success' : 'text-muted-foreground')}>
          {check.passed ? <Check className="h-3 w-3" aria-hidden="true" /> : <X className="h-3 w-3" aria-hidden="true" />}
          <span>
            {check.label}
            <span className="sr-only">{check.passed ? ' (met)' : ' (not met)'}</span>
          </span>
        </li>
      ))}
    </ul>
  )
}
