import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AppBars, foldTail, splitApps } from './AppBars'
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

const total = (label: string, ms: number, launches = 1) => ({
  package: label,
  label,
  ms,
  launches,
})

describe('foldTail', () => {
  it('leaves a short list alone', () => {
    const apps = [total('App 0', 3_000), total('App 1', 2_000), total('App 2', 1_000)]
    expect(foldTail(apps).map((a) => a.label)).toEqual(['App 0', 'App 1', 'App 2'])
  })

  it('folds the tail into one row rather than inventing a seventh colour', () => {
    const apps = Array.from({ length: 10 }, (_, i) => total(`App ${i}`, (10 - i) * 60_000))
    const folded = foldTail(apps)
    expect(folded).toHaveLength(6)
    expect(folded[5]?.package).toBe('__other__')
  })

  it('keeps the folded total honest', () => {
    const apps = Array.from({ length: 10 }, (_, i) => total(`App ${i}`, (10 - i) * 60_000))
    const folded = foldTail(apps)
    const foldedSum = folded.reduce((sum, app) => sum + app.ms, 0)
    const rawSum = apps.reduce((sum, app) => sum + app.ms, 0)
    expect(foldedSum).toBe(rawSum)
  })

  it('sums launches across the folded tail', () => {
    const apps = [
      total('App 0', 60_000, 3),
      total('App 1', 50_000, 4),
      total('App 2', 40_000, 1),
      total('App 3', 30_000, 1),
      total('App 4', 20_000, 1),
      total('App 5', 10_000, 2),
      total('App 6', 5_000, 5),
    ]
    const folded = foldTail(apps)
    expect(folded[5]?.launches, 'the folded row sums the launches it swallowed').toBe(7)
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
  // foldTail only ever sees `shown` (never `brief`), so a sub-minute app can't
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

    // Refold `shown` exactly as AppBars does.
    const other = foldTail(shown).find((a) => a.package === '__other__')

    const expectedOtherTotal = shown.slice(5).reduce((sum, a) => sum + a.ms, 0)
    expect(other?.ms).toBe(expectedOtherTotal)
    // The brief app's time must not have leaked into the folded tail.
    expect(other?.ms).not.toBe(expectedOtherTotal + brief.ms)
  })
})

describe('AppBars with both folds at once', () => {
  // Proves the wiring inside AppBars itself, not just that the two exported
  // functions compose correctly in isolation: renders a series that needs
  // BOTH the sub-minute split and the six-row tail fold at the same time, and
  // reads the result back out of the DOM.
  it('keeps the sub-minute app out of the __other__ row, at the DOM level', async () => {
    const bothFoldsSeries = [
      { package: 'com.a0', label: 'App0', points: [point(600_000)] },
      { package: 'com.a1', label: 'App1', points: [point(540_000)] },
      { package: 'com.a2', label: 'App2', points: [point(480_000)] },
      { package: 'com.a3', label: 'App3', points: [point(420_000)] },
      { package: 'com.a4', label: 'App4', points: [point(360_000)] },
      // These three fold into __other__: 300_000 + 240_000 + 75_000 = 615_000ms = "10 min".
      { package: 'com.a5', label: 'App5', points: [point(300_000)] },
      { package: 'com.a6', label: 'App6', points: [point(240_000)] },
      { package: 'com.a7', label: 'App7', points: [point(75_000)] },
      // Sub-minute: if it leaked into __other__'s sum, the total would round to
      // "11 min" instead of "10 min", and no disclosure button would exist.
      { package: 'com.brief', label: 'BriefApp', points: [point(45_000)] },
    ]

    render(
      <LocaleProvider>
        <AppBars series={bothFoldsSeries} />
      </LocaleProvider>,
    )

    // English fallback in tests: navigator.language is en-US under jsdom.
    expect(screen.getByTitle('Other apps — 10 min')).toBeInTheDocument()
    expect(screen.queryByTitle('Other apps — 11 min')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Apps under a minute (1)' })).toBeInTheDocument()
  })
})

describe('AppBars disclosure', () => {
  it('reveals the brief apps behind a real, labelled, keyboard-reachable control', async () => {
    const disclosureSeries = [
      { package: 'com.games.puzzle', label: 'Puzzle', points: [point(3_600_000)] },
      { package: 'com.utility.check', label: 'QuickCheck', points: [point(45_000)] },
      { package: 'com.weather', label: 'Weather', points: [point(20_000)] },
    ]

    render(
      <LocaleProvider>
        <AppBars series={disclosureSeries} />
      </LocaleProvider>,
    )

    // English fallback in tests: navigator.language is en-US under jsdom.
    const toggle = screen.getByRole('button', { name: 'Apps under a minute (2)' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('QuickCheck')).not.toBeInTheDocument()
    expect(screen.queryByText('Weather')).not.toBeInTheDocument()

    await userEvent.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('QuickCheck')).toBeInTheDocument()
    expect(screen.getByText('45 s')).toBeInTheDocument()
    expect(screen.getByText('Weather')).toBeInTheDocument()
    expect(screen.getByText('20 s')).toBeInTheDocument()
  })
})
