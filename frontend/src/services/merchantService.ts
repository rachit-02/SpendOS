import { api, cleanParams, toApiError, unwrap, unwrapPage } from './api'
import type { MappingInput, Merchant, MerchantMapping, MerchantSuggestion } from '@/types/merchants'

export interface MerchantQuery {
  page: number
  pageSize?: number
  searchText?: string
  categoryId?: string
  sortBy?: 'name' | 'transactionCount' | 'lastTransaction'
}

export const merchantService = {
  list: (query: MerchantQuery) =>
    unwrapPage<Merchant>(api.get('/merchants', { params: cleanParams({ pageSize: 25, ...query }) })),
  updateCategory: (merchantId: string, categoryId: string) =>
    unwrap<Merchant>(api.put(`/merchants/${merchantId}/category`, { categoryId })),
  suggestions: () => unwrap<MerchantSuggestion[]>(api.get('/merchants/suggestions')),
  mappings: (page = 1) => unwrapPage<MerchantMapping>(api.get('/merchants/mappings', { params: { page, pageSize: 50 } })),
  createMapping: (input: MappingInput) => unwrap<MerchantMapping>(api.post('/merchants/mappings', input)),
  deleteMapping: async (id: string) => {
    try {
      await api.delete(`/merchants/mappings/${id}`)
    } catch (error) {
      throw toApiError(error)
    }
  },
}
