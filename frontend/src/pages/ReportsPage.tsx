import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FileDown, Printer, RefreshCw } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { EmptyState, ErrorState, Spinner } from '@/components/ui/feedback'
import { MonthSelector } from '@/components/dashboard/MonthSelector'
import { MonthlyAutopsy } from '@/components/reports/MonthlyAutopsy'
import { reportService } from '@/services/reportService'
import { errorMessage } from '@/services/api'
import { currentMonth, shiftMonth, type MonthValue } from '@/utils/months'

export default function ReportsPage() {
  const queryClient = useQueryClient()
  // Default to the last completed month: that is when the autopsy is final.
  const [month, setMonth] = useState<MonthValue>(() => shiftMonth(currentMonth(), -1))
  const key = ['autopsy', month.year, month.month]
  const { data, isLoading, isError, error } = useQuery({
    queryKey: key,
    queryFn: () => reportService.autopsy(month),
    placeholderData: keepPreviousData,
  })
  const regenerate = useMutation({
    mutationFn: () => reportService.regenerate(month),
    onSuccess: (report) => queryClient.setQueryData(key, report),
  })
  const pdf = useMutation({ mutationFn: () => reportService.downloadPdf(month) })

  return (
    <>
      <PageHeader
        title="Monthly money autopsy"
        description="What happened to your money this month, and what to watch next."
        actions={
          <div className="flex flex-wrap gap-2 print:hidden">
            <MonthSelector value={month} onChange={setMonth} />
            <Button variant="outline" onClick={() => window.print()}><Printer className="h-4 w-4" aria-hidden="true" /> Print</Button>
            <Button variant="outline" onClick={() => pdf.mutate()} disabled={pdf.isPending}>
              <FileDown className="h-4 w-4" aria-hidden="true" /> PDF
            </Button>
            <Button variant="ghost" onClick={() => regenerate.mutate()} disabled={regenerate.isPending} aria-label="Regenerate report">
              <RefreshCw className={regenerate.isPending ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} />
            </Button>
          </div>
        }
      />
      {pdf.isError && <p role="alert" className="mb-4 text-sm text-destructive">{errorMessage(pdf.error)}</p>}
      {isLoading && <Spinner label="Building your report" />}
      {isError && <ErrorState message={errorMessage(error)} />}
      {data && data.transactionCount === 0 && (
        <EmptyState title={`No transactions in ${data.period}`} description="Import a statement for this month to get its autopsy." />
      )}
      {data && data.transactionCount > 0 && <MonthlyAutopsy report={data} />}
    </>
  )
}
