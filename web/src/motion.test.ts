import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useCountUp } from './motion'

/** jsdom has no matchMedia; every test states which preference it is under. */
const setReducedMotion = (reduced: boolean) => {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: reduced && query.includes('prefers-reduced-motion'),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }))
}

describe('useCountUp', () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'performance'] }))
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('renders the final value on the FIRST frame under reduced motion', () => {
    setReducedMotion(true)
    const seen: number[] = []
    renderHook(() => {
      const value = useCountUp(4_200)
      seen.push(value)
      return value
    })
    // seen[0] is what the first render committed, before any effect ran.
    // `result.current` cannot prove this: React flushes the effect before the
    // assertion, so a hook that starts at 0 and corrects itself in an effect
    // passes while still painting one frame of motion the setting forbids.
    expect(seen[0]).toBe(4_200)
  })

  it('starts below the target and lands exactly on it', async () => {
    setReducedMotion(false)
    const { result } = renderHook(() => useCountUp(4_200))
    expect(result.current).toBe(0)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(600)
    })
    // Exactly the target, not "close to": a total that stops at 4_199 is wrong
    // on screen, and an eased curve is where that rounding error hides.
    expect(result.current).toBe(4_200)
  })
})
