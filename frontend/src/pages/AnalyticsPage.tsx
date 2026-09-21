import { useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/common/PageHeader'
import { Tabs, type TabItem } from '@/components/ui/tabs'
import {
  CategoryTrendPanel,
  ComparePanel,
  MerchantPanel,
  MonthlyPanel,
  YearPanel,
} from '@/components/analytics/AnalyticsPanels'

type Tab = 'monthly' | 'categories' | 'merchants' | 'compare' | 'year'

const tabs: TabItem<Tab>[] = [
  { value: 'monthly', label: 'Monthly' },
  { value: 'categories', label: 'Category trends' },
  { value: 'merchants', label: 'Merchants' },
  { value: 'compare', label: 'Compare months' },
  { value: 'year', label: 'Year view' },
]

export default function AnalyticsPage() {
  const [params, setParams] = useSearchParams()
  const tab = (tabs.find((t) => t.value === params.get('tab'))?.value ?? 'monthly') as Tab

  return (
    <>
      <PageHeader title="Analytics" description="Monthly breakdowns, trends and comparisons of your spending." />
      <Tabs label="Analytics views" items={tabs} value={tab} onChange={(value) => setParams({ tab: value }, { replace: true })}>
        {tab === 'monthly' && <MonthlyPanel />}
        {tab === 'categories' && <CategoryTrendPanel />}
        {tab === 'merchants' && <MerchantPanel />}
        {tab === 'compare' && <ComparePanel />}
        {tab === 'year' && <YearPanel />}
      </Tabs>
    </>
  )
}
