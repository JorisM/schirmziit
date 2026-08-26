import type { components } from '../api/schema'
import { formatDuration, useI18n } from '../i18n'

type UsageResponse = components['schemas']['UsageResponse']

/**
 * Total foreground time per local day, one entry per day in `[from, to]`.
 *
 * Deliberately the same measure as the hero total. Screen-on time would be a
 * second, different number for the same day on the same screen.
 */
export function dailyTotals(
  series: UsageResponse['series'],
  from: string,
  to: string,
): { day: string; ms: number }[] {
  const totals = new Map<string, number>()
  // Zero-fill first: a day with no rows is a quiet day, not a missing one, and
  // a gap in the strip reads as a fault in the app.
  for (let day = from; day <= to; day = nextDay(day)) totals.set(day, 0)

  for (const entry of series) {
    for (const point of entry.points) {
      const current = totals.get(point.start)
      // Only days the response claimed. A stray point must not land on day one.
      if (current !== undefined) totals.set(point.start, current + point.foreground_ms)
    }
  }
  return [...totals.entries()].map(([day, ms]) => ({ day, ms }))
}

function nextDay(day: string): string {
  const date = new Date(`${day}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + 1)
  return date.toISOString().slice(0, 10)
}

/**
 * Fourteen days as bars. The ribbon answers "when in the day"; this answers
 * "was today unusual" — which is the question a single day cannot.
 */
export function DayStrip({
  series,
  from,
  to,
  selected,
  onSelect,
}: {
  series: UsageResponse['series']
  from: string
  to: string
  selected: string
  onSelect: (day: string) => void
}) {
  const { t, locale } = useI18n()
  const days = dailyTotals(series, from, to)
  const busiest = Math.max(...days.map((d) => d.ms), 0)

  return (
    <figure className="m-0">
      <figcaption className="mb-1 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-lg">{t.child.historyTitle}</h3>
        <span className="text-sm" style={{ color: 'var(--ink-faint)' }}>
          {t.child.historyHelp}
        </span>
      </figcaption>

      <div className="flex items-end gap-1">
        {days.map(({ day, ms }, index) => {
          const share = busiest > 0 ? ms / busiest : 0
          const label = new Date(`${day}T00:00:00`).toLocaleDateString(locale, {
            weekday: 'short',
            day: 'numeric',
            month: 'short',
          })
          return (
            <button
              key={day}
              type="button"
              aria-pressed={day === selected}
              onClick={() => onSelect(day)}
              className="flex flex-1 cursor-pointer flex-col items-center gap-1 rounded-[4px] bg-transparent p-0"
              title={`${label} — ${formatDuration(ms, t)}`}
              aria-label={`${label} — ${formatDuration(ms, t)}`}
            >
              <span
                className="w-full rounded-[3px] origin-bottom animate-[grow-up_var(--motion-base)_var(--ease-out)_backwards] transition-[box-shadow,transform] duration-[var(--motion-fast)] ease-[var(--ease-out)]"
                style={{
                  animationDelay: `calc(${index} * var(--motion-stagger))`,
                  // A floor, not a zero: an empty day is still a day, and a bar
                  // of no height reads as a hole in the chart.
                  height: `${8 + Math.round(share * 56)}px`,
                  background: ms > 0 ? 'var(--accent)' : 'var(--hairline)',
                  boxShadow:
                    day === selected ? 'inset 0 0 0 2px var(--ink-muted)' : 'inset 0 0 0 1px var(--hairline)',
                }}
              />
              <span className="font-mono text-[11px]" style={{ color: 'var(--ink-faint)' }}>
                {new Date(`${day}T00:00:00`).toLocaleDateString(locale, { day: 'numeric' })}
              </span>
            </button>
          )
        })}
      </div>
    </figure>
  )
}
