import type { HealthFactor } from './dashboard'

export interface HealthRecommendation {
  factor: string
  title: string
  action: string
  potentialPoints: number
}

export interface HealthMetrics {
  enabled: boolean
  period: string
  score?: number
  summary: string
  factors: HealthFactor[]
  metrics?: {
    savingsRate?: number
    averageMonthlyIncome?: number
    averageMonthlyExpense?: number
    spendingVolatility?: number
    recurringBurden?: number
    emergencyBufferMonths?: number
  }
  recommendations: HealthRecommendation[]
  calculatedAt?: string
}

export interface HealthHistoryPoint {
  period: string
  year: number
  month: number
  score?: number
  factors: Record<string, number>
}

export interface HealthFactorChange {
  factor: string
  label: string
  previousPoints?: number
  currentPoints?: number
  change: number
  reason: string
}

export interface HealthExplanation {
  enabled: boolean
  period: string
  previousPeriod: string
  score?: number
  previousScore?: number
  change?: number
  summary: string
  changes: HealthFactorChange[]
  recommendations: HealthRecommendation[]
}
