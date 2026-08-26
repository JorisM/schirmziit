import { act, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SWRConfig } from 'swr'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Children } from './Children'
import { LocaleProvider, formatDuration, locales } from '../i18n'
import { api } from '../api/client'

const renderPage = () =>
  render(
    <MemoryRouter>
      <LocaleProvider>
        <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
          <Children />
        </SWRConfig>
      </LocaleProvider>
    </MemoryRouter>,
  )

// Same pattern as motion.test.ts: only rAF/performance are faked, so SWR's
// promise-microtask resolution and waitFor's own real-timer polling still run
// normally, while the count-up's animation frames advance deterministically
// under our control instead of racing real wall-clock frames — which is what
// made this test flake under CPU load.
beforeEach(() => vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'performance'] }))
afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('Children', () => {
  it('asks for the list in the local zone and shows each child today total', async () => {
    const get = vi.spyOn(api, 'get').mockResolvedValue([
      { id: 'a', display_name: 'Kid', today_ms: 3_600_000 },
    ])

    renderPage()

    await waitFor(() => expect(screen.getByText('Kid')).toBeTruthy())
    // The zone travels with the request: "today" is local, and the server
    // refuses the call without it.
    const [path] = get.mock.calls[0] ?? []
    expect(path).toContain('tz=')

    // Drive the count-up's rAF loop to completion ourselves rather than
    // waiting on real frames.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(600)
    })

    // The exact formatted total, not a substring match that would pass on
    // almost any render.
    const expected = formatDuration(3_600_000, locales.en)
    expect(screen.getByText(expected)).toBeTruthy()
  })
})
