/**
 * Placeholders shaped like the content that is coming, never a spinner over the
 * layout. Same element counts and same heights as the real components, so the
 * page does not reflow when data lands — the crossfade is the only change.
 */
// A plain class, not a Tailwind arbitrary-value utility: the reduced-motion
// carve-out in index.css targets it by name, the same way it targets
// .wave-idle — an arbitrary-value class has no stable selector to hook.
const shimmer = 'skeleton-pulse'

function Frame({ children }: { children: React.ReactNode }) {
  return (
    <div role="status" aria-busy="true" className={shimmer}>
      {children}
    </div>
  )
}

export function StripSkeleton() {
  return (
    <Frame>
      <div className="flex items-end gap-1">
        {Array.from({ length: 14 }, (_, index) => (
          <span
            key={index}
            data-skeleton-bar
            className="flex-1 rounded-[3px]"
            style={{ height: 36, background: 'var(--hairline)' }}
          />
        ))}
      </div>
    </Frame>
  )
}

export function RibbonSkeleton() {
  return (
    <Frame>
      <div className="grid grid-cols-24 gap-[2px]">
        {Array.from({ length: 24 }, (_, index) => (
          <span
            key={index}
            data-skeleton-cell
            className="rounded-[4px]"
            style={{ height: 56, background: 'var(--ribbon-0)' }}
          />
        ))}
      </div>
    </Frame>
  )
}

export function HeroSkeleton() {
  // Same shape as the loaded hero: one big number on the left, a row of three
  // small stats on the right. A wrong shape (e.g. RowsSkeleton) reflows the
  // layout the instant data lands, which is the one thing a skeleton exists
  // to prevent.
  return (
    <Frame>
      <div className="flex w-full flex-wrap items-end justify-between gap-6">
        <div className="flex flex-col gap-2">
          <span className="h-3 w-24 rounded-[4px]" style={{ background: 'var(--hairline)' }} />
          <span className="h-12 w-36 rounded-[6px]" style={{ background: 'var(--hairline)' }} />
        </div>
        <div className="flex gap-8">
          {Array.from({ length: 3 }, (_, index) => (
            <div key={index} className="flex flex-col gap-2">
              <span className="h-3 w-14 rounded-[4px]" style={{ background: 'var(--hairline)' }} />
              <span className="h-5 w-10 rounded-[4px]" style={{ background: 'var(--hairline)' }} />
            </div>
          ))}
        </div>
      </div>
    </Frame>
  )
}

export function WeekSkeleton() {
  // Two figures side by side and two mover rows under them — the shape the
  // card settles into, so the page below it does not jump when the week lands.
  return (
    <Frame>
      <div className="flex flex-col gap-4">
        <span className="h-4 w-28 rounded-[4px]" style={{ background: 'var(--hairline)' }} />
        <div className="flex gap-8">
          {Array.from({ length: 2 }, (_, index) => (
            <div key={index} className="flex flex-col gap-2">
              <span className="h-3 w-20 rounded-[4px]" style={{ background: 'var(--hairline)' }} />
              <span className="h-7 w-24 rounded-[6px]" style={{ background: 'var(--hairline)' }} />
              <span className="h-3 w-28 rounded-[4px]" style={{ background: 'var(--hairline)' }} />
            </div>
          ))}
        </div>
        <div className="flex flex-col gap-2">
          {['72%', '58%'].map((width) => (
            <span
              key={width}
              className="h-3 rounded-[4px]"
              style={{ width, background: 'var(--hairline)' }}
            />
          ))}
        </div>
      </div>
    </Frame>
  )
}

export function RowsSkeleton() {
  // Four rows at decreasing widths: the real table is sorted biggest-first, so a
  // flat block would settle into a shape the eye did not expect.
  const widths = ['92%', '68%', '44%', '26%']
  return (
    <Frame>
      <div className="flex flex-col gap-3">
        {widths.map((width) => (
          <span
            key={width}
            data-skeleton-row
            className="h-3 rounded-[4px]"
            style={{ width, background: 'var(--hairline)' }}
          />
        ))}
      </div>
    </Frame>
  )
}
