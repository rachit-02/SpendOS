import type { Amount } from './common'

export type TransactionType = 'debit' | 'credit' | 'transfer'
export type PaymentMethod = 'upi' | 'card' | 'net_banking' | 'cash' | 'wallet'
export type AccountType = 'savings' | 'checking' | 'credit' | 'digital_wallet'

export interface Account {
  id: string
  accountName: string
  accountType: AccountType
  accountNumberMasked?: string
  bankName?: string
  isPrimary: boolean
  isActive: boolean
  currencyCode: string
  openingBalance: Amount
  createdAt: string
}

export interface Subcategory {
  id: string
  subcategoryName: string
  displayOrder?: number
}

export interface Category {
  id: string
  categoryName: string
  iconName?: string
  colorHex?: string
  displayOrder?: number
  isSystem: boolean
  subcategories: Subcategory[]
}

export interface Transaction {
  id: string
  accountId: string
  merchantId?: string
  merchantName?: string
  categoryId?: string
  categoryName?: string
  categoryColor?: string
  subcategoryId?: string
  subcategoryName?: string
  amount: Amount
  currencyCode: string
  transactionType: TransactionType
  transactionDate: string
  description?: string
  rawDescription?: string
  paymentMethod?: PaymentMethod
  externalReference?: string
  isRecurring: boolean
  isTransfer: boolean
  categorizationConfidence?: number
  categorizationSource?: string
  isDuplicate: boolean
  createdAt: string
  updatedAt: string
}
