import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MerchantsPage from './MerchantsPage'
import { merchantService } from '@/services/merchantService'
import { categoryService } from '@/services/accountService'
import { renderWithProviders } from '@/test/utils'
import { categories, page } from '@/test/fixtures'
import type { Merchant, MerchantMapping } from '@/types/merchants'

const chaayos: Merchant = {
  id: 'm-1', merchantName: 'Chaayos Koramangala', isVerified: false, userCategory: false, transactionCount: 4,
  lastTransaction: '2026-09-10',
}

const mapping: MerchantMapping = {
  id: 'map-1', rawMerchantName: 'Swigy Instamart', normalizedMerchantId: 'm-swiggy', normalizedMerchantName: 'Swiggy',
  categoryId: 'cat-food', categoryName: 'Food', usageCount: 3, createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-02T00:00:00Z',
}

describe('MerchantsPage', () => {
  beforeEach(() => {
    vi.spyOn(merchantService, 'list').mockResolvedValue(page([chaayos]))
    vi.spyOn(merchantService, 'suggestions').mockResolvedValue([{
      merchantId: 'm-2', merchantName: 'Swigy Instamart', transactionCount: 3, suggestedMerchantId: 'm-swiggy',
      suggestedMerchantName: 'Swiggy', suggestedCategoryId: 'cat-food', suggestedCategoryName: 'Food', similarity: 0.83,
    }])
    vi.spyOn(merchantService, 'mappings').mockResolvedValue(page([mapping]))
    vi.spyOn(categoryService, 'list').mockResolvedValue(categories)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('accepts a suggested merchant match', async () => {
    const create = vi.spyOn(merchantService, 'createMapping').mockResolvedValue(mapping)
    renderWithProviders(<MerchantsPage />)

    const suggestions = await screen.findByRole('list', { name: 'Merchant suggestions' })
    expect(within(suggestions).getByText('Swiggy')).toBeInTheDocument()
    await userEvent.click(within(suggestions).getByRole('button', { name: 'Map Swigy Instamart to Swiggy' }))

    expect(create).toHaveBeenCalledWith({ rawMerchantName: 'Swigy Instamart', normalizedMerchantId: 'm-swiggy', categoryId: 'cat-food' })
  })

  it('dismisses a suggestion without saving anything', async () => {
    const create = vi.spyOn(merchantService, 'createMapping')
    renderWithProviders(<MerchantsPage />)

    await userEvent.click(await screen.findByRole('button', { name: 'Dismiss Swigy Instamart' }))
    expect(screen.queryByRole('list', { name: 'Merchant suggestions' })).not.toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('changes a merchant category and searches', async () => {
    const update = vi.spyOn(merchantService, 'updateCategory').mockResolvedValue({ ...chaayos, categoryId: 'cat-food', userCategory: true })
    renderWithProviders(<MerchantsPage />)

    const select = await screen.findByRole('combobox', { name: 'Category for Chaayos Koramangala' })
    await screen.findByRole('option', { name: 'Food' })
    await userEvent.selectOptions(select, 'cat-food')
    expect(update).toHaveBeenCalledWith('m-1', 'cat-food')

    await userEvent.type(screen.getByLabelText('Search merchants'), 'cha')
    expect(merchantService.list).toHaveBeenLastCalledWith({ page: 1, searchText: 'cha', sortBy: 'transactionCount' })
  })

  it('shows correction history and removes a correction', async () => {
    const remove = vi.spyOn(merchantService, 'deleteMapping').mockResolvedValue()
    renderWithProviders(<MerchantsPage />)

    await userEvent.click(await screen.findByRole('tab', { name: 'Your corrections' }))
    const table = await screen.findByRole('table', { name: 'Your corrections' })
    expect(within(table).getByText('Swigy Instamart')).toBeInTheDocument()
    await userEvent.click(within(table).getByRole('button', { name: 'Remove correction for Swigy Instamart' }))
    expect(remove).toHaveBeenCalledWith('map-1')
  })
})
