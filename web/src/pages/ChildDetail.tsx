import { useState } from 'react'
import useSWR from 'swr'
import { Link } from 'react-router-dom'
import { api, AppError } from '../api/client'
import type { components } from '../api/schema'
import { AppBars } from '../components/AppBars'
import { BackgroundWave } from '../components/BackgroundWave'
import { DayRibbon } from '../components/DayRibbon'
import { DayStrip } from '../components/DayStrip'
import { DeviceStatus } from '../components/DeviceStatus'
import { ErrorPanel } from '../components/ErrorPanel'
import { PairDevice } from '../components/PairDevice'
import { PurgeData } from '../components/PurgeData'
import { HeroSkeleton, RibbonSkeleton, RowsSkeleton, StripSkeleton } from '../components/Skeleton'
import { formatDuration, useI18n } from '../i18n'

type UsageResponse = components['schemas']['UsageResponse']

const STRIP_DAYS = 14

// `toISOString` reports the UTC date, which is still "yesterday" for the first
// couple of hours after local midnight in Zurich (UTC+1/+2) — exactly the
// window a teenager is most likely checking. `en-CA` formats as YYYY-MM-DD in
// the viewer's own zone, matching the `tz=` this page already sends the server.
export const localToday = (now: Date = new Date()) => now.toLocaleDateString('en-CA')
const today = () => localToday()
const daysAgo = (n: number) => {
  const date = new Date(`${today()}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() - n)
  return date.toISOString().slice(0, 10)
}
const localZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone

export function ChildDetail({ childId }: { childId: string }) {
  const { t, locale } = useI18n()
  const [selected, setSelected] = useState(today())
  const from = daysAgo(STRIP_DAYS - 1)

  const { data: strip, error: stripError, mutate: refreshStrip } = useSWR<UsageResponse>(
    `/v1/children/${childId}/usage?from=${from}&to=${today()}&bucket=day&tz=${localZone()}`,
    api.get,
    { refreshInterval: 60_000, shouldRetryOnError: false },
  )
  const { data, error, mutate } = useSWR<UsageResponse>(
    `/v1/children/${childId}/usage?from=${selected}&to=${selected}&bucket=hour&tz=${localZone()}`,
    api.get,
    { refreshInterval: 60_000, shouldRetryOnError: false },
  )

  if (error) {
    return <ErrorPanel error={error as AppError} onRetry={() => void mutate()} />
  }
  const screenTime =
    data?.series.reduce(
      (sum, entry) => sum + entry.points.reduce((inner, point) => inner + point.foreground_ms, 0),
      0,
    ) ?? 0
  const unlocks = data?.device_totals.reduce((sum, total) => sum + total.unlock_count, 0) ?? 0
  // Deliberately not part of `screenTime`: media playing with the screen off
  // is a separate measure, and adding it would inflate every screen-time
  // number the parent reads.
  const backgroundTime =
    data?.series.reduce(
      (sum, entry) => sum + entry.points.reduce((inner, point) => inner + point.background_ms, 0),
      0,
    ) ?? 0
  // Not "no background time" — "no device reporting this day could observe
  // it". An iPhone, or an Android phone whose family declined the grant.
  const backgroundMeasured = data?.device_totals.some((total) => total.background_measured) ?? false
  const stamps = (data?.series ?? []).flatMap((entry) => entry.points.map((p) => p.start)).sort()
  const clock = (iso: string | undefined) =>
    iso ? new Date(iso).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' }) : '—'

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-1">
        <Link to="/" className="text-sm underline" style={{ color: 'var(--accent)' }}>
          {t.children.heading}
        </Link>
        <h1 className="text-2xl">
          {selected === today()
            ? t.child.today
            : new Date(`${selected}T00:00:00`).toLocaleDateString(locale, {
                weekday: 'long',
                day: 'numeric',
                month: 'long',
              })}
        </h1>
      </header>

      <section className="card p-6">
        {strip ? (
          <>
            {/* A failed refresh leaves the fortnight the parent is looking at
                exactly where it was, with a banner saying it is stale. */}
            {stripError && (
              <ErrorPanel
                error={stripError as AppError}
                variant="banner"
                onRetry={() => void refreshStrip()}
              />
            )}
            <DayStrip series={strip.series} from={from} to={today()} selected={selected} onSelect={setSelected} />
          </>
        ) : stripError ? (
          // Never zero-fill in place of a failed fetch: fourteen grey bars read as a
          // genuinely quiet fortnight, which is exactly the "lost day" this app promises
          // never to show.
          <ErrorPanel error={stripError as AppError} onRetry={() => void refreshStrip()} />
        ) : (
          <StripSkeleton />
        )}
      </section>

      {/* The hero is the day's total in the display face, with the numbers that
          qualify it right beneath — a big number alone invites the wrong
          conclusion when a phone has stopped reporting. */}
      <section className="card flex flex-wrap items-end justify-between gap-6 p-6">
        {data ? (
          <>
            <div>
              <p className="text-sm" style={{ color: 'var(--ink-muted)' }}>
                {selected === today() ? t.child.totalToday : t.child.selectedHeading}
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
              {backgroundMeasured && (
                <div>
                  <dt style={{ color: 'var(--ink-faint)' }}>{t.child.backgroundTotal}</dt>
                  <dd className="tabular text-lg" style={{ color: 'var(--background-wave)' }}>
                    {formatDuration(backgroundTime, t)}
                  </dd>
                </div>
              )}
              <div>
                <dt style={{ color: 'var(--ink-faint)' }}>{t.child.firstActivity}</dt>
                <dd className="tabular text-lg">{clock(stamps[0])}</dd>
              </div>
              <div>
                <dt style={{ color: 'var(--ink-faint)' }}>{t.child.lastActivity}</dt>
                <dd className="tabular text-lg">{clock(stamps[stamps.length - 1])}</dd>
              </div>
            </dl>
          </>
        ) : (
          <HeroSkeleton />
        )}
      </section>

      {data && screenTime === 0 && (
        <section className="card p-5">
          <p className="font-medium">{selected === today() ? t.child.noDataToday : t.child.noDataDay}</p>
          <p className="mt-1 max-w-prose text-sm" style={{ color: 'var(--ink-muted)' }}>
            {t.child.noDataHint}
          </p>
        </section>
      )}

      <section className="card p-6">
        {data ? (
          <>
            <DayRibbon totals={data.device_totals} />
            <BackgroundWave series={data.series} measured={backgroundMeasured} />
          </>
        ) : (
          <RibbonSkeleton />
        )}
      </section>

      <section className="card p-6">
        {data ? <AppBars series={data.series} /> : <RowsSkeleton />}
      </section>

      <section className="card p-6">
        {data ? (
          <DeviceStatus
            devices={data.devices}
            // A revoked device drops out of this response, so re-reading the
            // day is what makes the row disappear — no local list to keep in
            // step with the server.
            onRevoke={async (deviceId) => {
              await api.del(`/v1/devices/${deviceId}`)
              await mutate()
            }}
          />
        ) : (
          <RowsSkeleton />
        )}
        {/* Below the list, not above it: a parent arrives to read numbers, and
            connecting another phone is the rarer errand. It does not wait for
            `data` — minting a code has nothing to do with today's figures, and
            hiding it behind a failed usage fetch is how a family ends up unable
            to enrol the phone that would fix the gap. */}
        <div className="mt-6 border-t pt-6" style={{ borderColor: 'var(--hairline)' }}>
          <PairDevice childId={childId} />
        </div>
      </section>

      <section className="card p-6">
        <PurgeData
          childId={childId}
          // Both keys, and the strip first: the fortnight above is what shows
          // whether the delete actually landed.
          onPurged={async () => {
            await refreshStrip()
            await mutate()
          }}
        />
      </section>
    </div>
  )
}
