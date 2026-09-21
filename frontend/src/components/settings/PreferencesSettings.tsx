import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label, Select } from '@/components/ui/input'
import { Spinner } from '@/components/ui/feedback'
import { userService } from '@/services/userService'
import { useUiStore } from '@/store/uiStore'
import type { Preferences } from '@/types/auth'

const currencies = ['INR', 'USD', 'EUR', 'GBP', 'SGD', 'AED']
const timezones = ['Asia/Kolkata', 'Asia/Singapore', 'Asia/Dubai', 'Europe/London', 'Europe/Berlin', 'America/New_York', 'America/Los_Angeles', 'UTC']

export function PreferencesSettings() {
  const queryClient = useQueryClient()
  const setTheme = useUiStore((state) => state.setTheme)
  const { data, isLoading } = useQuery({ queryKey: ['preferences'], queryFn: userService.preferences })
  const mutation = useMutation({
    mutationFn: (input: Partial<Preferences>) => userService.updatePreferences(input),
    onSuccess: (preferences) => {
      queryClient.setQueryData(['preferences'], preferences)
      setTheme(preferences.theme)
    },
  })

  if (isLoading || !data) return <Spinner />

  const toggle = (key: 'emailReportsEnabled' | 'emailAlertsEnabled' | 'financialHealthScoreEnabled', label: string) => (
    <label className="flex items-center justify-between gap-4 text-sm">
      <span>{label}</span>
      <input type="checkbox" className="h-4 w-4 accent-[hsl(var(--primary))]" checked={data[key]}
        onChange={(e) => mutation.mutate({ [key]: e.target.checked })} />
    </label>
  )

  return (
    <Card>
      <CardHeader>
        <CardTitle>Preferences</CardTitle>
        <CardDescription>Currency, time zone and display settings.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4 sm:max-w-md">
        <div className="space-y-2">
          <Label htmlFor="pref-currency">Currency</Label>
          <Select id="pref-currency" value={data.currencyCode} onChange={(e) => mutation.mutate({ currencyCode: e.target.value })}>
            {currencies.map((code) => <option key={code} value={code}>{code}</option>)}
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="pref-timezone">Time zone</Label>
          <Select id="pref-timezone" value={data.timezone} onChange={(e) => mutation.mutate({ timezone: e.target.value })}>
            {[data.timezone, ...timezones.filter((tz) => tz !== data.timezone)].map((tz) => <option key={tz} value={tz}>{tz}</option>)}
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="pref-theme">Theme</Label>
          <Select id="pref-theme" value={data.theme} onChange={(e) => mutation.mutate({ theme: e.target.value as Preferences['theme'] })}>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
            <option value="system">System</option>
          </Select>
        </div>
        {toggle('financialHealthScoreEnabled', 'Show financial health score')}
        {toggle('emailReportsEnabled', 'Monthly email reports')}
        {toggle('emailAlertsEnabled', 'Budget email alerts')}
        {mutation.isError && <p role="alert" className="text-sm text-destructive">Could not save preferences.</p>}
      </CardContent>
    </Card>
  )
}
