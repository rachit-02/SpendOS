export interface Merchant {
  id: string
  merchantName: string
  categoryId?: string
  categoryName?: string
  logoUrl?: string
  website?: string
  isVerified: boolean
  confidenceScore?: number
  /** True when the category is the user's own correction rather than the default. */
  userCategory: boolean
  transactionCount: number
  averageTransactionAmount?: number
  lastTransaction?: string
}

export interface MerchantMapping {
  id: string
  rawMerchantName: string
  normalizedMerchantId: string
  normalizedMerchantName: string
  categoryId?: string
  categoryName?: string
  usageCount: number
  appliedToTransactions?: number
  createdAt: string
  updatedAt: string
}

export interface MappingInput {
  rawMerchantName: string
  normalizedMerchantId: string
  categoryId?: string
}

export interface MerchantSuggestion {
  merchantId: string
  merchantName: string
  transactionCount: number
  suggestedMerchantId: string
  suggestedMerchantName: string
  suggestedCategoryId?: string
  suggestedCategoryName?: string
  similarity: number
}
