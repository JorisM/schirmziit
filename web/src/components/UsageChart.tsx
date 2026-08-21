import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { components } from '../api/schema'

type UsageResponse = components['schemas']['UsageResponse']

/// Recharts wants one row per x value with a column per series, so pivot.
export function UsageChart({ series }: { series: UsageResponse['series'] }) {
  const byStart = new Map<string, Record<string, number | string>>()
  for (const entry of series) {
    for (const point of entry.points) {
      const row =
        byStart.get(point.start) ??
        ({
          start: new Date(point.start).toLocaleTimeString([], { hour: '2-digit' }),
        } as Record<string, number | string>)
      row[entry.label] = Math.round(point.foreground_ms / 60_000)
      byStart.set(point.start, row)
    }
  }
  const rows = [...byStart.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([, row]) => row)

  return (
    <ResponsiveContainer width="100%" height={280}>
      <BarChart data={rows}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="start" />
        <YAxis label={{ value: 'minutes', angle: -90, position: 'insideLeft' }} />
        <Tooltip />
        <Legend />
        {series.map((entry) => (
          <Bar key={entry.package} dataKey={entry.label} stackId="usage" />
        ))}
      </BarChart>
    </ResponsiveContainer>
  )
}
