import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BadgeCheck, Check, Sparkles, Trash2, X } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge, EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { Input, Select } from '@/components/ui/input'
import { Table, TBody, Td, Th, THead, Tr } from '@/components/ui/table'
import { Tabs } from '@/components/ui/tabs'
import { useCategories } from '@/hooks/useReferenceData'
import { merchantService } from '@/services/merchantService'
import { errorMessage } from '@/services/api'
import { formatDate } from '@/utils/formatters'
import type { MerchantSuggestion } from '@/types/merchants'

type Tab = 'merchants' | 'corrections'

/** Invalidates everything a correction can change: merchants, corrections and derived views. */
function useInvalidateMerchantData() {
  const queryClient = useQueryClient()
  return () => {
    for (const key of ['merchants', 'merchant-suggestions', 'merchant-mappings', 'transactions', 'dashboard', 'analytics', 'insights']) {
      queryClient.invalidateQueries({ queryKey: [key] })
    }
  }
}

function Suggestions() {
  const invalidate = useInvalidateMerchantData()
  const [dismissed, setDismissed] = useState<string[]>([])
  const suggestions = useQuery({ queryKey: ['merchant-suggestions'], queryFn: merchantService.suggestions })
  const accept = useMutation({
    mutationFn: (s: MerchantSuggestion) => merchantService.createMapping({
      rawMerchantName: s.merchantName, normalizedMerchantId: s.suggestedMerchantId, categoryId: s.suggestedCategoryId,
    }),
    onSuccess: invalidate,
  })
  const visible = suggestions.data?.filter((s) => !dismissed.includes(s.merchantId)) ?? []
  if (visible.length === 0) return null

  return (
    <Card className="mb-6 border-primary/30">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Sparkles className="h-4 w-4 text-primary" aria-hidden="true" /> Possible matches
        </CardTitle>
        <CardDescription>These names from your statements look like merchants SpendOS knows.</CardDescription>
      </CardHeader>
      <CardContent>
        <ul className="divide-y" aria-label="Merchant suggestions">
          {visible.map((s) => (
            <li key={s.merchantId} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
              <span>
                <span className="font-medium">{s.merchantName}</span>
                <span className="text-muted-foreground"> ({s.transactionCount} transactions) looks like </span>
                <span className="font-medium">{s.suggestedMerchantName}</span>
                {s.suggestedCategoryName && <span className="text-muted-foreground"> · {s.suggestedCategoryName}</span>}
              </span>
              <span className="flex gap-1">
                <Button size="sm" disabled={accept.isPending} onClick={() => accept.mutate(s)}
                  aria-label={`Map ${s.merchantName} to ${s.suggestedMerchantName}`}>
                  <Check className="h-4 w-4" aria-hidden="true" /> Yes
                </Button>
                <Button size="sm" variant="ghost" aria-label={`Dismiss ${s.merchantName}`}
                  onClick={() => setDismissed((current) => [...current, s.merchantId])}>
                  <X className="h-4 w-4" />
                </Button>
              </span>
            </li>
          ))}
        </ul>
        {accept.isError && <p role="alert" className="mt-2 text-sm text-destructive">{errorMessage(accept.error)}</p>}
      </CardContent>
    </Card>
  )
}

function MerchantList() {
  const invalidate = useInvalidateMerchantData()
  const { data: categories } = useCategories()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const merchants = useQuery({
    queryKey: ['merchants', page, search],
    queryFn: () => merchantService.list({ page, searchText: search || undefined, sortBy: 'transactionCount' }),
    placeholderData: keepPreviousData,
  })
  const update = useMutation({
    mutationFn: ({ id, categoryId }: { id: string; categoryId: string }) => merchantService.updateCategory(id, categoryId),
    onSuccess: invalidate,
  })

  return (
    <div className="space-y-4">
      <Input aria-label="Search merchants" placeholder="Search merchants" className="max-w-sm" value={search}
        onChange={(e) => { setSearch(e.target.value); setPage(1) }} />
      {update.isError && <p role="alert" className="text-sm text-destructive">{errorMessage(update.error)}</p>}
      {merchants.isLoading && <Spinner />}
      {merchants.isError && <ErrorState message={errorMessage(merchants.error)} />}
      {merchants.data && merchants.data.items.length === 0 && <EmptyState title="No merchants found" />}
      {merchants.data && merchants.data.items.length > 0 && (
        <>
          <Table aria-label="Merchants">
            <THead>
              <Tr>
                <Th>Merchant</Th>
                <Th>Category</Th>
                <Th className="text-right">Your transactions</Th>
                <Th className="hidden sm:table-cell">Last</Th>
              </Tr>
            </THead>
            <TBody>
              {merchants.data.items.map((merchant) => (
                <Tr key={merchant.id}>
                  <Td>
                    <span className="flex items-center gap-1 font-medium">
                      {merchant.merchantName}
                      {merchant.isVerified && <BadgeCheck className="h-4 w-4 text-primary" aria-label="Known merchant" />}
                    </span>
                  </Td>
                  <Td>
                    <div className="flex items-center gap-2">
                      <Select aria-label={`Category for ${merchant.merchantName}`} className="h-8 w-40"
                        value={merchant.categoryId ?? ''} disabled={update.isPending}
                        onChange={(e) => e.target.value && update.mutate({ id: merchant.id, categoryId: e.target.value })}>
                        <option value="">Uncategorized</option>
                        {categories?.map((c) => <option key={c.id} value={c.id}>{c.categoryName}</option>)}
                      </Select>
                      {merchant.userCategory && <Badge tone="primary">Yours</Badge>}
                    </div>
                  </Td>
                  <Td className="text-right tabular-nums">{merchant.transactionCount}</Td>
                  <Td className="hidden sm:table-cell">{formatDate(merchant.lastTransaction)}</Td>
                </Tr>
              ))}
            </TBody>
          </Table>
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">
              Page {merchants.data.pagination.currentPage} of {Math.max(1, merchants.data.pagination.totalPages)}
            </span>
            <span className="flex gap-2">
              <Button variant="outline" size="sm" disabled={!merchants.data.pagination.hasPrevious} onClick={() => setPage((p) => p - 1)}>
                Previous
              </Button>
              <Button variant="outline" size="sm" disabled={!merchants.data.pagination.hasNext} onClick={() => setPage((p) => p + 1)}>
                Next
              </Button>
            </span>
          </div>
          <p className="text-xs text-muted-foreground">
            Changing a category updates your past transactions at that merchant (except ones you categorised by hand) and
            every future one. It only affects your account.
          </p>
        </>
      )}
    </div>
  )
}

function CorrectionHistory() {
  const invalidate = useInvalidateMerchantData()
  const mappings = useQuery({ queryKey: ['merchant-mappings'], queryFn: () => merchantService.mappings() })
  const remove = useMutation({ mutationFn: (id: string) => merchantService.deleteMapping(id), onSuccess: invalidate })

  if (mappings.isLoading) return <Spinner />
  if (mappings.isError) return <ErrorState message={errorMessage(mappings.error)} />
  if (!mappings.data || mappings.data.items.length === 0) {
    return <EmptyState title="No corrections yet" description="Change a merchant's category or accept a suggestion to create one." />
  }
  return (
    <div className="space-y-2">
      <Table aria-label="Your corrections">
        <THead>
          <Tr>
            <Th>Statement name</Th>
            <Th>Becomes</Th>
            <Th className="text-right">Transactions</Th>
            <Th className="hidden sm:table-cell">Updated</Th>
            <Th><span className="sr-only">Actions</span></Th>
          </Tr>
        </THead>
        <TBody>
          {mappings.data.items.map((mapping) => (
            <Tr key={mapping.id}>
              <Td className="font-medium">{mapping.rawMerchantName}</Td>
              <Td>
                {mapping.normalizedMerchantName}
                {mapping.categoryName && <span className="text-muted-foreground"> · {mapping.categoryName}</span>}
              </Td>
              <Td className="text-right tabular-nums">{mapping.usageCount}</Td>
              <Td className="hidden sm:table-cell">{formatDate(mapping.updatedAt)}</Td>
              <Td className="text-right">
                <Button variant="ghost" size="icon" aria-label={`Remove correction for ${mapping.rawMerchantName}`}
                  disabled={remove.isPending} onClick={() => remove.mutate(mapping.id)}>
                  <Trash2 className="h-4 w-4" />
                </Button>
              </Td>
            </Tr>
          ))}
        </TBody>
      </Table>
      <p className="text-xs text-muted-foreground">
        Removing a correction stops it applying to new transactions; transactions it already updated stay as they are.
      </p>
    </div>
  )
}

export default function MerchantsPage() {
  const [tab, setTab] = useState<Tab>('merchants')
  return (
    <>
      <PageHeader title="Merchants" description="Fix how merchants are named and categorised. Corrections apply to your account only." />
      <Suggestions />
      <Tabs label="Merchant views" value={tab} onChange={setTab}
        items={[{ value: 'merchants', label: 'Merchants' }, { value: 'corrections', label: 'Your corrections' }]}>
        {tab === 'merchants' ? <MerchantList /> : <CorrectionHistory />}
      </Tabs>
    </>
  )
}
