import type { components } from '../api/schema'

type Matrix = components['schemas']['QrCode']

/**
 * Draws the matrix the server sent. Nothing here encodes anything: the one
 * encoder lives in `crates/core`, so the dashboard, both phones and a
 * self-hoster's browser draw the identical square.
 *
 * Dark on light in both themes, deliberately. A camera finds a code by its
 * contrast and expects dark modules on a light ground; an inverted QR is
 * refused outright by some scanners and read slowly by the rest. A pairing
 * square that looks at home in dark mode and will not scan is worse than one
 * that looks like a sticker.
 */
export function QrCode({ matrix, label }: { matrix: Matrix; label: string }) {
  const { size, rows } = matrix

  // One path rather than ~500 rects: the same pixels, a hundredth of the DOM.
  const modules = rows
    .flatMap((row, y) =>
      [...row].map((module, x) => (module === '1' ? `M${x} ${y}h1v1h-1z` : '')),
    )
    .join('')

  return (
    <svg
      role="img"
      aria-label={label}
      viewBox={`0 0 ${size} ${size}`}
      // Capped, not fluid: past about 240px a QR gains nothing a camera can
      // use, and a square the width of a desktop card reads as a decoration.
      className="animate-[fade-in_var(--motion-base)_var(--ease-out)] h-auto w-full max-w-[240px] rounded-[8px]"
      // shape-rendering: without it, sub-pixel antialiasing greys the module
      // edges at small sizes and the code gets harder to read, not softer.
      shapeRendering="crispEdges"
    >
      {/* The quiet zone comes with the matrix, so this ground is all the
          margin the code needs. */}
      <rect width={size} height={size} fill="#ffffff" />
      <path d={modules} fill="#101014" />
    </svg>
  )
}
