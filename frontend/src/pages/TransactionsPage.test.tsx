import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TransactionsPage from './TransactionsPage'
import { transactionService } from '@/services/transactionService'
import { accountService, categoryService } from '@/services/accountService'
import { renderWithProviders } from '@/test/utils'
import { account, categories, page, transaction } from '@/test/fixtures'

const rows = [
  transaction(),
  transaction({ id: 'tx-2', merchantName: 'Acme Corp', amount: 75000, transactionType: 'credit', categoryName: 'Income', transactionDate: '2026-09-01' }),
]

function renderPage(route = '/transactions') {
  return renderWithProviders(
    <Routes>
      <Route path="/transactions" element={<TransactionsPage />} />
      <Route path="/transactions/:transactionId" element={<TransactionsPage />} />
    </Routes>,
    { route },
  )
}

describe('TransactionsPage', () => {
  beforeEach(() => {
    vi.spyOn(transactionService, 'list').mockResolvedValue(page(rows))
    vi.spyOn(accountService, 'list').mockResolvedValue([account])
    vi.spyOn(categoryService, 'list').mockResolvedValue(categories)
    vi.spyOn(transactionService, 'suggestions').mockResolvedValue({ merchants: [], categories: [], searchTerms: [] })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('lists transactions with signed amounts', async () => {
    renderPage()
    const table = await screen.findByRole('table', { name: 'Transactions' })
    expect(within(table).getByText('Zomato')).toBeInTheDocument()
    expect(within(table).getByText('−₹450.00')).toBeInTheDocument()
    expect(within(table).getByText('+₹75,000.00')).toBeInTheDocument()
    expect(screen.getByText('2 transactions')).toBeInTheDocument()
  })

  it('reads filters from the URL and changes sort order from column headers', async () => {
    const list = vi.mocked(transactionService.list)
    renderPage('/transactions?categoryId=cat-food')
    await screen.findByRole('table', { name: 'Transactions' })
    expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 'cat-food', sortBy: 'date' }))

    await userEvent.click(screen.getByRole('button', { name: /Amount/ }))
    await waitFor(() =>
      expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sortBy: 'amount', sortOrder: 'desc', categoryId: 'cat-food' })),
    )
  })

  it('recategorizes the selected rows in bulk', async () => {
    const bulkUpdate = vi.spyOn(transactionService, 'bulkUpdate').mockResolvedValue({ updated: 2, failed: 0 })
    renderPage()
    await screen.findByRole('table', { name: 'Transactions' })

    await userEvent.click(screen.getAllByRole('checkbox', { name: 'Select all on this page' })[0])
    const toolbar = screen.getByRole('toolbar', { name: 'Bulk actions' })
    expect(within(toolbar).getByText('2 selected')).toBeInTheDocument()

    await userEvent.selectOptions(within(toolbar).getByLabelText('New category for selected'), 'cat-travel')
    await userEvent.click(within(toolbar).getByRole('button', { name: /Apply/ }))

    expect(bulkUpdate).toHaveBeenCalledWith(['tx-1', 'tx-2'], { categoryId: 'cat-travel' })
    expect(await screen.findByText('Recategorized 2 transactions.')).toBeInTheDocument()
  })

  it('requires confirmation before bulk delete', async () => {
    const bulkDelete = vi.spyOn(transactionService, 'bulkDelete').mockResolvedValue({ deleted: 1, failed: 0 })
    renderPage()
    await screen.findByRole('table', { name: 'Transactions' })

    await userEvent.click(screen.getAllByRole('checkbox', { name: /Select Zomato/ })[0])
    await userEvent.click(screen.getByRole('button', { name: /Delete/ }))
    expect(bulkDelete).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Yes, delete' }))
    expect(bulkDelete).toHaveBeenCalledWith(['tx-1'])
  })

  it('opens the detail drawer from a deep link and edits only changed fields', async () => {
    vi.spyOn(transactionService, 'get').mockResolvedValue(rows[0])
    const update = vi.spyOn(transactionService, 'update').mockResolvedValue({ ...rows[0], categoryId: 'cat-travel', categoryName: 'Travel' })
    renderPage('/transactions/tx-1')

    const dialog = await screen.findByRole('dialog', { name: 'Transaction' })
    expect(await within(dialog).findByText('From merchant')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: /Edit/ }))
    await userEvent.selectOptions(within(dialog).getByLabelText('Category'), 'cat-travel')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(update).toHaveBeenCalledWith('tx-1', { categoryId: 'cat-travel', subcategoryId: undefined })
  })

  it('validates the manual transaction form', async () => {
    const create = vi.spyOn(transactionService, 'create')
    renderPage()
    await screen.findByRole('table', { name: 'Transactions' })

    await userEvent.click(screen.getByRole('button', { name: /Add transaction/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Add transaction' })
    await userEvent.type(within(dialog).getByLabelText('Amount'), '-5')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add transaction' }))

    expect(within(dialog).getByText('Enter the merchant or payee')).toBeInTheDocument()
    expect(within(dialog).getByText('Enter a positive amount with at most 2 decimals')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('shows an import prompt when there are no transactions', async () => {
    vi.mocked(transactionService.list).mockResolvedValue(page([]))
    renderPage()
    expect(await screen.findByText('No transactions yet')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import a statement' })).toBeInTheDocument()
  })
})
