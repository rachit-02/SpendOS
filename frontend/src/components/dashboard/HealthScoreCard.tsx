import { Link } from 'react-router-dom'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/utils/cn'
import type { Dashboard } from '@/types/dashboard'

function scoreTone(score: number): string {
  if (score >= 80) return 'text-success'
  if (score >= 60) return 'text-primary'
  if (score >= 40) return 'text-warning'
  return 'text-destructive'
}

/** Score ring plus the factor breakdown so users can see exactly why the score is what it is. */
export function HealthScoreCard({ health, compact = false, detailsLink = false }: {
  health: Dashboard['financialHealth']
  compact?: boolean
  /** Shows a link to the full health page (used on the dashboard). */
  detailsLink?: boolean
}) {
  const score = health.score
  const circumference = 2 * Math.PI * 42
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle>Financial health</CardTitle>
          {detailsLink && <Link to="/health" className="text-sm text-primary hover:underline">Details and tips</Link>}
        </div>
        <CardDescription>{health.summary}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center gap-5">
          <div className="relative h-28 w-28 shrink-0">
            <svg viewBox="0 0 100 100" className="h-full w-full -rotate-90" aria-hidden="true">
              <circle cx="50" cy="50" r="42" className="fill-none stroke-muted" strokeWidth="10" />
              {score !== undefined && score !== null && (
                <circle cx="50" cy="50" r="42" className={cn('fill-none stroke-current transition-all', scoreTone(score))}
                  strokeWidth="10" strokeLinecap="round" strokeDasharray={circumference}
                  strokeDashoffset={circumference * (1 - score / 100)} />
              )}
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className={cn('text-3xl font-bold tabular-nums', score !== undefined && score !== null && scoreTone(score))}>
                {score ?? '—'}
              </span>
              <span className="text-xs text-muted-foreground">of 100</span>
            </div>
          </div>
          {health.changes.length > 0 && (
            <ul className="space-y-1 text-sm" aria-label="Changes since last month">
              {health.changes.map((change) => (
                <li key={change.factor} className={change.change > 0 ? 'text-success' : 'text-destructive'}>
                  {change.change > 0 ? '+' : ''}
                  {change.change} · {change.reason}
                </li>
              ))}
            </ul>
          )}
        </div>
        {!compact && health.breakdown.length > 0 && (
          <ul className="space-y-3" aria-label="Score breakdown">
            {health.breakdown.map((factor) => (
              <li key={factor.key} className="space-y-1">
                <div className="flex justify-between text-sm">
                  <span className={cn(!factor.scored && 'text-muted-foreground')}>{factor.label}</span>
                  <span className="tabular-nums text-muted-foreground">
                    {factor.scored ? `${factor.points} / ${factor.weight}` : 'Not scored'}
                  </span>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                  <div className="h-full rounded-full bg-primary" style={{ width: `${factor.scored ? (factor.points / factor.weight) * 100 : 0}%` }} />
                </div>
                <p className="text-xs text-muted-foreground">{factor.explanation}</p>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
