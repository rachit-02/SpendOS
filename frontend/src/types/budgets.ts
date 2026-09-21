export type BudgetType = 'monthly' | 'quarterly' | 'annual' | 'custom'

export interface CategoryProgress {
  categoryId: string
  categoryName: string
  colorHex?: string
  allocatedAmount: number
  spentAmount: number
  remainingAmount: number
  percentage: number
  isExceeded: boolean
  isAlert: boolean
}

export interface Budget {
  id: string
  budgetName: string
  budgetType: BudgetType
  totalAmount: number
  currencyCode: string
  startDate: string
  endDate: string
  alertThreshold: number
  isActive: boolean
  spentAmount: number
  remainingAmount: number
  percentage: number
  isExceeded: boolean
  isAlert: boolean
  status: 'on_track' | 'warning' | 'exceeded'
  categories: CategoryProgress[]
}

export interface BudgetInput {
  budgetName: string
  budgetType: BudgetType
  totalAmount: number
  startDate: string
  endDate?: string
  alertThreshold: number
  categories: { categoryId: string; allocatedAmount: number }[]
}

export interface BudgetProgress {
  budget: Budget
  daysElapsed: number
  daysTotal: number
  daysRemaining: number
  expectedSpendToDate: number
  projectedSpend: number
  projectedToExceed: boolean
  dailyAllowanceRemaining: number
  pace: 'not_started' | 'ahead' | 'on_track' | 'behind'
}

export interface BudgetAlert {
  budgetId: string
  budgetName: string
  categoryId?: string
  categoryName?: string
  limit: number
  spent: number
  percentage: number
  level: 'warning' | 'exceeded'
  message: string
}

export interface RecurringPayment {
  id: string
  merchantId?: string
  merchantName: string
  categoryId?: string
  typicalAmount: number
  currencyCode: string
  frequency: 'daily' | 'weekly' | 'biweekly' | 'monthly' | 'quarterly' | 'annual'
  nextExpectedDate?: string
  lastOccurrenceDate?: string
  occurrencesCount: number
  confidence: number
  isActive: boolean
  isUserConfirmed: boolean
  status: 'pending' | 'confirmed' | 'dismissed' | 'lapsed'
  monthlyCost: number
}
