import { act, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { fromProblem } from '../api/errors'
import { DeviceStatus } from './DeviceStatus'
import { LocaleProvider, locales } from '../i18n'

const device = (over: Partial<Parameters<typeof DeviceStatus>[0]['devices'][number]> = {}) => ({
  id: 'd1',
  label: "Kid's phone",
  last_seen_at: '2026-08-21T12:00:00Z',
  stale: false,
  ...over,
})

describe('DeviceStatus', () => {
  it('explains a stale device instead of leaving a low number unexplained', () => {
    render(
      <LocaleProvider>
        <DeviceStatus devices={[device({ stale: true })]} />
      </LocaleProvider>,
    )
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('stays quiet when everything is reporting', () => {
    render(
      <LocaleProvider>
        <DeviceStatus devices={[device()]} />
      </LocaleProvider>,
    )
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('says so when a device has never reported', () => {
    render(
      <LocaleProvider>
        <DeviceStatus devices={[device({ last_seen_at: null, stale: true })]} />
      </LocaleProvider>,
    )
    // English fallback in tests: navigator.language is en-US under jsdom.
    expect(screen.getByText(/never reported/i)).toBeInTheDocument()
  })

  it('renders nothing without devices', () => {
    const { container } = render(
      <LocaleProvider>
        <DeviceStatus devices={[]} />
      </LocaleProvider>,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('offers no disconnect at all when the screen cannot handle one', () => {
    // The child's own screen renders this same component. A control that posts
    // nothing is worse than no control, so the affordance follows the handler.
    render(
      <LocaleProvider>
        <DeviceStatus devices={[device()]} />
      </LocaleProvider>,
    )
    expect(screen.queryByRole('button', { name: locales.en.devices.revoke })).toBeNull()
  })

  it('asks before disconnecting a phone, and only then calls back', async () => {
    const onRevoke = vi.fn().mockResolvedValue(undefined)
    render(
      <LocaleProvider>
        <DeviceStatus devices={[device()]} onRevoke={onRevoke} />
      </LocaleProvider>,
    )

    await act(async () => {
      screen.getByRole('button', { name: locales.en.devices.revoke }).click()
    })
    expect(onRevoke).not.toHaveBeenCalled()
    expect(screen.getByText(locales.en.devices.revokeBody)).toBeInTheDocument()

    await act(async () => {
      screen.getByRole('button', { name: locales.en.devices.revokeConfirm }).click()
    })
    expect(onRevoke).toHaveBeenCalledWith('d1')
  })

  it('keeps the phone connected when the confirmation is cancelled', async () => {
    const onRevoke = vi.fn().mockResolvedValue(undefined)
    render(
      <LocaleProvider>
        <DeviceStatus devices={[device()]} onRevoke={onRevoke} />
      </LocaleProvider>,
    )

    await act(async () => {
      screen.getByRole('button', { name: locales.en.devices.revoke }).click()
    })
    await act(async () => {
      screen.getByRole('button', { name: locales.en.app.cancel }).click()
    })

    expect(onRevoke).not.toHaveBeenCalled()
    expect(screen.queryByText(locales.en.devices.revokeBody)).toBeNull()
  })

  it('says what went wrong and keeps the row when the disconnect fails', async () => {
    const onRevoke = vi.fn().mockRejectedValue(
      fromProblem(
        {
          type: 't',
          title: 't',
          status: 500,
          detail: 'internal error',
          code: 'SZ-E901',
          ref: '7f3a9c',
        },
        { endpoint: '/v1/devices/d1', httpStatus: 500 },
      ),
    )
    render(
      <LocaleProvider>
        <DeviceStatus devices={[device()]} onRevoke={onRevoke} />
      </LocaleProvider>,
    )

    await act(async () => {
      screen.getByRole('button', { name: locales.en.devices.revoke }).click()
    })
    await act(async () => {
      screen.getByRole('button', { name: locales.en.devices.revokeConfirm }).click()
    })

    // The code and reference, never the server's English detail.
    expect(screen.getByText(/SZ-E901 · 7f3a9c/)).toBeInTheDocument()
    expect(screen.queryByText(/internal error/)).toBeNull()
    expect(screen.getByText("Kid's phone")).toBeInTheDocument()
  })
})
