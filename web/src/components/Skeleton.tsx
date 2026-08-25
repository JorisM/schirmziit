/**
 * Placeholders shaped like the content that is coming, never a spinner over the
 * layout. Same element counts and same heights as the real components, so the
 * page does not reflow when data lands — the crossfade is the only change.
 */
const shimmer = 'animate-[skeleton-pulse_var(--motion-slow)_ease-in-out_infinite]'

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
