import { useEffect, useRef, useState } from 'react'

/**
 * The one place web motion is driven by JavaScript. Everything else rides the
 * `--motion-*` tokens, which a single media query zeroes — a component cannot
 * forget the reduced-motion path because it gets it from the token.
 */
const prefersReducedMotion = () =>
  typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches

/** Counts from zero to `target`. Returns `target` immediately under reduced motion. */
export function useCountUp(target: number, durationMs = 600): number {
  // Seeded in the initialiser, not in an effect: an effect would render 0 for one
  // frame first, which is the animation the preference asks us not to run.
  const [value, setValue] = useState(() => (prefersReducedMotion() ? target : 0))
  const frame = useRef(0)

  useEffect(() => {
    if (prefersReducedMotion()) {
      setValue(target)
      return
    }
    const start = performance.now()
    const tick = (now: number) => {
      const elapsed = Math.min(1, (now - start) / durationMs)
      // easeOutCubic — the number's counterpart to --ease-out. Landing is
      // assigned, not interpolated, so the last frame is exactly the target.
      setValue(elapsed >= 1 ? target : Math.round(target * (1 - (1 - elapsed) ** 3)))
      if (elapsed < 1) frame.current = requestAnimationFrame(tick)
    }
    frame.current = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame.current)
  }, [target, durationMs])

  return value
}
