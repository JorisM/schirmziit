import { useState } from 'react'
import type { components } from '../api/schema'
import { formatDuration, useI18n } from '../i18n'

type UsageResponse = components['schemas']['UsageResponse']

export type AppTotal = { package: string; label: string; ms: number; launches: number }

/** Per-app totals, biggest first. */
function toTotals(series: UsageResponse['series']): AppTotal[] {
  return series
    .map((entry) => ({
      package: entry.package,
      label: entry.label,
      ms: entry.points.reduce((sum, point) => sum + point.foreground_ms, 0),
      launches: entry.points.reduce((sum, point) => sum + point.launch_count, 0),
    }))
    .sort((a, b) => b.ms - a.ms)
}

/** Totals, biggest first, with everything past `keep` folded into one row. */
function foldTail(totals: AppTotal[], keep = 6): AppTotal[] {
  if (totals.length <= keep) return totals

  const rest = totals.slice(keep - 1)
  return [
    ...totals.slice(0, keep - 1),
    {
      package: '__other__',
      label: '',
      ms: rest.reduce((sum, app) => sum + app.ms, 0),
      launches: rest.reduce((sum, app) => sum + app.launches, 0),
    },
  ]
}

/** Per-app totals, biggest first, with everything past `keep` folded into one row. */
export function foldApps(series: UsageResponse['series'], keep = 6): AppTotal[] {
  return foldTail(toTotals(series), keep)
}

/**
 * Apps worth a row of their own, and the glances that are not.
 *
 * A launcher, a clock and a keyboard fill the list with rows nobody wants to
 * talk about and push the day's real apps off the screen. They stay reachable —
 * a parent and a child must be able to see the same numbers — but folded.
 */
export function splitApps(apps: AppTotal[]): { shown: AppTotal[]; brief: AppTotal[] } {
  const shown: AppTotal[] = []
  const brief: AppTotal[] = []
  for (const app of apps) {
    // Rounded, not raw: the row would render "0 s", which says nothing at all.
    if (Math.round(app.ms / 1000) === 0) continue
    if (app.ms < 60_000) brief.push(app)
    else shown.push(app)
  }
  return { shown, brief }
}

/**
 * A ranked table with the bar drawn inside the row: the number is the data and
 * the bar is the comparison, so no legend is needed and every value is labelled.
 */
export function AppBars({ series }: { series: UsageResponse['series'] }) {
  const { t } = useI18n()
  const [briefOpen, setBriefOpen] = useState(false)

  // The sub-minute split runs first, on the raw totals — foldApps then folds
  // only the `shown` tail, so a sub-minute app can never land inside both
  // its own brief row and the __other__ row's sum.
  const { shown, brief } = splitApps(toTotals(series))
  const apps = foldTail(shown)
  const busiest = apps[0]?.ms ?? 0

  if (apps.length === 0 && brief.length === 0) return null

  const row = (app: AppTotal, index: number) => (
    <tr
      key={app.package}
      className="align-middle"
      title={`${app.package === '__other__' ? t.child.otherApps : app.label} — ${formatDuration(app.ms, t)}`}
    >
      <td className="w-[34%] max-w-56 py-2 pr-4">
        <span className="block truncate">
          {app.package === '__other__' ? t.child.otherApps : app.label}
        </span>
      </td>
      <td className="py-2 pr-4">
        <div
          className="h-3 rounded-[4px]"
          style={{
            width: busiest > 0 ? `${Math.max(2, (app.ms / busiest) * 100)}%` : '2%',
            background:
              app.package === '__other__' ? 'var(--ink-faint)' : `var(--series-${(index % 6) + 1})`,
          }}
        />
      </td>
      <td className="tabular w-24 py-2 text-right whitespace-nowrap">{formatDuration(app.ms, t)}</td>
      <td
        className="tabular w-20 py-2 text-right text-sm whitespace-nowrap"
        style={{ color: 'var(--ink-faint)' }}
      >
        {app.launches}×
      </td>
    </tr>
  )

  return (
    <section>
      <div className="mb-1 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-lg">{t.child.appsTitle}</h3>
        <span className="text-sm" style={{ color: 'var(--ink-faint)' }}>
          {t.child.appsHelp}
        </span>
      </div>

      <table className="w-full border-collapse">
        <thead className="sr-only">
          <tr>
            <th>{t.child.appColumn}</th>
            <th>{t.child.timeColumn}</th>
            <th>{t.child.openCountColumn}</th>
          </tr>
        </thead>
        <tbody>
          {apps.map((app, index) => row(app, index))}
          {brief.length > 0 && (
            <tr>
              <td colSpan={4} className="py-2">
                <button
                  type="button"
                  aria-expanded={briefOpen}
                  onClick={() => setBriefOpen((open) => !open)}
                  className="text-sm underline"
                  style={{ color: 'var(--ink-faint)' }}
                >
                  {t.child.briefApps} ({brief.length})
                </button>
              </td>
            </tr>
          )}
          {briefOpen && brief.map((app, index) => row(app, apps.length + index))}
        </tbody>
      </table>
    </section>
  )
}
