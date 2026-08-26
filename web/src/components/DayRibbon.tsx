import { useState } from 'react'
import type { components } from '../api/schema'
import { formatDuration, useI18n } from '../i18n'

type UsageResponse = components['schemas']['UsageResponse']

/** Screen-on milliseconds per local hour, 0..23. */
export function hoursFromTotals(totals: UsageResponse['device_totals']): number[] {
  const perHour = new Array<number>(24).fill(0)
  // "2026-08-21T15:00:00+02:00" — the server already applied the caller's
  // timezone, so the local hour is the field after the T. Matched strictly:
  // slicing blindly turns any unparseable value into hour 0, which would paint
  // phantom midnight usage — the one thing this ribbon exists to reveal.
  const localHour = /^\d{4}-\d{2}-\d{2}T(\d{2}):/
  for (const total of totals) {
    const match = localHour.exec(total.start)
    if (!match) continue
    const hour = Number(match[1])
    if (hour >= 0 && hour < 24) perHour[hour] = (perHour[hour] ?? 0) + total.screen_on_ms
  }
  return perHour
}

/** Sequential ramp step for a magnitude, relative to the busiest hour. */
export function rampStep(ms: number, busiestMs: number): number {
  if (ms <= 0) return 0
  if (busiestMs <= 0) return 0
  const share = ms / busiestMs
  if (share > 0.8) return 5
  if (share > 0.6) return 4
  if (share > 0.4) return 3
  if (share > 0.2) return 2
  return 1
}

/**
 * The day as 24 cells, midnight to midnight. A bar chart answers "how much"; a
 * parent's actual question is "when" — an hour of use at 23:00 means something
 * different from an hour after lunch, and only the shape of the day shows that.
 */
export function DayRibbon({ totals }: { totals: UsageResponse['device_totals'] }) {
  const { t } = useI18n()
  const [hovered, setHovered] = useState<number | null>(null)
  const perHour = hoursFromTotals(totals)
  const busiest = Math.max(...perHour, 0)

  return (
    <figure className="m-0">
      <figcaption className="mb-1 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-lg">{t.child.ribbonTitle}</h3>
        <span className="text-sm" style={{ color: 'var(--ink-faint)' }}>
          {hovered === null
            ? t.child.ribbonHelp
            : `${String(hovered).padStart(2, '0')}:00 — ${formatDuration(perHour[hovered] ?? 0, t)}`}
        </span>
      </figcaption>

      <div className="grid grid-cols-24 gap-[2px]" role="img" aria-label={t.child.ribbonHelp}>
        {perHour.map((ms, hour) => {
          const step = rampStep(ms, busiest)
          const night = hour < 6 || hour >= 22
          const clock = `${String(hour).padStart(2, '0')}:00`
          return (
            <div
              key={hour}
              data-ribbon-cell
              // Enters plainly. The background wave beneath it is this
              // screen's one flourish; two performing at once and both lose.
              className="cursor-default rounded-[4px] transition-transform"
              style={{
                height: 56,
                background: `var(--ribbon-${step})`,
                // Every cell keeps a hairline, so an empty 03:00 still reads as
                // an hour that happened rather than a hole in the ribbon.
                boxShadow: 'inset 0 0 0 1px var(--hairline)',
                transform: hovered === hour ? 'translateY(-2px)' : undefined,
              }}
              onMouseEnter={() => setHovered(hour)}
              onMouseLeave={() => setHovered(null)}
              title={`${clock}${night ? ` (${t.child.ribbonNight})` : ''} — ${formatDuration(ms, t)}`}
            />
          )
        })}
      </div>

      <div
        className="mt-1 grid grid-cols-24 font-mono text-[11px]"
        style={{ color: 'var(--ink-faint)' }}
        aria-hidden="true"
      >
        {Array.from({ length: 24 }, (_, hour) => (
          <span key={hour} className="text-center">
            {hour % 6 === 0 ? String(hour).padStart(2, '0') : ''}
          </span>
        ))}
      </div>

      <div className="mt-2 flex items-center gap-2 text-xs" style={{ color: 'var(--ink-muted)' }}>
        <span>{t.child.ribbonQuiet}</span>
        {[0, 1, 2, 3, 4, 5].map((step) => (
          <span
            key={step}
            className="inline-block h-3 w-4 rounded-[3px]"
            style={{ background: `var(--ribbon-${step})` }}
          />
        ))}
        <span>{t.child.ribbonBusy}</span>
      </div>
    </figure>
  )
}
