import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { Label, Select } from '@/components/ui/input'
import { Tabs, type TabItem } from '@/components/ui/tabs'
import { InsightCard } from '@/components/insights/InsightCard'
import { RelatedTransactionList } from '@/components/insights/RelatedTransactionList'
import { insightService } from '@/services/insightService'
import { errorMessage } from '@/services/api'
import { useCurrency } from '@/hooks/useReferenceData'
import { formatDate } from '@/utils/formatters'
import type { InsightType } from '@/types/insights'

type Filter = InsightType | 'all'

const filters: TabItem<Filter>[] = [
  { value: 'all', label: 'All' },
  { value: 'money_leak', label: 'Money leaks' },
  { value: 'anomaly', label: 'Unusual spending' },
  { value: 'opportunity', label: 'Opportunities' },
  { value: 'spending_trend', label: 'Trends' },
]

function EvidenceDrawer({ insightId, onClose }: { insightId: string | null; onClose: () => void }) {
  const currency = useCurrency()
  const { data } = useQuery({
    queryKey: ['insight', insightId],
    queryFn: () => insightService.get(insightId!),
    enabled: Boolean(insightId),
  })
  return (
    <Dialog open={Boolean(insightId)} onClose={onClose} title={data?.title ?? 'Insight'} description={data?.description} variant="drawer">
      {!data ? <Spinner /> : (
        <div className="space-y-4">
          {data.suggestedAction && <p className="rounded-md bg-accent p-3 text-sm text-accent-foreground">{data.suggestedAction}</p>}
          <RelatedTransactionList transactions={data.relatedTransactions ?? []} currency={currency} label="Supporting transactions" />
        </div>
      )}
    </Dialog>
  )
}

function UnusualSpending() {
  const currency = useCurrency()
  const [sensitivity, setSensitivity] = useState<'low' | 'medium' | 'high'>('medium')
  const { data, isLoading } = useQuery({
    queryKey: ['anomalies', sensitivity],
    queryFn: () => insightService.anomalies(sensitivity),
  })
  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-end justify-between gap-3">
        <div>
          <CardTitle>Unusual spending this month</CardTitle>
          <CardDescription>Compared with your own last six months. Needs three months of history.</CardDescription>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="sensitivity">Sensitivity</Label>
          <Select id="sensitivity" value={sensitivity} className="w-36"
            onChange={(e) => setSensitivity(e.target.value as 'low' | 'medium' | 'high')}>
            <option value="low">Low (big changes only)</option>
            <option value="medium">Medium</option>
            <option value="high">High (flag more)</option>
          </Select>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading && <Spinner />}
        {data && data.length === 0 && <p className="text-sm text-muted-foreground">Nothing unusual at this sensitivity.</p>}
        {data && data.length > 0 && (
          <ul className="space-y-4" aria-label="Unusual spending">
            {data.map((anomaly, index) => (
              <li key={`${anomaly.kind}-${anomaly.categoryId}-${index}`} className="space-y-2">
                <p className="text-sm">{anomaly.description}</p>
                {anomaly.kind === 'category' && (
                  <RelatedTransactionList transactions={anomaly.relatedTransactions.slice(0, 5)} currency={currency}
                    label={`${anomaly.categoryName} transactions`} />
                )}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

export default function InsightsPage() {
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState<Filter>('all')
  const [period, setPeriod] = useState('current_month')
  const [evidence, setEvidence] = useState<string | null>(null)

  const insights = useQuery({
    queryKey: ['insights', period],
    queryFn: () => insightService.list('all', period),
  })
  const history = useQuery({ queryKey: ['insight-history'], queryFn: () => insightService.history(6) })
  const refresh = useMutation({
    mutationFn: () => insightService.regenerate(period),
    onSuccess: (data) => {
      queryClient.setQueryData(['insights', period], data)
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })

  const visible = (insights.data ?? []).filter((i) => filter === 'all' || i.type === filter)

  return (
    <>
      <PageHeader
        title="Insights"
        description="Patterns in your spending, each explained and backed by your transactions."
        actions={
          <>
            <Select aria-label="Period" value={period} onChange={(e) => setPeriod(e.target.value)} className="w-40">
              <option value="current_month">This month</option>
              <option value="last_month">Last month</option>
            </Select>
            <Button variant="outline" onClick={() => refresh.mutate()} disabled={refresh.isPending}>
              <RefreshCw className={refresh.isPending ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} aria-hidden="true" /> Refresh
            </Button>
          </>
        }
      />
      <div className="grid gap-6">
        <Tabs label="Insight types" items={filters} value={filter} onChange={setFilter}>
          {insights.isLoading && <Spinner label="Analysing your spending" />}
          {insights.isError && <ErrorState message={errorMessage(insights.error)} />}
          {insights.data && visible.length === 0 && (
            <EmptyState title="Nothing to flag"
              description="No meaningful patterns yet. Insights appear once you have a few months of transactions." />
          )}
          {visible.length > 0 && (
            <ul className="grid gap-3" aria-label="Insights">
              {visible.map((insight) => (
                <li key={insight.id}>
                  <InsightCard type={insight.type} title={insight.title} description={insight.description}
                    suggestedAction={insight.suggestedAction} onShowEvidence={() => setEvidence(insight.id)} />
                </li>
              ))}
            </ul>
          )}
        </Tabs>

        <UnusualSpending />

        {history.data && history.data.length > 0 && (
          <Card>
            <CardHeader>
              <CardTitle>Earlier insights</CardTitle>
            </CardHeader>
            <CardContent>
              <ul className="divide-y" aria-label="Insight history">
                {history.data.slice(0, 12).map((insight) => (
                  <li key={insight.id} className="flex items-start justify-between gap-4 py-2.5 text-sm">
                    <button type="button" className="text-left hover:underline" onClick={() => setEvidence(insight.id)}>
                      {insight.title}
                    </button>
                    <span className="shrink-0 text-xs text-muted-foreground">{formatDate(insight.periodStartDate, 'MMM yyyy')}</span>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        )}
      </div>
      <EvidenceDrawer insightId={evidence} onClose={() => setEvidence(null)} />
    </>
  )
}
