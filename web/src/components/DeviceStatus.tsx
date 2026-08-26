import type { components } from '../api/schema'
import { DestructiveAction } from './DestructiveAction'
import { useI18n } from '../i18n'

type DeviceStatusRow = components['schemas']['DeviceStatus']

/**
 * A silent agent looks exactly like a child who did not touch their phone. This
 * says which one it is, in words, before a parent draws the wrong conclusion.
 *
 * `onRevoke` is optional because the affordance has to follow the ability: a
 * disconnect button that posts nothing is worse than no button at all.
 */
export function DeviceStatus({
  devices,
  onRevoke,
}: {
  devices: DeviceStatusRow[]
  onRevoke?: (deviceId: string) => Promise<void>
}) {
  const { t, locale } = useI18n()
  if (devices.length === 0) return null

  const stale = devices.filter((device) => device.stale)

  return (
    <section aria-labelledby="devices-heading">
      <h3 id="devices-heading" className="mb-2 text-lg">
        {t.devices.title}
      </h3>

      <ul className="flex flex-col gap-3">
        {devices.map((device) => (
          <li key={device.id} className="flex flex-col gap-1">
            <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <span
                aria-hidden="true"
                className="inline-block h-2 w-2 rounded-full"
                style={{ background: device.stale ? 'var(--warn)' : 'var(--ok)' }}
              />
              <span className="font-medium">{device.label}</span>
              <span
                className="text-sm"
                style={{ color: device.stale ? 'var(--warn)' : 'var(--ok)' }}
              >
                {device.stale ? t.devices.stale : t.devices.fresh}
              </span>
              <span className="text-sm" style={{ color: 'var(--ink-faint)' }}>
                {device.last_seen_at
                  ? `${t.devices.lastSeen}: ${new Date(device.last_seen_at).toLocaleString(locale)}`
                  : t.devices.neverReported}
              </span>
            </div>
            {/* Its own row rather than trailing the line above: expanded, the
                confirmation is a paragraph and two buttons, and squeezing that
                into the end of a wrapping status line moves the status text
                around as it opens. */}
            {onRevoke && (
              <div className="flex justify-end">
                <DestructiveAction
                  label={t.devices.revoke}
                  body={t.devices.revokeBody}
                  confirmLabel={t.devices.revokeConfirm}
                  onConfirm={() => onRevoke(device.id)}
                />
              </div>
            )}
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
