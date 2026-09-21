export type InsightType = 'anomaly' | 'money_leak' | 'opportunity' | 'spending_trend'

export interface RelatedTransaction {
  id: string
  amount: number
  merchant?: string
  categoryName?: string
  date: string
}

export interface Insight {
  id: string
  type: InsightType
  title: string
  description: string
  impactValue?: number
  impactPercentage?: number
  categoryId?: string
  categoryName?: string
  merchantId?: string
  merchantName?: string
  actionable: boolean
  suggestedAction?: string
  confidence?: number
  importance: number
  periodStartDate: string
  periodEndDate: string
  createdAt: string
  details?: Record<string, unknown>
  relatedTransactions?: RelatedTransaction[]
}

export interface Anomaly {
  kind: 'category' | 'purchase'
  categoryId?: string
  categoryName: string
  normalRange?: { min: number; max: number }
  currentAmount: number
  percentageAboveNormal?: number
  period: string
  transactionCount: number
  description: string
  relatedTransactions: RelatedTransaction[]
}
