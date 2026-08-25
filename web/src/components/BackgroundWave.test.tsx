import { describe, expect, it } from 'vitest'
import { FULL_SCALE_MS, backgroundHours, wavePath } from './BackgroundWave'

/**
 * Every number in the path is part of an x,y pair (M takes one point, each C
 * takes three), so the y coordinates are the odd indices. Reading them
 * positionally rather than by regex is what keeps these assertions about the
 * shape rather than about the formatting.
 */
const ys = (path: string) =>
  [...path.matchAll(/-?\d+(?:\.\d+)?/g)]
    .map((match) => Number(match[0]))
    .filter((_, index) => index % 2 === 1)

/**
 * The y of hour `hour`'s own point: M contributes hour 0, then each C
 * contributes two control points and the next hour's endpoint.
 */
const hourY = (path: string, hour: number) => ys(path)[hour === 0 ? 0 : hour * 3]

const series = (points: { start: string; background_ms: number }[]) => [
  {
    package: 'com.abs',
    label: 'Audiobookshelf',
    points: points.map((point) => ({ ...point, foreground_ms: 0, launch_count: 0 })),
  },
]

describe('backgroundHours', () => {
  it('places an hour by its local clock hour', () => {
    const hours = backgroundHours(series([{ start: '2026-08-20T22:00:00+02:00', background_ms: 1_800_000 }]))
    expect(hours[22]).toBe(1_800_000)
    expect(hours).toHaveLength(24)
  })

  it('ignores an unparseable timestamp instead of painting it at midnight', () => {
    // The same trap hoursFromTotals guards: a blind slice invents 00:00 usage.
    const hours = backgroundHours(series([{ start: 'not a timestamp', background_ms: 999 }]))
    expect(hours.every((ms) => ms === 0)).toBe(true)
  })

  it('sums two apps in the same hour', () => {
    const at = (ms: number) => [{ start: '2026-08-20T21:00:00+02:00', foreground_ms: 0, launch_count: 0, background_ms: ms }]
    const hours = backgroundHours([
      { package: 'a', label: 'A', points: at(600_000) },
      { package: 'b', label: 'B', points: at(300_000) },
    ])
    expect(hours[21]).toBe(900_000)
  })
})

describe('wavePath', () => {
  it('is flat at the baseline for a silent day', () => {
    const path = wavePath(new Array(24).fill(0), 240, 40)
    expect(path).not.toContain('NaN')
    expect(new Set(ys(path).map((y) => Math.round(y)))).toEqual(new Set([40]))
  })

  it('clamps an hour above full scale instead of overflowing the box', () => {
    const samples = new Array(24).fill(0)
    samples[3] = FULL_SCALE_MS * 4
    expect(Math.min(...ys(wavePath(samples, 240, 40)))).toBeGreaterThanOrEqual(0)
  })

  it('is a fixed scale, so two days are comparable', () => {
    // A day-relative scale would draw ten quiet minutes exactly like a full
    // hour, and the whole point of the lane is comparing one day to the next.
    const quiet = new Array(24).fill(0)
    quiet[5] = 600_000
    const loud = new Array(24).fill(0)
    loud[5] = 600_000
    loud[6] = FULL_SCALE_MS
    expect(hourY(wavePath(quiet, 240, 40), 5)).toBe(hourY(wavePath(loud, 240, 40), 5))
  })

  it('produces no NaN for a single non-zero sample', () => {
    const samples = new Array(24).fill(0)
    samples[0] = 1
    expect(wavePath(samples, 240, 40)).not.toContain('NaN')
  })

  it('never dips below the baseline after a spike', () => {
    // An ordinary spline overshoots and draws listening into an hour that had
    // none. Every y stays inside the box.
    const samples = new Array(24).fill(0)
    samples[10] = FULL_SCALE_MS
    expect(Math.max(...ys(wavePath(samples, 240, 40)))).toBeLessThanOrEqual(40)
  })
})
