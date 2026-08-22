import useSWR from 'swr'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { components } from '../api/schema'
import { AppBars } from '../components/AppBars'
import { DayRibbon } from '../components/DayRibbon'
import { DeviceStatus } from '../components/DeviceStatus'
import { formatDuration, useI18n } from '../i18n'

type UsageResponse = components['schemas']['UsageResponse']

const today = () => new Date().toISOString().slice(0, 10)
const localZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone

export function ChildDetail({ childId }: { childId: string }) {
  const { t, locale } = useI18n()
  const day = today()
  const { data, error } = useSWR<UsageResponse>(
    `/v1/children/${childId}/usage?from=${day}&to=${day}&bucket=hour&tz=${localZone()}`,
    api.get,
    { refreshInterval: 60_000, shouldRetryOnError: false },
  )

  if (error) {
    return (
      <p role="alert" style={{ color: 'var(--urgent)' }}>
        {error instanceof ApiError ? error.problem.detail : t.errors.generic}
      </p>
    )
  }
  if (!data) return <p style={{ color: 'var(--ink-faint)' }}>…</p>

  const screenTime = data.series.reduce(
    (sum, entry) => sum + entry.points.reduce((inner, point) => inner + point.foreground_ms, 0),
    0,
  )
  const unlocks = data.device_totals.reduce((sum, total) => sum + total.unlock_count, 0)
  const stamps = data.series.flatMap((entry) => entry.points.map((point) => point.start)).sort()
  const clock = (iso: string | undefined) =>
    iso ? new Date(iso).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' }) : '—'

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-1">
        <Link to="/" className="text-sm underline" style={{ color: 'var(--accent)' }}>
          {t.children.heading}
        </Link>
        <h1 className="text-2xl">{t.child.todayHeading}</h1>
      </header>

      {/* The hero is the day's total in the display face, with the numbers that
          qualify it right beneath — a big number alone invites the wrong
          conclusion when a phone has stopped reporting. */}
      <section className="card flex flex-wrap items-end justify-between gap-6 p-6">
        <div>
          <p className="text-sm" style={{ color: 'var(--ink-muted)' }}>
            {t.child.totalToday}
          </p>
          <p className="font-display tabular text-5xl leading-none">
            {formatDuration(screenTime, t)}
          </p>
        </div>
        <dl className="flex gap-8 text-sm">
          <div>
            <dt style={{ color: 'var(--ink-faint)' }}>{t.child.unlocks}</dt>
            <dd className="tabular text-lg">{unlocks}</dd>
          </div>
          <div>
            <dt style={{ color: 'var(--ink-faint)' }}>{t.child.firstActivity}</dt>
            <dd className="tabular text-lg">{clock(stamps[0])}</dd>
          </div>
          <div>
            <dt style={{ color: 'var(--ink-faint)' }}>{t.child.lastActivity}</dt>
            <dd className="tabular text-lg">{clock(stamps[stamps.length - 1])}</dd>
          </div>
        </dl>
      </section>

      {screenTime === 0 && (
        <section className="card p-5">
          <p className="font-medium">{t.child.noDataToday}</p>
          <p className="mt-1 max-w-prose text-sm" style={{ color: 'var(--ink-muted)' }}>
            {t.child.noDataHint}
          </p>
        </section>
      )}

      <section className="card p-6">
        <DayRibbon totals={data.device_totals} />
      </section>

      <section className="card p-6">
        <AppBars series={data.series} />
      </section>

      <section className="card p-6">
        <DeviceStatus devices={data.devices} />
      </section>
    </div>
  )
}
