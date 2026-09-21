export interface PeriodInfo {
  month: string
  monthNumber: number
  year: number
  startDate: string
  endDate: string
}

export interface CategoryChange {
  categoryId?: string
  categoryName: string
  previous: number
  current: number
  change: number
  changePercentage?: number
}

export interface MonthlyAnalytics {
  period: PeriodInfo
  income: { total: number; bySource: { source: string; amount: number; percentage: number }[] }
  expenses: {
    total: number
    byCategory: { categoryId?: string; categoryName: string; colorHex?: string; amount: number; percentage: number; count: number }[]
    byPaymentMethod: { method: string; amount: number; percentage: number; count: number }[]
  }
  savings: number
  savingsRate: number
  previousMonthComparison: {
    previousIncome: number
    previousExpense: number
    incomeChange?: number
    expenseChange?: number
    spendingTrend: 'up' | 'down' | 'stable'
    categoryChanges: CategoryChange[]
  }
  currencyCode: string
}

export interface CategoryTrend {
  categoryId: string
  categoryName: string
  trend: { month: string; monthNumber: number; year: number; amount: number; count: number }[]
  average: number
  percentageChange?: number
  forecast: number
  forecastMethod: string
}

export interface MerchantStat {
  merchantId?: string
  merchantName: string
  amount: number
  count: number
  averageAmount: number
  percentage: number
}

export interface TrendMonth {
  month: string
  monthNumber: number
  year: number
  income: number
  expense: number
  savings: number
  savingsRate: number
  lastYearIncome: number
  lastYearExpense: number
  expenseChangeYearOverYear?: number
}

export interface Trends {
  months: TrendMonth[]
  averageMonthlyIncome: number
  averageMonthlyExpense: number
  highestSpendingMonth?: TrendMonth
  lowestSpendingMonth?: TrendMonth
}

export interface MonthSummary {
  period: PeriodInfo
  income: number
  expense: number
  savings: number
  savingsRate: number
}

export interface MonthComparison {
  first: MonthSummary
  second: MonthSummary
  expenseChange: number
  expenseChangePercentage?: number
  categories: CategoryChange[]
}
