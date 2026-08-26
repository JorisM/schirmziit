import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SWRConfig } from 'swr'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Children } from './Children'
import { LocaleProvider, formatDuration, locales } from '../i18n'
import { api } from '../api/client'
import { clearErrorLog, fromProblem, type ErrorCode } from '../api/errors'

/** A server failure as the client would build it, code and reference included. */
const problem = (code: ErrorCode, ref = '7f3a9c') =>
  fromProblem(
    { type: 't', title: 't', status: 500, detail: 'internal error', code, ref },
    { endpoint: '/v1/children', httpStatus: 500 },
  )

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
beforeEach(() => {
  clearErrorLog()
  vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'performance'] })
})
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

  it('adds a child and re-reads the list', async () => {
    vi.spyOn(api, 'get').mockResolvedValue([])
    const post = vi.spyOn(api, 'post').mockResolvedValue(undefined)

    renderPage()

    await waitFor(() => expect(screen.getByText(locales.en.children.empty)).toBeTruthy())
    await act(async () => {
      fireEvent.change(screen.getByLabelText(locales.en.children.add), {
        target: { value: 'Lena' },
      })
    })
    await act(async () => {
      screen.getByRole('button', { name: locales.en.children.add }).click()
    })

    expect(post).toHaveBeenCalledWith('/v1/children', { display_name: 'Lena' })
  })

  it('says why an add was refused instead of doing nothing visible', async () => {
    vi.spyOn(api, 'get').mockResolvedValue([])
    vi.spyOn(api, 'post').mockRejectedValue(problem('SZ-E301'))

    renderPage()

    await waitFor(() => expect(screen.getByText(locales.en.children.empty)).toBeTruthy())
    await act(async () => {
      fireEvent.change(screen.getByLabelText(locales.en.children.add), {
        target: { value: 'Lena' },
      })
    })
    await act(async () => {
      screen.getByRole('button', { name: locales.en.children.add }).click()
    })

    // The code and reference, not the server's English detail: a screenshot of
    // this has to be enough to say which occurrence failed.
    expect(screen.getByText(/SZ-E301 · 7f3a9c/)).toBeInTheDocument()
    expect(screen.queryByText(/internal error/)).toBeNull()
  })

  // Irreversible and one tap from a list the parent opens every day: the first
  // press must never be the one that deletes.
  it('never deletes on the first press — it asks first', async () => {
    vi.spyOn(api, 'get').mockResolvedValue([{ id: 'a', display_name: 'Kid', today_ms: 0 }])
    const del = vi.spyOn(api, 'del').mockResolvedValue(undefined)

    renderPage()

    await waitFor(() => expect(screen.getByText('Kid')).toBeTruthy())
    await act(async () => {
      screen.getByRole('button', { name: locales.en.children.remove }).click()
    })

    expect(del).not.toHaveBeenCalled()
    expect(screen.getByText(locales.en.children.removeBody)).toBeTruthy()

    await act(async () => {
      screen.getByRole('button', { name: locales.en.children.removeConfirm }).click()
    })
    expect(del).toHaveBeenCalledWith('/v1/children/a')
  })

  it('leaves the child alone when the confirmation is cancelled', async () => {
    vi.spyOn(api, 'get').mockResolvedValue([{ id: 'a', display_name: 'Kid', today_ms: 0 }])
    const del = vi.spyOn(api, 'del').mockResolvedValue(undefined)

    renderPage()

    await waitFor(() => expect(screen.getByText('Kid')).toBeTruthy())
    await act(async () => {
      screen.getByRole('button', { name: locales.en.children.remove }).click()
    })
    await act(async () => {
      screen.getByRole('button', { name: locales.en.app.cancel }).click()
    })

    expect(del).not.toHaveBeenCalled()
    expect(screen.queryByText(locales.en.children.removeBody)).toBeNull()
    expect(screen.getByText('Kid')).toBeTruthy()
  })

  it('says what went wrong and keeps the child on screen when the delete fails', async () => {
    vi.spyOn(api, 'get').mockResolvedValue([{ id: 'a', display_name: 'Kid', today_ms: 0 }])
    vi.spyOn(api, 'del').mockRejectedValue(problem('SZ-E901'))

    renderPage()

    await waitFor(() => expect(screen.getByText('Kid')).toBeTruthy())
    await act(async () => {
      screen.getByRole('button', { name: locales.en.children.remove }).click()
    })
    await act(async () => {
      screen.getByRole('button', { name: locales.en.children.removeConfirm }).click()
    })

    // The failure with its code, the question still open, and the child still
    // listed: a failed delete that silently removes the row from the screen, or
    // closes the confirmation, is a lie about what happened.
    expect(screen.getByText(/SZ-E901 · 7f3a9c/)).toBeInTheDocument()
    expect(screen.getByText(locales.en.children.removeBody)).toBeTruthy()
    expect(screen.getByText('Kid')).toBeTruthy()
  })
})
