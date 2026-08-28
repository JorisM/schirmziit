import type { components } from '../api/schema'
import { formatDuration, useI18n } from '../i18n'
import type { Strings } from '../i18n/types'

type WeekComparison = components['schemas']['WeekComparison']

/**
 * Last full week against the one before it — the sentence a parent comes back
 * for, and the only thing on this screen that answers "is this week unusual".
 *
 * Every number here is the server's; nothing is recomputed in the browser, so
 * the dashboard and the two phones say the same thing about the same week.
 * Nothing here judges the child: it reports what moved and by how much, in
 * both directions, with no target to hit and no streak to keep.
 */
export function WeekInsight({ week }: { week: WeekComparison }) {
  const { t, locale } = useI18n()
  const day = (iso: string) =>
    new Date(`${iso}T00:00:00`).toLocaleDateString(locale, { day: 'numeric', month: 'short' })
  const evening = `${String(week.evening_from_hour).padStart(2, '0')}:00`

  return (
    <section className="card flex flex-col gap-4 p-6">
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg">{t.week.title}</h2>
        <span data-testid="week-range" className="font-mono text-sm" style={{ color: 'var(--ink-faint)' }}>
          {day(week.from)} – {day(week.to)}
        </span>
      </header>

      <div className="flex flex-wrap gap-8">
        <Figure
          testId="week-total"
          label={t.week.total}
          value={formatDuration(week.total_ms, t)}
          delay={0}
        >
          {week.previous_measured && (
            <Delta testId="week-total-delta" ms={week.total_ms - week.previous_total_ms} t={t} />
          )}
        </Figure>

        <Figure
          testId="week-evening"
          label={`${t.week.eveningFrom} ${evening}`}
          value={formatDuration(week.evening_ms, t)}
          delay={1}
        >
          {week.previous_measured && (
            <Delta
              testId="week-evening-delta"
              ms={week.evening_ms - week.previous_evening_ms}
              t={t}
            />
          )}
        </Figure>
      </div>

      {week.previous_measured ? (
        <div className="flex flex-col gap-2">
          <h3 className="text-sm" style={{ color: 'var(--ink-faint)' }}>
            {t.week.moversTitle}
          </h3>
          {week.movers.length === 0 ? (
            <p data-testid="week-no-movers" className="m-0 text-sm" style={{ color: 'var(--ink-muted)' }}>
              {t.week.noMovers}
            </p>
          ) : (
            <ul className="m-0 flex list-none flex-col gap-2 p-0">
              {week.movers.map((mover, index) => (
                <li
                  key={mover.package}
                  data-testid="week-mover"
                  className="flex flex-wrap items-baseline justify-between gap-2 animate-[rise-in_var(--motion-base)_var(--ease-out)_backwards]"
                  style={{ animationDelay: `calc(${index + 2} * var(--motion-stagger))` }}
                >
                  <span>{mover.label}</span>
                  <Delta ms={mover.foreground_ms - mover.previous_foreground_ms} t={t} />
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        // Not a rise of a hundred per cent, and not a blank: the week before
        // this one had no phone reporting, and a comparison against silence is
        // the lost day this product exists not to show.
        <p data-testid="week-first" className="m-0 text-sm" style={{ color: 'var(--ink-muted)' }}>
          {t.week.firstWeek}
        </p>
      )}
    </section>
  )
}

function Figure({
  testId,
  label,
  value,
  delay,
  children,
}: {
  testId: string
  label: string
  value: string
  delay: number
  children?: React.ReactNode
}) {
  return (
    <div
      data-testid={testId}
      className="flex flex-col gap-1 animate-[rise-in_var(--motion-base)_var(--ease-out)_backwards]"
      style={{ animationDelay: `calc(${delay} * var(--motion-stagger))` }}
    >
      <span className="text-sm" style={{ color: 'var(--ink-faint)' }}>
        {label}
      </span>
      <span className="text-2xl">{value}</span>
      {children}
    </div>
  )
}

/**
 * A direction in words as well as in an arrow and a colour: an arrow alone is
 * lost on a screen reader, and colour alone is lost on the eight per cent of
 * men who cannot separate these two.
 */
function Delta({ ms, t, testId }: { ms: number; t: Strings; testId?: string }) {
  if (ms === 0) {
    return (
      <span data-testid={testId} className="text-sm" style={{ color: 'var(--ink-faint)' }}>
        {t.week.same}
      </span>
    )
  }
  const up = ms > 0
  return (
    <span
      data-testid={testId}
      className="text-sm"
      style={{ color: 'var(--ink-muted)' }}
    >
      <span aria-hidden="true">{up ? '▲' : '▼'}</span> {formatDuration(Math.abs(ms), t)}{' '}
      {up ? t.week.more : t.week.less}
    </span>
  )
}
