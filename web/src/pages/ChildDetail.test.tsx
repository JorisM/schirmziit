import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { SWRConfig } from 'swr'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ChildDetail, localToday } from './ChildDetail'
import { LocaleProvider, locales } from '../i18n'
import { ApiError, api } from '../api/client'

const usage = (bucket: string, from: string, to: string) => ({
  child_id: 'kid', from, to, bucket, tz: 'Europe/Zurich',
  devices: [], device_totals: [],
  series: [{ package: 'com.a', label: 'A', points: [{ start: bucket === 'day' ? to : `${to}T10:00:00+02:00`, foreground_ms: 60_000, launch_count: 1, background_ms: 0 }] }],
})

// SWR's default cache is a module-level singleton. Both tests build the same
// key (same child, same "today"), so without an isolated cache and no
// deduping window, the second test's mount would be served from the first
// test's in-flight/just-fetched entry instead of issuing its own request.
const renderPage = () =>
  render(
    <MemoryRouter>
      <LocaleProvider>
        <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
          <ChildDetail childId="kid" />
        </SWRConfig>
      </LocaleProvider>
    </MemoryRouter>,
  )

afterEach(() => vi.restoreAllMocks())

describe('localToday', () => {
  afterEach(() => vi.unstubAllEnvs())

  it('answers the local calendar date, not UTC, just after Zurich midnight', () => {
    vi.stubEnv('TZ', 'Europe/Zurich')
    // 2026-08-24T22:30:00Z is 2026-08-25T00:30 in Zurich (CEST, UTC+2) — local
    // midnight has passed, but the UTC date has not rolled over yet.
    const now = new Date('2026-08-24T22:30:00Z')
    expect(localToday(now)).toBe('2026-08-25')
    // The bug this guards against, made explicit: the UTC answer for the same
    // instant is still "yesterday".
    expect(now.toISOString().slice(0, 10)).toBe('2026-08-24')
  })
})

describe('ChildDetail', () => {
  it('asks for fourteen days of totals and one day of hours', async () => {
    const get = vi.spyOn(api, 'get').mockImplementation(async (path: string) => {
      const url = new URL(path, 'http://x')
      return usage(url.searchParams.get('bucket')!, url.searchParams.get('from')!, url.searchParams.get('to')!) as never
    })

    renderPage()

    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
    const paths = get.mock.calls.map(([path]) => path as string)
    const strip = paths.find((p) => p.includes('bucket=day'))!
    const detail = paths.find((p) => p.includes('bucket=hour'))!
    const range = new URL(strip, 'http://x').searchParams
    const days =
      (Date.parse(`${range.get('to')}T00:00:00Z`) - Date.parse(`${range.get('from')}T00:00:00Z`)) / 86_400_000
    // from today − 13 to today is fourteen days inclusive
    expect(days).toBe(13)
    expect(new URL(detail, 'http://x').searchParams.get('from')).toBe(range.get('to'))
  })

  it('re-requests only the selected day when a bar is clicked', async () => {
    const get = vi.spyOn(api, 'get').mockImplementation(async (path: string) => {
      const url = new URL(path, 'http://x')
      return usage(url.searchParams.get('bucket')!, url.searchParams.get('from')!, url.searchParams.get('to')!) as never
    })

    renderPage()
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))

    await userEvent.click(screen.getAllByRole('button')[0]!)

    await waitFor(() => {
      const hourly = get.mock.calls.map(([p]) => p as string).filter((p) => p.includes('bucket=hour'))
      expect(hourly).toHaveLength(2)
      expect(hourly[1]).not.toBe(hourly[0])
    })
    const daily = get.mock.calls.map(([p]) => p as string).filter((p) => p.includes('bucket=day'))
    expect(daily, 'selecting a day must not re-fetch the strip').toHaveLength(1)
  })

  it('names the day, not "today", when an empty past day is selected', async () => {
    vi.spyOn(api, 'get').mockImplementation(async (path: string) => {
      const url = new URL(path, 'http://x')
      const bucket = url.searchParams.get('bucket')!
      const from = url.searchParams.get('from')!
      const to = url.searchParams.get('to')!
      // The strip has data (so the button under test is real and clickable),
      // but every hourly/detail response — today's included — is empty, so
      // only the day-aware copy distinguishes "today" from "this day".
      if (bucket === 'hour') {
        return { child_id: 'kid', from, to, bucket, tz: 'Europe/Zurich', devices: [], device_totals: [], series: [] } as never
      }
      return usage(bucket, from, to) as never
    })

    renderPage()
    await waitFor(() => expect(screen.getByText(locales.en.child.noDataToday)).toBeInTheDocument())

    // The strip's first button is the oldest day in range (today − 13) — never today.
    await userEvent.click(screen.getAllByRole('button')[0]!)

    await waitFor(() => expect(screen.getByText(locales.en.child.noDataDay)).toBeInTheDocument())
    expect(screen.queryByText(locales.en.child.noDataToday)).not.toBeInTheDocument()
  })

  it('keeps the strip on screen while a newly selected day loads', async () => {
    const user = userEvent.setup()
    let hourCalls = 0
    let resolveSecondDay: (value: unknown) => void = () => {}
    vi.spyOn(api, 'get').mockImplementation(async (path: string) => {
      const url = new URL(path, 'http://x')
      if (url.searchParams.get('bucket') === 'day') return usage('day', '2026-08-12', localToday())
      hourCalls += 1
      // First call (initial mount) resolves; the second (after the tap) is held
      // open so the loading state is observable after the click.
      if (hourCalls === 1) return usage('hour', localToday(), localToday())
      return new Promise((resolve) => {
        resolveSecondDay = resolve
      })
    })

    renderPage()
    await waitFor(() => expect(screen.getAllByRole('button').length).toBeGreaterThan(0))

    const bars = screen.getAllByRole('button')
    await user.click(bars[0]!)

    // The control the parent just tapped must not vanish under their finger.
    // Before this fix a single `if (!data)` guard blanked the entire page.
    expect(screen.getAllByRole('button').length).toBeGreaterThan(1)
    expect(screen.getAllByRole('status').length).toBeGreaterThan(0)

    // Let the held promise resolve so no unhandled state leaks into later tests.
    resolveSecondDay(usage('hour', localToday(), localToday()))
  })

  it('shows an error instead of a fortnight of zero bars when the strip request fails', async () => {
    vi.spyOn(api, 'get').mockImplementation(async (path: string) => {
      const url = new URL(path, 'http://x')
      const bucket = url.searchParams.get('bucket')!
      if (bucket === 'day')
        throw new ApiError({ type: 'about:blank', title: 'error', status: 502, detail: 'bad gateway' })
      return usage(bucket, url.searchParams.get('from')!, url.searchParams.get('to')!) as never
    })

    renderPage()

    await waitFor(() => expect(screen.getByText(locales.en.child.historyError)).toBeInTheDocument())
    // A fortnight of zero-height bars must never render in place of the error —
    // that is indistinguishable from a genuinely quiet fortnight.
    expect(screen.queryByText(locales.en.child.historyTitle)).not.toBeInTheDocument()
  })
})

describe('background listening', () => {
  const withBackground = (
    background_ms: number,
    background_measured: boolean,
    foreground_ms = 0,
  ) => {
    const body = (bucket: string, from: string, to: string) => ({
      child_id: 'kid',
      from,
      to,
      bucket,
      tz: 'Europe/Zurich',
      devices: [],
      device_totals: [
        {
          start: bucket === 'day' ? to : `${to}T22:00:00+02:00`,
          screen_on_ms: foreground_ms,
          unlock_count: 0,
          background_measured,
        },
      ],
      series: [
        {
          package: 'com.abs',
          label: 'Audiobookshelf',
          points: [
            {
              start: bucket === 'day' ? to : `${to}T22:00:00+02:00`,
              foreground_ms,
              launch_count: 0,
              background_ms,
            },
          ],
        },
      ],
    })
    vi.spyOn(api, 'get').mockImplementation(async (path: string) => {
      const url = new URL(path, 'http://x')
      return body(
        url.searchParams.get('bucket')!,
        url.searchParams.get('from')!,
        url.searchParams.get('to')!,
      ) as never
    })
  }

  it('shows background listening as its own total, never inside screen time', async () => {
    // A full hour of audiobook with the screen off must not move the
    // screen-time hero by a single minute.
    withBackground(3_600_000, true)
    renderPage()

    await waitFor(() =>
      expect(screen.getByText(locales.en.child.backgroundTotal)).toBeInTheDocument(),
    )
    expect(screen.getByText('0 min')).toBeInTheDocument()
    expect(screen.getByText('1 h')).toBeInTheDocument()
  })

  it('reports not measured when no device could observe it', async () => {
    withBackground(0, false, 60_000)
    renderPage()

    await waitFor(() =>
      expect(screen.getByText(locales.en.child.backgroundNotMeasured)).toBeInTheDocument(),
    )
    // No total either: there is no number to report when nothing measured it.
    expect(screen.queryByText(locales.en.child.backgroundTotal)).toBeNull()
  })
})
