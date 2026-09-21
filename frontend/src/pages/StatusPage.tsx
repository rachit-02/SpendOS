import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge, Spinner } from '@/components/ui/feedback'
import { healthService } from '@/services/healthService'

/** System status page: shows whether the API and database are reachable. */
export default function StatusPage() {
  const { data, isLoading, isError } = useQuery({ queryKey: ['health'], queryFn: healthService.get, retry: false })

  return (
    <main className="container flex min-h-screen max-w-lg flex-col justify-center py-10">
      <Card>
        <CardHeader>
          <CardTitle>SpendOS system status</CardTitle>
          <CardDescription>API and database connectivity</CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading && <Spinner label="Checking" />}
          {isError && <Badge tone="danger">API unreachable</Badge>}
          {data && (
            <ul className="space-y-2 text-sm">
              <li className="flex justify-between">
                <span>API</span>
                <Badge tone={data.status === 'UP' ? 'success' : 'danger'}>{data.status}</Badge>
              </li>
              {Object.entries(data.checks).map(([name, status]) => (
                <li key={name} className="flex justify-between capitalize">
                  <span>{name}</span>
                  <Badge tone={status === 'UP' ? 'success' : 'danger'}>{status}</Badge>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </main>
  )
}
