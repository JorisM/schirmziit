import type { components } from '../api/schema'

type DeviceStatusRow = components['schemas']['DeviceStatus']

export function formatDuration(ms: number): string {
  const minutes = Math.round(ms / 60_000)
  if (minutes < 60) return `${minutes}m`
  const hours = Math.floor(minutes / 60)
  return minutes % 60 === 0 ? `${hours}h` : `${hours}h ${minutes % 60}m`
}

/// A silent agent looks exactly like a child who did not touch their phone, so
/// staleness gets a loud, unmissable banner rather than a subtle icon.
export function DeviceStatus({ devices }: { devices: DeviceStatusRow[] }) {
  const stale = devices.filter((d) => d.stale)
  if (stale.length === 0) return null

  return (
    <div role="alert" className="rounded border border-amber-400 bg-amber-50 p-3 text-amber-900">
      {stale.map((device) => (
        <p key={device.id}>
          <strong>{device.label}</strong>{' '}
          {device.last_seen_at
            ? `has not reported since ${new Date(device.last_seen_at).toLocaleString()}`
            : 'has never reported'}{' '}
          — usage shown below may be incomplete.
        </p>
      ))}
    </div>
  )
}
