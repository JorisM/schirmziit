import { useLayoutEffect, useRef, useState } from 'react'
import type { components } from '../api/schema'
import { formatDuration, useI18n } from '../i18n'

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

const WIDTH = 240
const HEIGHT = 40

/**
 * Background listening as a lane under the day ribbon, sharing its 24-hour
 * axis so a spike lines up with the hour above it.
 *
 * `measured` is not "was there any". A phone that cannot observe background
 * playback — an iPhone, or an Android phone whose family declined the grant —
 * gets a sentence saying so. Drawing it a flat line would tell the parent
 * nothing played, which is the one thing we do not know.
 */
export function BackgroundWave({
  series,
  measured,
}: {
  series: UsageResponse['series']
  measured: boolean
}) {
  const { t } = useI18n()
  const [hovered, setHovered] = useState<number | null>(null)
  const pathRef = useRef<SVGPathElement>(null)
  const [drawn, setDrawn] = useState(false)
  const [length, setLength] = useState(0)

  const perHour = measured ? backgroundHours(series) : new Array<number>(24).fill(0)
  const total = perHour.reduce((sum, ms) => sum + ms, 0)
  const d = wavePath(perHour, WIDTH, HEIGHT)

  useLayoutEffect(() => {
    const node = pathRef.current
    // getTotalLength is an SVG-layout API: jsdom has no layout, so it is
    // absent under test. A length of 0 leaves strokeDasharray unset and the
    // offset at 0, so the path simply appears whole — there is nothing to hide
    // and nothing to draw in.
    const total = node && typeof node.getTotalLength === 'function' ? node.getTotalLength() : 0
    setLength(total)
    setDrawn(false)
    const frame = requestAnimationFrame(() => setDrawn(true))
    return () => cancelAnimationFrame(frame)
  }, [d])

  if (!measured) {
    return (
      <figure className="m-0 mt-6">
        <figcaption className="mb-1 flex flex-wrap items-baseline justify-between gap-2">
          <h3 className="text-lg">{t.child.backgroundTitle}</h3>
        </figcaption>
        <div
          className="mt-2 border-t border-dashed"
          style={{ borderColor: 'var(--hairline)' }}
          aria-hidden="true"
        />
        <p className="mt-2 max-w-prose text-sm" style={{ color: 'var(--ink-muted)' }}>
          {t.child.backgroundNotMeasured}
        </p>
      </figure>
    )
  }

  return (
    <figure className="m-0 mt-6">
      {/*
       * The readout gets its own line, and the help text keeps its own. Sharing
       * one row meant hovering swapped a three-line help text for a short time:
       * the caption shrank, the wave slid up out from under the cursor,
       * mouseleave fired, the text came back — and the page flickered between
       * the two heights. Nothing a pointer hovers may change layout.
       */}
      <figcaption className="mb-1">
        <div className="flex items-baseline justify-between gap-4">
          <h3 className="text-lg">{t.child.backgroundTitle}</h3>
          <span
            data-wave-readout
            className="whitespace-nowrap text-sm tabular-nums"
            style={{ color: 'var(--ink-faint)' }}
            aria-live="polite"
          >
            {hovered === null
              ? '\u00a0'
              : `${String(hovered).padStart(2, '0')}:00 — ${formatDuration(perHour[hovered] ?? 0, t)}`}
          </span>
        </div>
        <p className="mt-1 text-sm" style={{ color: 'var(--ink-faint)' }}>
          {t.child.backgroundHelp}
        </p>
      </figcaption>

      <div className="relative" style={{ height: HEIGHT }}>
        <svg
          className="absolute inset-0 h-full w-full"
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          preserveAspectRatio="none"
          role="img"
          aria-label={`${t.child.backgroundTitle}: ${formatDuration(total, t)}`}
        >
          <path
            className={total > 0 ? 'motion-safe:wave-idle' : undefined}
            d={`${d} L ${WIDTH} ${HEIGHT} L 0 ${HEIGHT} Z`}
            fill="var(--background-wave)"
            opacity={0.18}
          />
          <path
            ref={pathRef}
            data-wave
            d={d}
            fill="none"
            stroke="var(--background-wave)"
            strokeWidth={1.5}
            strokeLinecap="round"
            vectorEffect="non-scaling-stroke"
            strokeDasharray={length || undefined}
            strokeDashoffset={drawn ? 0 : length}
            style={{ transition: 'stroke-dashoffset 600ms ease-out' }}
          />
        </svg>

        <div className="absolute inset-0 grid grid-cols-24">
          {perHour.map((ms, hour) => (
            <div
              key={hour}
              className="cursor-default"
              onMouseEnter={() => setHovered(hour)}
              onMouseLeave={() => setHovered(null)}
              title={`${String(hour).padStart(2, '0')}:00 — ${formatDuration(ms, t)}`}
            />
          ))}
        </div>
      </div>

      {total === 0 && (
        <p className="mt-2 text-sm" style={{ color: 'var(--ink-muted)' }}>
          {t.child.backgroundEmpty}
        </p>
      )}
    </figure>
  )
}
