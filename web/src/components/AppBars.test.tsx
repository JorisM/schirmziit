import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AppBars, foldApps } from './AppBars'
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
