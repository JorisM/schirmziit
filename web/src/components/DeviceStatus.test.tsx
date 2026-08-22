import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DeviceStatus } from './DeviceStatus'
import { LocaleProvider } from '../i18n'

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
})
