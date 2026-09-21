import type { Account, Category, Transaction } from '@/types/transaction'
import type { Paged } from '@/types/common'

export const account: Account = {
  id: 'acc-1',
  accountName: 'HDFC Savings',
  accountType: 'savings',
  isPrimary: true,
  isActive: true,
  currencyCode: 'INR',
  openingBalance: 0,
  createdAt: '2026-09-01T00:00:00Z',
}

export const categories: Category[] = [
  {
    id: 'cat-food',
    categoryName: 'Food',
    colorHex: '#FF6B6B',
    isSystem: true,
    subcategories: [
      { id: 'sub-delivery', subcategoryName: 'Food Delivery' },
      { id: 'sub-groceries', subcategoryName: 'Groceries' },
    ],
  },
  { id: 'cat-travel', categoryName: 'Travel', colorHex: '#85C1E2', isSystem: true, subcategories: [] },
]

export function transaction(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 'tx-1',
    accountId: 'acc-1',
    merchantId: 'm-1',
    merchantName: 'Zomato',
    categoryId: 'cat-food',
    categoryName: 'Food',
    categoryColor: '#FF6B6B',
    amount: 450,
    currencyCode: 'INR',
    transactionType: 'debit',
    transactionDate: '2026-09-05',
    paymentMethod: 'upi',
    isRecurring: false,
    isTransfer: false,
    isDuplicate: false,
    categorizationSource: 'merchant_mapping',
    categorizationConfidence: 0.95,
    createdAt: '2026-09-05T10:00:00Z',
    updatedAt: '2026-09-05T10:00:00Z',
    ...overrides,
  }
}

export function page<T>(items: T[], overrides: Partial<Paged<T>['pagination']> = {}): Paged<T> {
  return {
    items,
    pagination: {
      totalItems: items.length,
      totalPages: 1,
      currentPage: 1,
      pageSize: 25,
      hasNext: false,
      hasPrevious: false,
      ...overrides,
    },
  }
}
