import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AppBars, foldApps, splitApps } from './AppBars'
import { LocaleProvider } from '../i18n'

const point = (ms: number, launches = 1) => ({
  start: '2026-08-21T15:00:00+02:00',
  foreground_ms: ms,
  launch_count: launches,
})

const series = (count: number) =>
  Array.from({ length: count }, (_, index) => ({
    package: `com.app${index}`,
    label: `App ${index}`,
    points: [point((count - index) * 60_000)],
  }))

describe('foldApps', () => {
  it('ranks apps by time', () => {
    const apps = foldApps(series(3))
    expect(apps.map((a) => a.label)).toEqual(['App 0', 'App 1', 'App 2'])
  })

  it('folds the tail into one row rather than inventing a seventh colour', () => {
    const apps = foldApps(series(10))
    expect(apps).toHaveLength(6)
    expect(apps[5]?.package).toBe('__other__')
  })

  it('keeps the folded total honest', () => {
    const apps = foldApps(series(10))
    const total = apps.reduce((sum, app) => sum + app.ms, 0)
    const expected = series(10)
      .flatMap((s) => s.points)
      .reduce((sum, p) => sum + p.foreground_ms, 0)
    expect(total).toBe(expected)
  })

  it('sums launches across an app’s hours', () => {
    const apps = foldApps([
      { package: 'com.a', label: 'A', points: [point(60_000, 3), point(60_000, 4)] },
    ])
    expect(apps[0]?.launches).toBe(7)
  })
})

describe('AppBars', () => {
  it('labels every value, so colour is never the only signal', () => {
    render(
      <LocaleProvider>
        <AppBars series={series(2)} />
      </LocaleProvider>,
    )
    expect(screen.getByText('App 0')).toBeInTheDocument()
    expect(screen.getByText('2 min')).toBeInTheDocument()
    expect(screen.getByText('1 min')).toBeInTheDocument()
  })

  it('renders nothing when there is nothing to rank', () => {
    const { container } = render(
      <LocaleProvider>
        <AppBars series={[]} />
      </LocaleProvider>,
    )
    expect(container).toBeEmptyDOMElement()
  })
})

describe('splitApps', () => {
  const app = (label: string, ms: number) => ({ package: label, label, ms, launches: 1 })

  it('separates the apps under a minute from the ones above it', () => {
    const { shown, brief } = splitApps([app('A', 3_600_000), app('B', 45_000), app('C', 60_000)])
    expect(shown.map((a) => a.label)).toEqual(['A', 'C'])
    expect(brief.map((a) => a.label)).toEqual(['B'])
  })

  it('drops an app that rounds to zero seconds', () => {
    // A row reading "0 s" carries nothing; it is the one thing worth hiding.
    const { shown, brief } = splitApps([app('A', 3_600_000), app('Blink', 300)])
    expect(shown.map((a) => a.label)).toEqual(['A'])
    expect(brief).toHaveLength(0)
  })

  it('keeps an app that rounds to one second', () => {
    const { brief } = splitApps([app('Blink', 900)])
    expect(brief.map((a) => a.label)).toEqual(['Blink'])
  })
})

describe('splitApps with the tail fold', () => {
  // foldApps only ever sees `shown` (never `brief`), so a sub-minute app can't
  // land inside the __other__ row's sum as well as its own brief row.
  it('never counts a sub-minute app in both folds', () => {
    const many = Array.from({ length: 10 }, (_, i) => ({
      package: `com.app${i}`,
      label: `App ${i}`,
      ms: (10 - i) * 60_000,
      launches: 1,
    }))
    const brief = { package: 'com.brief', label: 'Brief', ms: 20_000, launches: 1 }
    const { shown, brief: folded } = splitApps([...many, brief])

    expect(folded.map((a) => a.label)).toEqual(['Brief'])

    // Refold `shown` exactly as AppBars does: round-trip it through the series
    // shape foldApps expects, since foldApps is not being asked to change.
    const other = foldApps(
      shown.map((a) => ({
        package: a.package,
        label: a.label,
        points: [{ start: '2026-08-20', foreground_ms: a.ms, launch_count: a.launches }],
      })),
    ).find((a) => a.package === '__other__')

    const expectedOtherTotal = shown.slice(5).reduce((sum, a) => sum + a.ms, 0)
    expect(other?.ms).toBe(expectedOtherTotal)
    // The brief app's time must not have leaked into the folded tail.
    expect(other?.ms).not.toBe(expectedOtherTotal + brief.ms)
  })
})
