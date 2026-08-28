import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { WeekInsight } from './WeekInsight'
import { LocaleProvider } from '../i18n'
import type { components } from '../api/schema'

type WeekComparison = components['schemas']['WeekComparison']

const minutes = (n: number) => n * 60_000

const week = (over: Partial<WeekComparison> = {}): WeekComparison => ({
  from: '2026-08-13',
  to: '2026-08-19',
  previous_from: '2026-08-06',
  previous_to: '2026-08-12',
  total_ms: minutes(600),
  previous_total_ms: minutes(560),
  evening_ms: minutes(120),
  previous_evening_ms: minutes(80),
  evening_from_hour: 21,
  movers: [],
  previous_measured: true,
  ...over,
})

const show = (comparison: WeekComparison) =>
  render(
    <LocaleProvider>
      <WeekInsight week={comparison} />
    </LocaleProvider>,
  )

describe('WeekInsight', () => {
  it('says how far the week moved, not only what it was', () => {
    show(week())
    expect(screen.getByTestId('week-total')).toHaveTextContent('10 h')
    expect(screen.getByTestId('week-total-delta')).toHaveTextContent('40 min')
  })

  it('names the direction in words, never by colour alone', () => {
    show(week({ total_ms: minutes(500), previous_total_ms: minutes(560) }))
    const delta = screen.getByTestId('week-total-delta')
    expect(delta).toHaveTextContent(/less/i)
    expect(delta).not.toHaveTextContent(/more/i)
  })

  it('compares evenings with evenings, and says which hour it means', () => {
    show(week())
    expect(screen.getByTestId('week-evening')).toHaveTextContent('2 h')
    expect(screen.getByTestId('week-evening-delta')).toHaveTextContent('40 min')
    expect(screen.getByTestId('week-evening')).toHaveTextContent('21')
  })

  it('says nothing changed rather than printing a zero as a rise', () => {
    show(week({ total_ms: minutes(600), previous_total_ms: minutes(600) }))
    expect(screen.getByTestId('week-total-delta')).toHaveTextContent(/same|unchanged/i)
  })

  it('is a first week rather than a doubling when nothing was measured before', () => {
    show(week({ previous_measured: false, previous_total_ms: 0, previous_evening_ms: 0 }))
    expect(screen.getByTestId('week-first')).toBeInTheDocument()
    // A comparison against silence is not a comparison: no delta at all.
    expect(screen.queryByTestId('week-total-delta')).not.toBeInTheDocument()
    // The week itself is still worth reading.
    expect(screen.getByTestId('week-total')).toHaveTextContent('10 h')
  })

  it('lists the apps that moved, with both weeks behind each one', () => {
    show(
      week({
        movers: [
          { package: 'com.b', label: 'Games', foreground_ms: minutes(90), previous_foreground_ms: minutes(20) },
          { package: 'com.a', label: 'Video', foreground_ms: minutes(10), previous_foreground_ms: minutes(45) },
        ],
      }),
    )
    const movers = screen.getAllByTestId('week-mover')
    expect(movers).toHaveLength(2)
    expect(movers[0]).toHaveTextContent('Games')
    expect(movers[0]).toHaveTextContent('1 h 10 min')
    expect(movers[1]).toHaveTextContent('Video')
    expect(movers[1]).toHaveTextContent('35 min')
  })

  it('says no app moved instead of showing an empty list', () => {
    show(week({ movers: [] }))
    expect(screen.getByTestId('week-no-movers')).toBeInTheDocument()
  })

  it('shows the days it is talking about', () => {
    show(week())
    const range = screen.getByTestId('week-range')
    expect(range.textContent).toMatch(/13/)
    expect(range.textContent).toMatch(/19/)
  })
})
