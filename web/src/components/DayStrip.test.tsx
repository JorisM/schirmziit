import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DayStrip, dailyTotals } from './DayStrip'
import { LocaleProvider } from '../i18n'

const series = (points: { start: string; foreground_ms: number }[]) => [
  {
    package: 'com.a',
    label: 'A',
    points: points.map((p) => ({ ...p, launch_count: 1 })),
  },
]

describe('dailyTotals', () => {
  it('fills a day with no rows with a zero rather than skipping it', () => {
    const days = dailyTotals(
      series([
        { start: '2026-08-18', foreground_ms: 60_000 },
        { start: '2026-08-20', foreground_ms: 30_000 },
      ]),
      '2026-08-18',
      '2026-08-20',
    )
    expect(days).toEqual([
      { day: '2026-08-18', ms: 60_000 },
      { day: '2026-08-19', ms: 0 },
      { day: '2026-08-20', ms: 30_000 },
    ])
  })

  it('drops a point outside the range instead of folding it into the first day', () => {
    const days = dailyTotals(series([{ start: '2026-07-01', foreground_ms: 60_000 }]), '2026-08-18', '2026-08-20')
    expect(days.every((d) => d.ms === 0)).toBe(true)
  })

  it('sums every app for the same day', () => {
    const two = [
      ...series([{ start: '2026-08-18', foreground_ms: 60_000 }]),
      { package: 'com.b', label: 'B', points: [{ start: '2026-08-18', foreground_ms: 15_000, launch_count: 1 }] },
    ]
    expect(dailyTotals(two, '2026-08-18', '2026-08-18')).toEqual([{ day: '2026-08-18', ms: 75_000 }])
  })
})

describe('DayStrip', () => {
  it('reports the day that was clicked', async () => {
    const onSelect = vi.fn()
    render(
      <LocaleProvider>
        <DayStrip
          series={series([{ start: '2026-08-20', foreground_ms: 30_000 }])}
          from="2026-08-18"
          to="2026-08-20"
          selected="2026-08-20"
          onSelect={onSelect}
        />
      </LocaleProvider>,
    )
    const buttons = screen.getAllByRole('button')
    expect(buttons).toHaveLength(3) // one per day in the range
    await userEvent.click(buttons[0]!)
    expect(onSelect).toHaveBeenCalledWith('2026-08-18')
  })

  it('marks the selected day for a screen reader', () => {
    render(
      <LocaleProvider>
        <DayStrip series={[]} from="2026-08-18" to="2026-08-20" selected="2026-08-19" onSelect={() => {}} />
      </LocaleProvider>,
    )
    const pressed = screen.getAllByRole('button').filter((b) => b.getAttribute('aria-pressed') === 'true')
    expect(pressed).toHaveLength(1)
  })
})
