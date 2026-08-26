import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RibbonSkeleton, RowsSkeleton, StripSkeleton } from './Skeleton'

describe('skeletons', () => {
  it('is shaped like the strip it stands in for', () => {
    const { container } = render(<StripSkeleton />)
    // Fourteen, because a skeleton with a different count reflows the page when
    // the real data lands — which is the flicker a skeleton exists to remove.
    expect(container.querySelectorAll('[data-skeleton-bar]')).toHaveLength(14)
  })

  it('is shaped like the ribbon it stands in for', () => {
    const { container } = render(<RibbonSkeleton />)
    expect(container.querySelectorAll('[data-skeleton-cell]')).toHaveLength(24)
  })

  it('announces itself as busy rather than as content', () => {
    render(<RowsSkeleton />)
    expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true')
  })
})
