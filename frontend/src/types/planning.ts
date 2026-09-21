export interface SpendingPrediction {
  currentDate: string
  month: number
  year: number
  currentSpending: number
  daysElapsed: number
  remainingDaysInMonth: number
  projectedMonthEnd: { min: number; max: number; median: number }
  confidence: number
  confidenceReason: string
  sufficientData: boolean
  methodology: string
  upcomingRecurring: number
  dailyAverage: number
  previousMonthTotal: number
  monthlyAverage: number
  currencyCode: string
}

export interface AffordabilityResult {
  purchase: { amount: number; description?: string }
  affordability: { isAffordable: boolean; confidence: string }
  analysis: {
    currentMonthSpending: number
    expectedIncome: number
    upcomingRecurring: number
    expectedRemainingExpenses: number
    availableAfterPurchase: number
    projectedSavingsWithoutPurchase: number
    projectedSavingsWithPurchase: number
    savingsImpact: number
    monthlyGoalContributions: number
    remainingBudget?: number
  }
  considerations: string[]
  explanation: string
  disclaimer: string
}

export type GoalType = 'savings' | 'debt_payoff' | 'expense_reduction'

export interface Goal {
  id: string
  goalName: string
  goalDescription?: string
  goalType: GoalType
  targetAmount: number
  currentProgress: number
  remainingAmount: number
  progressPercentage: number
  targetDate: string
  isActive: boolean
  monthsToTarget?: number
  monthlyContributionNeeded: number
  projectedCompletionDate?: string
  onTrack: boolean
  currencyCode: string
  createdAt: string
}

export interface GoalInput {
  goalName: string
  goalDescription?: string
  goalType: GoalType
  targetAmount: number
  currentProgress?: number
  targetDate: string
  isActive?: boolean
}

export type ScenarioType = 'spend_reduction' | 'spend_increase' | 'income_change' | 'savings_increase' | 'one_time_purchase'

export interface Scenario {
  type: ScenarioType
  categoryId?: string
  amount: number
  period?: 'monthly' | 'one_time'
  description?: string
}

export interface Impact {
  before: number
  after: number
  change: number
  changePercentage?: number
}

export interface Simulation {
  id: string
  simulationName: string
  scenarios: Scenario[]
  results: {
    monthlyImpact: Impact
    annualImpact: Impact
    monthlySavings: Impact
    annualSavings: Impact
    goalImpact: { goalId: string; goalName: string; currentMonthsToCompletion?: number; projectedMonthsWithSimulation?: number }[]
    notes: string[]
    baselineMonths: number
  }
  createdAt: string
}

export interface SimulationComparison {
  simulations: Simulation[]
  bestForSavingsId?: string
}
