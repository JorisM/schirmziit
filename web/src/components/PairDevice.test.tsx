import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PairDevice } from './PairDevice'
import { LocaleProvider, locales } from '../i18n'
import { api } from '../api/client'
import { clearErrorLog, fromProblem } from '../api/errors'

const enrollment = {
  code: 'K7MPQ2XY',
  expires_at: '2026-08-27T10:15:00Z',
  qr_payload: 'schirmziit://enroll?url=https://schirmziit.example.ch&code=K7MPQ2XY',
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

    await waitFor(() => expect(screen.getByText('K7MPQ2XY')).toBeTruthy())
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

    await waitFor(() => expect(screen.getByText('K7MPQ2XY')).toBeTruthy())
    // The deep link is a machine string. A parent reads eight characters aloud,
    // so the payload must never be what is put in front of them as "the code".
    expect(screen.queryByText(/schirmziit:\/\//)).toBeNull()
  })

  it('mints a second code on request', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue(enrollment as never)
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: locales.en.devices.pairCreateCode }))
    await waitFor(() => expect(screen.getByText('K7MPQ2XY')).toBeTruthy())
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
