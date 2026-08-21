import useSWR from 'swr'
import { ApiError, api } from '../api/client'
import type { components } from '../api/schema'
import { DeviceStatus, formatDuration } from '../components/DeviceStatus'
import { UsageChart } from '../components/UsageChart'

type UsageResponse = components['schemas']['UsageResponse']

const today = () => new Date().toISOString().slice(0, 10)
const localZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone

export function ChildDetail({ childId }: { childId: string }) {
  const day = today()
  const { data, error } = useSWR<UsageResponse>(
    `/v1/children/${childId}/usage?from=${day}&to=${day}&bucket=hour&tz=${localZone()}`,
    api.get,
    { refreshInterval: 60_000, shouldRetryOnError: false },
  )

  if (error) {
    return (
      <p role="alert">
        {error instanceof ApiError ? error.problem.detail : 'failed to load usage'}
      </p>
    )
  }
  if (!data) return <p>Loading…</p>

  const totals = data.series
    .map((entry) => ({
      package: entry.package,
      label: entry.label,
      ms: entry.points.reduce((sum, point) => sum + point.foreground_ms, 0),
    }))
    .sort((a, b) => b.ms - a.ms)

  const unlocks = data.device_totals.reduce((sum, total) => sum + total.unlock_count, 0)
  const screenTime = totals.reduce((sum, total) => sum + total.ms, 0)

  return (
    <div className="flex flex-col gap-6 p-6">
      <DeviceStatus devices={data.devices} />
      <p className="text-sm text-slate-600">
        {formatDuration(screenTime)} today · {unlocks} unlocks
      </p>
      <UsageChart series={data.series} />
      <table className="w-full text-left">
        <thead>
          <tr>
            <th>App</th>
            <th>Time</th>
          </tr>
        </thead>
        <tbody>
          {totals.map((total) => (
            <tr key={total.package}>
              <td>{total.label}</td>
              <td>{formatDuration(total.ms)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
