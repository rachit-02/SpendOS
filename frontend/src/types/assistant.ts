export interface AssistantFinding {
  category?: string
  merchant?: string
  thisMonth?: number
  previousMonth?: number
  change?: number
  changePercentage?: number
  amount?: number
  percentage?: number
  count?: number
  frequency?: string
}

export interface AssistantTransaction {
  id: string
  merchant?: string
  description?: string
  category: string
  amount: number
  date: string
}

export interface AssistantAnswer {
  question: string
  intent: string
  period: string
  answer: string
  supportingData: { keyFindings: AssistantFinding[]; relatedTransactions: AssistantTransaction[] }
  followUpQuestions: string[]
  /** "rules" when deterministic, "llm" when reworded by a model and verified against backend numbers. */
  answerSource: 'rules' | 'llm'
  currencyCode: string
}

export interface AssistantSuggestion {
  question: string
  intent: string
}
