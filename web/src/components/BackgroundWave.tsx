import type { components } from '../api/schema'

type UsageResponse = components['schemas']['UsageResponse']

/**
 * One hour of background listening fills the lane. Fixed, not day-relative: a
 * scale that adapted to each day would draw ten quiet minutes exactly like a
 * full hour, and comparing one day to the next is the point of the lane.
 */
export const FULL_SCALE_MS = 3_600_000

const LOCAL_HOUR = /^\d{4}-\d{2}-\d{2}T(\d{2}):/

/** Background milliseconds per local hour, 0..23, summed across apps. */
export function backgroundHours(series: UsageResponse['series']): number[] {
  const perHour = new Array<number>(24).fill(0)
  for (const entry of series) {
    for (const point of entry.points) {
      // Matched strictly: slicing blindly turns any unparseable value into
      // hour 0, which paints phantom midnight listening.
      const match = LOCAL_HOUR.exec(point.start)
      if (!match) continue
      const hour = Number(match[1])
      if (hour >= 0 && hour < 24) perHour[hour] = (perHour[hour] ?? 0) + point.background_ms
    }
  }
  return perHour
}

/**
 * A smooth line through the 24 hour midpoints, drawn with horizontally
 * symmetric control points so the curve never overshoots past a sample: an
 * ordinary spline dips below the baseline after a spike and draws listening
 * into an hour that had none.
 */
export function wavePath(samples: number[], width: number, height: number): string {
  if (samples.length === 0) return ''
  const step = width / samples.length
  const points = samples.map((ms, index) => {
    const share = Math.min(Math.max(ms, 0), FULL_SCALE_MS) / FULL_SCALE_MS
    return { x: step * (index + 0.5), y: height - share * height }
  })

  const first = points[0]!
  let path = `M ${first.x} ${first.y}`
  for (let index = 1; index < points.length; index += 1) {
    const previous = points[index - 1]!
    const current = points[index]!
    const midX = (previous.x + current.x) / 2
    path += ` C ${midX} ${previous.y}, ${midX} ${current.y}, ${current.x} ${current.y}`
  }
  return path
}
