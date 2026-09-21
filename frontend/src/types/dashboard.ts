import type { TransactionType } from './transaction'

export interface HealthFactor {
  key: string
  label: string
  weight: number
  points: number
  metric?: number
  scored: boolean
  explanation: string
}

export interface Dashboard {
  period: { startDate: string; endDate: string; month: string; monthNumber: number; year: number; isCurrentMonth: boolean }
  summary: {
    totalIncome: number
    totalExpense: number
    netSavings: number
    savingsRate: number
    currencyCode: string
    transactionCount: number
    expenseChangePercentage?: number
    previousMonthExpense: number
  }
  financialHealth: {
    score?: number
    factors: Record<string, number>
    breakdown: HealthFactor[]
    changes: { factor: string; change: number; reason: string }[]
    summary: string
  }
  spending: {
    byCategory: {
      categoryId?: string
      categoryName: string
      colorHex?: string
      amount: number
      percentage: number
      count: number
      trend: 'up' | 'down' | 'stable'
      previousAmount?: number
    }[]
    topMerchants: { merchantId?: string; merchantName: string; amount: number; count: number }[]
  }
  trend: { month: string; monthNumber: number; year: number; income: number; expense: number }[]
  recentTransactions: {
    id: string
    merchantName?: string
    categoryName?: string
    categoryColor?: string
    amount: number
    transactionDate: string
    transactionType: TransactionType
  }[]
  insights: { id: string; type: string; title: string; description: string; impactValue?: number; actionable: boolean }[]
  recurringPayments: {
    id: string
    merchantName: string
    typicalAmount: number
    frequency: string
    nextExpectedDate?: string
    isUserConfirmed: boolean
  }[]
  budgets: {
    id: string
    budgetName: string
    totalAmount: number
    spentAmount: number
    percentage: number
    remainingAmount: number
    isExceeded: boolean
    isAlert: boolean
    alertThreshold: number
  }[]
}
