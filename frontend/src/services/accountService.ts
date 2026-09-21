import { api, unwrap } from './api'
import type { Account, AccountType, Category } from '@/types/transaction'

export interface AccountInput {
  accountName: string
  accountType: AccountType
  accountNumberLast4?: string
  bankName?: string
  isPrimary?: boolean
}

export const accountService = {
  list: () => unwrap<Account[]>(api.get('/accounts')),
  create: (input: AccountInput) => unwrap<Account>(api.post('/accounts', input)),
  update: (id: string, input: Partial<AccountInput> & { isActive?: boolean }) =>
    unwrap<Account>(api.put(`/accounts/${id}`, input)),
}

export const categoryService = {
  list: () => unwrap<Category[]>(api.get('/categories')),
}
