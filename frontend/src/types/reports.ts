export interface CategoryDelta {
  category: string
  previous: number
  current: number
  amount: number
  percentageChange?: number
}

export interface MonthlyAutopsy {
  period: string
  year: number
  month: number
  startDate: string
  endDate: string
  isComplete: boolean
  currencyCode: string
  income: number
  expenses: number
  savings: number
  savingsRate: number
  previousExpenses: number
  expenseChangePercentage?: number
  healthScore?: number
  spendingByCategory: { categoryName: string; colorHex?: string; amount: number; percentage: number }[]
  changes: { largestIncreases: CategoryDelta[]; largestDecreases: CategoryDelta[] }
  largestMerchants: { merchantName: string; amount: number; count: number }[]
  recurringPayments: { merchantName: string; amount: number; frequency: string; monthlyCost: number }[]
  unusualTransactions: { description: string; amount: number; date: string; merchantName?: string; categoryName: string }[]
  budgetPerformance: Record<string, { budget: number; spent: number; percentage: number; exceeded: boolean }>
  mostImportantInsight: string
  suggestedAction: string
  nextMonthWatchlist: { kind: string; message: string; amount?: number }[]
  transactionCount: number
  generatedAt: string
}
