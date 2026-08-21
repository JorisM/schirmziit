import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DeviceStatus, formatDuration } from './DeviceStatus'

describe('DeviceStatus', () => {
  it('warns loudly when a device is stale', () => {
    render(
      <DeviceStatus
        devices={[
          {
            id: '1',
            label: "Kid's phone",
            last_seen_at: '2026-08-21T06:00:00Z',
            stale: true,
          },
        ]}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(/not reported/i)
    expect(screen.getByRole('alert')).toHaveTextContent("Kid's phone")
  })

  it('says nothing alarming when devices are healthy', () => {
    render(
      <DeviceStatus
        devices={[
          {
            id: '1',
            label: "Kid's phone",
            last_seen_at: '2026-08-21T12:00:00Z',
            stale: false,
          },
        ]}
      />,
    )
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('treats a never-synced device as stale', () => {
    render(
      <DeviceStatus
        devices={[{ id: '1', label: 'New phone', last_seen_at: null, stale: true }]}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(/never/i)
  })
})

describe('formatDuration', () => {
  it('reads like a human wrote it', () => {
    expect(formatDuration(600_000)).toBe('10m')
    expect(formatDuration(3_600_000)).toBe('1h')
    expect(formatDuration(5_400_000)).toBe('1h 30m')
  })
})
