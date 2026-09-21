import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/feedback'
import { cn } from '@/utils/cn'
import type { InsightType } from '@/types/insights'
import { insightMeta } from './insightMeta'


interface Props {
  type: InsightType
  title: string
  description: string
  suggestedAction?: string
  onShowEvidence?: () => void
  compact?: boolean
}

/** One explainable insight: what happened, why it matters and what to do about it. */
export function InsightCard({ type, title, description, suggestedAction, onShowEvidence, compact }: Props) {
  const meta = insightMeta[type]
  const Icon = meta.icon
  return (
    <Card>
      <CardContent className={cn('flex gap-4', compact ? 'p-4' : 'p-5')}>
        <span className={cn('grid h-10 w-10 shrink-0 place-items-center rounded-lg', meta.tone)} aria-hidden="true">
          <Icon className="h-5 w-5" />
        </span>
        <div className="min-w-0 flex-1 space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold">{title}</h3>
            <Badge tone={meta.badge}>{meta.label}</Badge>
          </div>
          <p className="text-sm">{description}</p>
          {suggestedAction && !compact && <p className="text-sm text-muted-foreground">{suggestedAction}</p>}
          {onShowEvidence && (
            <Button variant="link" size="sm" className="h-auto p-0" onClick={onShowEvidence}>
              See the transactions behind this
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
