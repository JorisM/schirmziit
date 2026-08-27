import { useState } from 'react'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { DestructiveAction } from './DestructiveAction'
import { useI18n } from '../i18n'

type Purged = components['schemas']['PurgeResponse']

/**
 * Deletes a child's stored figures, and says how many rows went.
 *
 * The privacy page and both help screens promise this; until now it existed
 * only as an API route, which makes the promise true for whoever can run curl
 * and nobody else.
 *
 * The counts are not decoration: "deleted" with nothing behind it is exactly
 * the claim a family has no way to check. They come from the server's own
 * `rows_affected`, so a delete that matched nothing says zero rather than
 * implying a purge.
 */
export function PurgeData({ childId, onPurged }: { childId: string; onPurged: () => Promise<void> }) {
  const { t } = useI18n()
  const [purged, setPurged] = useState<Purged | null>(null)

  return (
    <section aria-labelledby="purge-heading" className="flex flex-col gap-2">
      <h3 id="purge-heading" className="text-lg">
        {t.data.title}
      </h3>
      <p className="max-w-prose text-sm" style={{ color: 'var(--ink-muted)' }}>
        {t.data.body}
      </p>

      {purged && (
        <div
          className="animate-[rise-in_var(--motion-base)_var(--ease-out)] flex flex-col gap-2 rounded-[12px] p-3"
          style={{ background: 'var(--sunken)' }}
        >
          <p role="status" className="text-sm font-medium">
            {t.data.deleted}
          </p>
          <dl className="flex flex-wrap gap-6 text-sm">
            {[
              [t.data.deletedHours, purged.deleted_usage_hours],
              [t.data.deletedDeviceHours, purged.deleted_device_hours],
              [t.data.deletedDays, purged.deleted_usage_days],
            ].map(([label, count]) => (
              <div key={label as string}>
                <dt style={{ color: 'var(--ink-faint)' }}>{label}</dt>
                <dd className="tabular text-lg">{count}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}

      <div className="flex justify-end">
        <DestructiveAction
          label={t.data.delete}
          body={t.data.deleteBody}
          confirmLabel={t.data.deleteConfirm}
          onConfirm={async () => {
            // Order matters: the counts are only set once the server has
            // answered, and `onPurged` re-reads the day the parent is looking
            // at — which is now empty and must not keep showing yesterday's
            // figures as though nothing happened.
            const result = await api.del<Purged>(`/v1/children/${childId}/data`)
            setPurged(result)
            await onPurged()
          }}
        />
      </div>
    </section>
  )
}
