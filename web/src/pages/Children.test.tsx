import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SWRConfig } from 'swr'
import { afterEach, describe, expect, it, vi } from 'vitest'
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

afterEach(() => vi.restoreAllMocks())

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
    // The exact formatted total, not the brief's `/1/` — that regex would match
    // almost any render. jsdom's default locale resolves to `en`, and vitest
    // runs with no rAF loop, so a `waitFor` on this string only ever succeeds
    // once the count-up has settled on the real target.
    const expected = formatDuration(3_600_000, locales.en)
    await waitFor(() => expect(screen.getByText(expected)).toBeTruthy())
  })
})
