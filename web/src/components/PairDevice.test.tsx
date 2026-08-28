import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PairDevice } from './PairDevice'
import { LocaleProvider, locales } from '../i18n'
import { api } from '../api/client'
import { clearErrorLog, fromProblem } from '../api/errors'

const enrollment = {
  code: 'K7MPQ2',
  // The server's own window, measured from now. A fixed instant here passes
  // until the wall clock reaches it and then fails for everyone afterwards —
  // which is exactly what this fixture did, silently, an afternoon after it
  // was written. The expired case below sets its own past instant on purpose.
  expires_at: new Date(Date.now() + 15 * 60_000).toISOString(),
  qr_payload: 'schirmziit://enroll?url=https://schirmziit.example.ch&code=K7MPQ2',
}

const renderPanel = () =>
  render(
    <LocaleProvider>
      <PairDevice childId="kid" />
    </LocaleProvider>,
  )

afterEach(() => {
  clearErrorLog()
  vi.restoreAllMocks()
})

describe('PairDevice', () => {
  it('mints no code until it is asked for', () => {
    const post = vi.spyOn(api, 'post')
    renderPanel()
    // A code minted on render would burn a one-shot code on every visit to the
    // page and expire unread fifteen minutes later.
    expect(post).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: locales.en.devices.pairCreateCode })).toBeTruthy()
  })

  it('asks the server for a code and shows the code, the address and the expiry', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue(enrollment as never)
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: locales.en.devices.pairCreateCode }))

    await waitFor(() => expect(screen.getByText('K7MPQ2')).toBeTruthy())
    expect(post).toHaveBeenCalledWith('/v1/children/kid/enrollments')
    // The address is the half of the pairing a wrong value breaks silently: the
    // phone enrols once against the wrong host and then never reports.
    expect(screen.getByText('https://schirmziit.example.ch')).toBeTruthy()
    expect(screen.getByText(new RegExp(locales.en.devices.codeExpires))).toBeTruthy()
  })

  it('shows the code as typed, without the scheme around it', async () => {
    vi.spyOn(api, 'post').mockResolvedValue(enrollment as never)
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: locales.en.devices.pairCreateCode }))

    await waitFor(() => expect(screen.getByText('K7MPQ2')).toBeTruthy())
    // The deep link is a machine string. A parent reads six characters aloud,
    // so the payload must never be what is put in front of them as "the code".
    expect(screen.queryByText(/schirmziit:\/\//)).toBeNull()
  })

  it('shows the square the server drew, beside the code it drew it from', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({
      ...enrollment,
      qr: { size: 3, rows: ['101', '010', '101'] },
    } as never)
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: locales.en.devices.pairCreateCode }))

    await waitFor(() => expect(screen.getByRole('img', { name: locales.en.devices.pairQrAlt })).toBeTruthy())
    // The code stays. A parent whose child's phone has no working camera, or
    // who is reading this over the phone, pairs by typing exactly as before.
    expect(screen.getByText('K7MPQ2')).toBeTruthy()
  })

  it('pairs by code alone when the server drew no square', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ ...enrollment, qr: null } as never)
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: locales.en.devices.pairCreateCode }))

    await waitFor(() => expect(screen.getByText('K7MPQ2')).toBeTruthy())
    // Not an empty frame where the square would be: a missing matrix is a
    // missing convenience, and a blank box reads as a broken page.
    expect(screen.queryByRole('img', { name: locales.en.devices.pairQrAlt })).toBeNull()
  })

  it('mints a second code on request', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue(enrollment as never)
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: locales.en.devices.pairCreateCode }))
    await waitFor(() => expect(screen.getByText('K7MPQ2')).toBeTruthy())
    await userEvent.click(screen.getByRole('button', { name: locales.en.devices.pairNewCode }))

    await waitFor(() => expect(post).toHaveBeenCalledTimes(2))
  })

  it('says a code is expired rather than showing it as usable', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({
      ...enrollment,
      expires_at: new Date(Date.now() - 60_000).toISOString(),
    } as never)
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: locales.en.devices.pairCreateCode }))

    // An expired code typed into a phone fails with an error the parent cannot
    // place. Saying so beside the code is the whole point of showing an expiry.
    await waitFor(() => expect(screen.getByText(locales.en.devices.pairExpired)).toBeTruthy())
  })

  it('shows the failure with its code and offers the button again', async () => {
    vi.spyOn(api, 'post').mockRejectedValue(
      fromProblem(
        { type: 't', title: 't', status: 500, detail: 'internal error', code: 'SZ-E901', ref: '7f3a9c' },
        { endpoint: '/v1/children/kid/enrollments', httpStatus: 500 },
      ),
    )
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: locales.en.devices.pairCreateCode }))

    await waitFor(() => expect(screen.getByText(/7f3a9c/)).toBeTruthy())
    expect(screen.getByRole('button', { name: locales.en.devices.pairCreateCode })).toBeTruthy()
  })
})
