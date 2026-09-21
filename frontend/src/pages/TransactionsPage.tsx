import { useEffect, useMemo, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Download, Plus, Upload } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { TransactionFilters } from '@/components/transactions/TransactionFilters'
import { TransactionList } from '@/components/transactions/TransactionList'
import { BulkActions } from '@/components/transactions/BulkActions'
import { TransactionDetail } from '@/components/transactions/TransactionDetail'
import { TransactionForm } from '@/components/transactions/TransactionForm'
import { useTransactionFilters } from '@/hooks/useTransactionFilters'
import { transactionService, type TransactionQuery } from '@/services/transactionService'
import { errorMessage } from '@/services/api'
import type { Transaction } from '@/types/transaction'

export default function TransactionsPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { transactionId } = useParams()
  const { query, update, clear, activeCount } = useTransactionFilters()
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const list = useQuery({
    queryKey: ['transactions', query],
    queryFn: () => transactionService.list(query),
    placeholderData: keepPreviousData,
  })

  // Deep links (/transactions/:id) open the detail drawer, loading the row if it is not on this page.
  const detail = useQuery({
    queryKey: ['transaction', transactionId],
    queryFn: () => transactionService.get(transactionId!),
    enabled: Boolean(transactionId),
    initialData: () => list.data?.items.find((t) => t.id === transactionId),
  })

  useEffect(() => setSelected(new Set()), [query])

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['transactions'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const bulkUpdate = useMutation({
    mutationFn: (categoryId: string) => transactionService.bulkUpdate([...selected], { categoryId }),
    onSuccess: (result) => {
      setNotice(`Recategorized ${result.updated} transaction${result.updated === 1 ? '' : 's'}.`)
      setSelected(new Set())
      invalidate()
    },
    onError: (error) => setNotice(errorMessage(error)),
  })
  const bulkDelete = useMutation({
    mutationFn: () => transactionService.bulkDelete([...selected]),
    onSuccess: (result) => {
      setNotice(`Deleted ${result.deleted} transaction${result.deleted === 1 ? '' : 's'}.`)
      setSelected(new Set())
      invalidate()
    },
    onError: (error) => setNotice(errorMessage(error)),
  })
  const create = useMutation({
    mutationFn: transactionService.create,
    onSuccess: () => {
      setCreating(false)
      setNotice('Transaction added.')
      invalidate()
    },
  })
  const exportCsv = useMutation({
    mutationFn: (ids?: string[]) => transactionService.exportCsv(query, ids),
    onError: (error) => setNotice(errorMessage(error)),
  })

  const items = useMemo(() => list.data?.items ?? [], [list.data])
  const pagination = list.data?.pagination

  function toggle(id: string) {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function toggleAll() {
    setSelected((current) => (items.every((t) => current.has(t.id)) ? new Set() : new Set(items.map((t) => t.id))))
  }

  function sort(field: NonNullable<TransactionQuery['sortBy']>) {
    const order = query.sortBy === field && query.sortOrder === 'desc' ? 'asc' : 'desc'
    update({ sortBy: field, sortOrder: order })
  }

  const openDetail = (transaction: Transaction) => navigate(`/transactions/${transaction.id}${window.location.search}`)
  const closeDetail = () => navigate(`/transactions${window.location.search}`)

  return (
    <>
      <PageHeader
        title="Transactions"
        description={pagination ? `${pagination.totalItems.toLocaleString()} transactions` : 'All your income and spending'}
        actions={
          <>
            <Button variant="outline" onClick={() => exportCsv.mutate(undefined)} disabled={exportCsv.isPending || items.length === 0}>
              <Download className="h-4 w-4" aria-hidden="true" /> Export CSV
            </Button>
            <Link to="/import">
              <Button variant="outline"><Upload className="h-4 w-4" aria-hidden="true" /> Import</Button>
            </Link>
            <Button onClick={() => setCreating(true)}>
              <Plus className="h-4 w-4" aria-hidden="true" /> Add transaction
            </Button>
          </>
        }
      />

      <div className="space-y-4">
        <TransactionFilters query={query} onChange={update} onClear={clear} activeCount={activeCount} />

        {notice && (
          <div role="status" className="flex items-center justify-between rounded-md border bg-card px-3 py-2 text-sm">
            {notice}
            <Button variant="link" size="sm" onClick={() => setNotice(null)}>Dismiss</Button>
          </div>
        )}

        {selected.size > 0 && (
          <BulkActions
            count={selected.size}
            busy={bulkUpdate.isPending || bulkDelete.isPending}
            onRecategorize={(categoryId) => bulkUpdate.mutate(categoryId)}
            onDelete={() => bulkDelete.mutate()}
            onExport={() => exportCsv.mutate([...selected])}
            onClearSelection={() => setSelected(new Set())}
          />
        )}

        {list.isLoading && <Spinner label="Loading transactions" />}
        {list.isError && <ErrorState message={errorMessage(list.error)} action={<Button onClick={() => list.refetch()}>Retry</Button>} />}
        {list.data && items.length === 0 && (
          activeCount > 0 ? (
            <EmptyState title="No matching transactions" description="Try widening your filters." action={<Button variant="outline" onClick={clear}>Clear filters</Button>} />
          ) : (
            <EmptyState
              title="No transactions yet"
              description="Import a bank statement or add a transaction manually."
              action={<Link to="/import"><Button>Import a statement</Button></Link>}
            />
          )
        )}
        {items.length > 0 && (
          <TransactionList
            transactions={items}
            selected={selected}
            onToggle={toggle}
            onToggleAll={toggleAll}
            onOpen={openDetail}
            sortBy={query.sortBy ?? 'date'}
            sortOrder={query.sortOrder ?? 'desc'}
            onSort={sort}
          />
        )}

        {pagination && pagination.totalPages > 1 && (
          <nav aria-label="Pagination" className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">
              Page {pagination.currentPage} of {pagination.totalPages}
            </span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={!pagination.hasPrevious} onClick={() => update({ page: pagination.currentPage - 1 })}>
                Previous
              </Button>
              <Button variant="outline" size="sm" disabled={!pagination.hasNext} onClick={() => update({ page: pagination.currentPage + 1 })}>
                Next
              </Button>
            </div>
          </nav>
        )}
      </div>

      <Dialog open={Boolean(transactionId)} onClose={closeDetail} title="Transaction" variant="drawer">
        {detail.isLoading && <Spinner />}
        {detail.isError && <ErrorState message="Transaction not found." />}
        {detail.data && <TransactionDetail key={detail.data.id} transaction={detail.data} onDeleted={closeDetail} />}
      </Dialog>

      <Dialog open={creating} onClose={() => setCreating(false)} title="Add transaction" description="Record a cash payment or anything missing from your statements.">
        <TransactionForm
          submitLabel="Add transaction"
          busy={create.isPending}
          serverError={create.isError ? errorMessage(create.error) : undefined}
          onSubmit={(input) => create.mutate(input)}
          onCancel={() => setCreating(false)}
        />
      </Dialog>
    </>
  )
}
