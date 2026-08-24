import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { SWRConfig } from 'swr'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ChildDetail } from './ChildDetail'
import { LocaleProvider, locales } from '../i18n'
import { api } from '../api/client'

const usage = (bucket: string, from: string, to: string) => ({
  child_id: 'kid', from, to, bucket, tz: 'Europe/Zurich',
  devices: [], device_totals: [],
  series: [{ package: 'com.a', label: 'A', points: [{ start: bucket === 'day' ? to : `${to}T10:00:00+02:00`, foreground_ms: 60_000, launch_count: 1 }] }],
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
})
