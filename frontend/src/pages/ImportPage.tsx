import { useCallback, useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ShieldCheck } from 'lucide-react'
import { PageHeader } from '@/components/common/PageHeader'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Label, Select } from '@/components/ui/input'
import { CSVUpload } from '@/components/imports/CSVUpload'
import { ImportPreview } from '@/components/imports/ImportPreview'
import { isPdfFile } from '@/components/imports/csvValidation'
import { ImportProgress } from '@/components/imports/ImportProgress'
import { ImportSummary } from '@/components/imports/ImportSummary'
import { ImportHistory } from '@/components/imports/ImportHistory'
import { AccountForm } from '@/components/accounts/AccountForm'
import { useAccounts } from '@/hooks/useReferenceData'
import { importService } from '@/services/importService'
import { toApiError } from '@/services/api'

type Stage = { kind: 'select' } | { kind: 'processing'; jobId: string } | { kind: 'done'; jobId: string }

const dateFormats = [
  { value: '', label: 'Detect automatically' },
  { value: 'DD-MM-YYYY', label: 'Day first (31-12-2026)' },
  { value: 'MM/DD/YYYY', label: 'Month first (12/31/2026)' },
  { value: 'YYYY-MM-DD', label: 'Year first (2026-12-31)' },
]

export default function ImportPage() {
  const queryClient = useQueryClient()
  const { data: accounts } = useAccounts()
  const [accountId, setAccountId] = useState('')
  const [addingAccount, setAddingAccount] = useState(false)
  const [dateFormat, setDateFormat] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [stage, setStage] = useState<Stage>({ kind: 'select' })
  const [error, setError] = useState<string | null>(null)
  const [historyJob, setHistoryJob] = useState<string | null>(null)

  useEffect(() => {
    if (!accountId && accounts && accounts.length > 0) {
      setAccountId((accounts.find((a) => a.isPrimary) ?? accounts[0]).id)
    }
  }, [accounts, accountId])

  const upload = useMutation({
    mutationFn: () => importService.upload(file!, accountId || undefined, dateFormat || undefined),
    onSuccess: (response) => setStage({ kind: 'processing', jobId: response.importJobId }),
    onError: (err) => {
      const apiError = toApiError(err)
      setError(
        apiError.code === 'DUPLICATE_IMPORT'
          ? 'This exact file has already been imported. Its transactions are already in SpendOS.'
          : apiError.message,
      )
    },
  })

  const handleFinished = useCallback(
    (status: { importJobId: string }) => {
      setStage({ kind: 'done', jobId: status.importJobId })
      queryClient.invalidateQueries({ queryKey: ['import-history'] })
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      queryClient.invalidateQueries({ queryKey: ['accounts'] })
    },
    [queryClient],
  )

  function reset() {
    setFile(null)
    setError(null)
    setStage({ kind: 'select' })
  }

  return (
    <>
      <PageHeader title="Import transactions" description="Upload a CSV or PDF statement from your bank, card or wallet." />
      <div className="grid gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Upload a statement</CardTitle>
            <CardDescription className="flex items-center gap-1.5">
              <ShieldCheck className="h-4 w-4 text-success" aria-hidden="true" />
              Files are processed on our server and never shared. We never need your banking password.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {stage.kind === 'select' && (
              <>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-1.5">
                    <Label htmlFor="import-account">Import into account</Label>
                    <div className="flex gap-2">
                      <Select id="import-account" value={accountId} onChange={(e) => setAccountId(e.target.value)}>
                        {(!accounts || accounts.length === 0) && <option value="">Create a default account</option>}
                        {accounts?.map((account) => (
                          <option key={account.id} value={account.id}>
                            {account.accountName}
                            {account.accountNumberMasked ? ` (${account.accountNumberMasked})` : ''}
                          </option>
                        ))}
                      </Select>
                      <Button variant="outline" onClick={() => setAddingAccount((v) => !v)}>
                        New
                      </Button>
                    </div>
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="import-date-format">Date format</Label>
                    <Select id="import-date-format" value={dateFormat} onChange={(e) => setDateFormat(e.target.value)}>
                      {dateFormats.map((format) => (
                        <option key={format.value} value={format.value}>{format.label}</option>
                      ))}
                    </Select>
                  </div>
                </div>
                {addingAccount && (
                  <div className="rounded-lg border p-4">
                    <AccountForm
                      onCreated={(account) => {
                        setAccountId(account.id)
                        setAddingAccount(false)
                      }}
                      onCancel={() => setAddingAccount(false)}
                    />
                  </div>
                )}
                <CSVUpload file={file} onFileSelected={(f) => { setFile(f); setError(null) }} disabled={upload.isPending} />
                {file && (isPdfFile(file)
                  ? (
                    <p className="text-sm text-muted-foreground" data-testid="pdf-note">
                      PDF statement: SpendOS reads the transaction table when you import. Statements downloaded from
                      your bank's website or app work best; scanned or photographed pages can't be read yet.
                    </p>
                  )
                  : <ImportPreview file={file} />)}
                {error && (
                  <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
                    {error}
                  </div>
                )}
                <Button onClick={() => upload.mutate()} disabled={!file || upload.isPending}>
                  {upload.isPending ? 'Uploading…' : 'Import transactions'}
                </Button>
              </>
            )}

            {stage.kind === 'processing' && <ImportProgress jobId={stage.jobId} onFinished={handleFinished} />}

            {stage.kind === 'done' && (
              <div className="space-y-4">
                <ImportSummary jobId={stage.jobId} />
                <div className="flex flex-wrap gap-2">
                  <Link to="/transactions">
                    <Button>View transactions</Button>
                  </Link>
                  <Button variant="outline" onClick={reset}>Import another file</Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Import history</CardTitle>
            <CardDescription>Select an import to see its details.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <ImportHistory onSelect={setHistoryJob} />
            {historyJob && (
              <div className="rounded-lg border p-4">
                <ImportSummary jobId={historyJob} />
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </>
  )
}
