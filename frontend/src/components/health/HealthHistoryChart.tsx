import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatDate } from '@/utils/formatters'
import type { HealthHistoryPoint } from '@/types/health'

export default function HealthHistoryChart({ history }: { history: HealthHistoryPoint[] }) {
  const data = history.map((point) => ({ name: formatDate(`${point.period}-01`, 'MMM yy'), Score: point.score }))
  const first = history[0]
  const last = history[history.length - 1]
  return (
    <div className="h-56" role="img"
      aria-label={`Health score over ${history.length} months, from ${first?.score ?? 'none'} to ${last?.score ?? 'none'}`}>
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ left: 0, right: 8, top: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
          <XAxis dataKey="name" fontSize={12} tickLine={false} axisLine={false} />
          <YAxis domain={[0, 100]} fontSize={12} width={32} tickLine={false} axisLine={false} />
          <Tooltip />
          <Line type="monotone" dataKey="Score" stroke="hsl(var(--primary))" strokeWidth={2} dot={{ r: 3 }} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
