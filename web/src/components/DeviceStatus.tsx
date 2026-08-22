import type { components } from '../api/schema'
import { useI18n } from '../i18n'

type DeviceStatusRow = components['schemas']['DeviceStatus']

/**
 * A silent agent looks exactly like a child who did not touch their phone. This
 * says which one it is, in words, before a parent draws the wrong conclusion.
 */
export function DeviceStatus({ devices }: { devices: DeviceStatusRow[] }) {
  const { t, locale } = useI18n()
  if (devices.length === 0) return null

  const stale = devices.filter((device) => device.stale)

  return (
    <section aria-labelledby="devices-heading">
      <h3 id="devices-heading" className="mb-2 text-lg">
        {t.devices.title}
      </h3>

      <ul className="flex flex-col gap-2">
        {devices.map((device) => (
          <li key={device.id} className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <span
              aria-hidden="true"
              className="inline-block h-2 w-2 rounded-full"
              style={{ background: device.stale ? 'var(--warn)' : 'var(--ok)' }}
            />
            <span className="font-medium">{device.label}</span>
            <span className="text-sm" style={{ color: device.stale ? 'var(--warn)' : 'var(--ok)' }}>
              {device.stale ? t.devices.stale : t.devices.fresh}
            </span>
            <span className="text-sm" style={{ color: 'var(--ink-faint)' }}>
              {device.last_seen_at
                ? `${t.devices.lastSeen}: ${new Date(device.last_seen_at).toLocaleString(locale)}`
                : t.devices.neverReported}
            </span>
          </li>
        ))}
      </ul>

      {stale.length > 0 && (
        <p
          role="alert"
          className="mt-3 rounded-[12px] p-3 text-sm"
          style={{ background: 'var(--sunken)', color: 'var(--ink-muted)' }}
        >
          {t.devices.staleHelp}
        </p>
      )}
    </section>
  )
}
