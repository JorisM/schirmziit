import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DayRibbon, hoursFromTotals, rampStep } from './DayRibbon'
import { LocaleProvider } from '../i18n'

describe('hoursFromTotals', () => {
  it('places each total in its local hour', () => {
    const hours = hoursFromTotals([
      { start: '2026-08-21T15:00:00+02:00', screen_on_ms: 600_000, unlock_count: 2, background_measured: false },
      { start: '2026-08-21T23:00:00+02:00', screen_on_ms: 300_000, unlock_count: 1, background_measured: false },
    ])
    expect(hours[15]).toBe(600_000)
    expect(hours[23]).toBe(300_000)
    expect(hours).toHaveLength(24)
  })

  it('sums two devices in the same hour', () => {
    const hours = hoursFromTotals([
      { start: '2026-08-21T08:00:00+02:00', screen_on_ms: 100, unlock_count: 0, background_measured: false },
      { start: '2026-08-21T08:00:00+02:00', screen_on_ms: 200, unlock_count: 0, background_measured: false },
    ])
    expect(hours[8]).toBe(300)
  })

  it('ignores an unparseable timestamp instead of shifting the day', () => {
    expect(hoursFromTotals([{ start: 'nonsense', screen_on_ms: 999, unlock_count: 0, background_measured: false }])
      .every((ms) => ms === 0)).toBe(true)
  })
})

describe('rampStep', () => {
  it('an empty hour is the lightest step', () => {
    expect(rampStep(0, 3_600_000)).toBe(0)
  })

  it('the busiest hour is the darkest step', () => {
    expect(rampStep(3_600_000, 3_600_000)).toBe(5)
  })

  it('scales relative to the busiest hour, not to a fixed hour', () => {
    // 30 min against a 1 h peak reads mid-ramp; against a 30 min peak it is the top.
    expect(rampStep(1_800_000, 3_600_000)).toBe(3)
    expect(rampStep(1_800_000, 1_800_000)).toBe(5)
  })

  it('does not divide by zero on a day with no usage', () => {
    expect(rampStep(0, 0)).toBe(0)
  })
})

describe('DayRibbon', () => {
  it('renders 24 hour cells with readable titles', () => {
    render(
      <LocaleProvider>
        <DayRibbon totals={[{ start: '2026-08-21T15:00:00+02:00', screen_on_ms: 1_800_000, unlock_count: 3, background_measured: false }]} />
      </LocaleProvider>,
    )
    expect(screen.getByTitle('15:00 — 30 min')).toBeInTheDocument()
    // Night hours say so, so 03:00 with nothing on it reads as an hour that
    // happened rather than missing data.
    expect(screen.getByTitle('03:00 (night) — 0 min')).toBeInTheDocument()
    expect(screen.getByTitle('12:00 — 0 min')).toBeInTheDocument()
  })
})

describe('the ribbon and the wave share one screen', () => {
  it('no longer owns the screen flourish', () => {
    // One flourish per screen. The wave took it, so the ribbon enters plainly:
    // two things performing at once and both lose.
    render(
      <LocaleProvider>
        <DayRibbon totals={[{ start: '2026-08-21T15:00:00+02:00', screen_on_ms: 1_800_000, unlock_count: 3, background_measured: true }]} />
      </LocaleProvider>,
    )
    const cells = [...document.querySelectorAll('[data-ribbon-cell]')]
    expect(cells).toHaveLength(24)
    expect(cells.every((cell) => !cell.className.includes('animate-'))).toBe(true)
    expect(cells.every((cell) => !(cell as HTMLElement).style.animationDelay)).toBe(true)
  })
})

describe('hovering an hour must not move the ribbon', () => {
  // The readout used to take the help text's place in the same flex row:
  // hovering shrank the caption by two lines, the ribbon slid up out from under
  // the cursor, mouseleave fired, the help text grew back, the cell returned
  // under the cursor — and the page flickered as fast as the browser could
  // relayout. Both lines keep their space now, hovered or not.
  const ribbon = (
    <LocaleProvider>
      <DayRibbon totals={[{ start: '2026-08-21T15:00:00+02:00', screen_on_ms: 1_800_000, unlock_count: 3, background_measured: false }]} />
    </LocaleProvider>
  )
  const help = 'Each cell is one hour, midnight to midnight. Darker means the screen was on longer. So you see not just how much, but when.'

  it('keeps the help text while an hour is hovered', () => {
    render(ribbon)
    const cell = document.querySelectorAll('[data-ribbon-cell]')[15] as HTMLElement
    fireEvent.mouseEnter(cell)
    expect(screen.getByText('15:00 — 30 min')).toBeInTheDocument()
    expect(screen.getByText(help)).toBeInTheDocument()
  })

  it('reserves the readout line before anything is hovered', () => {
    render(ribbon)
    const readout = document.querySelector('[data-ribbon-readout]') as HTMLElement
    // Present and holding a line's height, so filling it adds nothing.
    expect(readout).not.toBeNull()
    expect(readout.textContent).toBe('\u00a0')
  })

  it('lifts the cell without moving its own hit area', () => {
    render(ribbon)
    const cell = document.querySelectorAll('[data-ribbon-cell]')[15] as HTMLElement
    fireEvent.mouseEnter(cell)
    // The lift lives on an inert child: a transform on the cell itself moves the
    // hit box too, so a cursor near the bottom edge falls out of it and back in.
    expect(cell.style.transform).toBe('')
    const lift = cell.querySelector('[data-ribbon-lift]') as HTMLElement
    expect(lift.style.transform).toBe('translateY(-2px)')
    expect(lift.style.pointerEvents).toBe('none')
  })
})
