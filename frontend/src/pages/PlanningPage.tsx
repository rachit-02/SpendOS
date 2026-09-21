import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '@/components/common/PageHeader'
import { ErrorState, Spinner } from '@/components/ui/feedback'
import { Tabs } from '@/components/ui/tabs'
import { SpendingForecast } from '@/components/planning/SpendingForecast'
import { AffordabilityCheck } from '@/components/planning/AffordabilityCheck'
import { WhatIfPanel } from '@/components/planning/WhatIfPanel'
import { planningService } from '@/services/planningService'
import { errorMessage } from '@/services/api'
import { useCurrency } from '@/hooks/useReferenceData'

type Tab = 'forecast' | 'whatif'

export default function PlanningPage() {
  const currency = useCurrency()
  const [tab, setTab] = useState<Tab>('forecast')
  const prediction = useQuery({ queryKey: ['spending-prediction'], queryFn: () => planningService.prediction() })

  return (
    <>
      <PageHeader title="Planning" description="See where this month is heading and test decisions before you make them." />
      <Tabs label="Planning tools" value={tab} onChange={setTab}
        items={[{ value: 'forecast', label: 'Forecast' }, { value: 'whatif', label: 'What-if' }]}>
        {tab === 'forecast' && (
          <div className="grid gap-6 xl:grid-cols-2">
            <div>
              {prediction.isLoading && <Spinner label="Forecasting" />}
              {prediction.isError && <ErrorState message={errorMessage(prediction.error)} />}
              {prediction.data && <SpendingForecast prediction={prediction.data} />}
            </div>
            <AffordabilityCheck currency={prediction.data?.currencyCode ?? currency} />
          </div>
        )}
        {tab === 'whatif' && <WhatIfPanel currency={currency} />}
      </Tabs>
    </>
  )
}
