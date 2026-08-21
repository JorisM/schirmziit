import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { SWRConfig } from 'swr'
import { ChildDetail } from './ChildDetail'

const usage = {
  child_id: 'c1',
  from: '2026-08-21',
  to: '2026-08-21',
  bucket: 'hour',
  tz: 'Europe/Zurich',
  devices: [
    { id: 'd1', label: "Kid's phone", last_seen_at: '2026-08-21T12:00:00Z', stale: false },
  ],
  series: [
    {
      package: 'com.tiktok',
      label: 'TikTok',
      points: [
        { start: '2026-08-21T15:00:00+02:00', foreground_ms: 1_800_000, launch_count: 12 },
      ],
    },
    {
      package: 'com.chrome',
      label: 'Chrome',
      points: [
        { start: '2026-08-21T16:00:00+02:00', foreground_ms: 600_000, launch_count: 3 },
      ],
    },
  ],
  device_totals: [
    { start: '2026-08-21T15:00:00+02:00', screen_on_ms: 2_400_000, unlock_count: 14 },
  ],
}

const server = setupServer(
  http.get('/v1/children/c1/usage', () => HttpResponse.json(usage)),
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

/// SWR's cache is module-global; without a fresh provider per render, data
/// fetched by one test satisfies the next one's hook and the error case never
/// reaches its error branch.
function renderIsolated(childId: string) {
  return render(
    <SWRConfig value={{ provider: () => new Map() }}>
      <ChildDetail childId={childId} />
    </SWRConfig>,
  )
}

describe('ChildDetail', () => {
  it('lists apps by time descending, formatted for humans', async () => {
    renderIsolated('c1')
    const rows = await screen.findAllByRole('row')
    // header + 2 apps
    expect(rows).toHaveLength(3)
    expect(rows[1]).toHaveTextContent('TikTok')
    expect(rows[1]).toHaveTextContent('30m')
    expect(rows[2]).toHaveTextContent('Chrome')
    expect(rows[2]).toHaveTextContent('10m')
  })

  it('shows total screen time and the unlock count', async () => {
    renderIsolated('c1')
    expect(await screen.findByText(/40m today · 14 unlocks/i)).toBeInTheDocument()
  })

  it('surfaces an API error instead of rendering an empty page', async () => {
    server.use(
      http.get('/v1/children/c1/usage', () =>
        HttpResponse.json(
          {
            type: 'https://nestling.dev/problems/not-found',
            title: 'not-found',
            status: 404,
            detail: 'not found',
          },
          { status: 404 },
        ),
      ),
    )
    renderIsolated('c1')
    expect(await screen.findByRole('alert')).toHaveTextContent('not found')
  })
})
