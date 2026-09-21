import { AlertTriangle, Droplets, Lightbulb, TrendingUp } from 'lucide-react'
import type { InsightType } from '@/types/insights'

/** Label, icon and colour for each insight type. */
export const insightMeta: Record<InsightType, { label: string; icon: typeof Lightbulb; tone: string; badge: 'danger' | 'warning' | 'primary' | 'neutral' }> = {
  anomaly: { label: 'Unusual spending', icon: AlertTriangle, tone: 'text-destructive bg-destructive/10', badge: 'danger' },
  money_leak: { label: 'Money leak', icon: Droplets, tone: 'text-warning bg-warning/10', badge: 'warning' },
  opportunity: { label: 'Opportunity', icon: Lightbulb, tone: 'text-primary bg-accent', badge: 'primary' },
  spending_trend: { label: 'Trend', icon: TrendingUp, tone: 'text-muted-foreground bg-muted', badge: 'neutral' },
}
