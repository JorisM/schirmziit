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
    const { result } = renderHook(() => useCountUp(4_200))
    // Not "after an effect settles": an animation that starts and is then
    // cancelled is still motion, and the rule is that it never starts.
    expect(result.current).toBe(4_200)
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
