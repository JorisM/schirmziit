import { useState } from 'react'
import { copyDetails, type AppError } from '../api/errors'
import { errorCopy, type ErrorCopyCode } from '../i18n/errors'
import { useI18n } from '../i18n'

type Placement = 'inline' | 'banner'

/**
 * Every error the dashboard shows, in one component.
 *
 * `inline` replaces the data that failed to load and takes the footprint its
 * skeleton had, so nothing jumps. `banner` sits above data that is already on
 * screen when a *refresh* failed — the numbers stay, the banner says they are
 * stale. Blanking a loaded chart because a poll failed is the same mistake as
 * losing a day, one layer up.
 *
 * Entry motion and press feedback, and no flourish: the flourish belongs to the
 * data. Animating a failure is the interface enjoying itself at the parent's
 * expense.
 */
export function ErrorPanel({
  error,
  onRetry,
  variant = 'inline',
}: {
  error: AppError
  onRetry?: () => void
  variant?: Placement
}) {
  const { t, locale } = useI18n()
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)

  // A code with no web copy still has to read as something: an Android-only
  // failure arriving here is a bug, and a blank panel would hide it.
  const entry = errorCopy[error.code as ErrorCopyCode] ?? errorCopy['SZ-E901']
  const copy = entry[locale]
  const urgent = entry.weight === 'urgent'

  async function copyToClipboard() {
    await navigator.clipboard.writeText(copyDetails(error))
    setCopied(true)
  }

  return (
    <div
      role="alert"
      data-weight={entry.weight}
      className={[
        'flex flex-col gap-2 animate-[rise-in_var(--motion-base)_var(--ease-out)_backwards]',
        variant === 'banner' ? 'card mb-4 border p-4' : 'p-1',
      ].join(' ')}
      style={{ borderColor: urgent ? 'var(--urgent)' : 'var(--hairline)' }}
    >
      <p className="font-medium" style={{ color: urgent ? 'var(--urgent)' : 'var(--ink)' }}>
        {copy.title}
      </p>
      <p className="text-sm" style={{ color: 'var(--ink-muted)' }}>
        {copy.action}
      </p>

      {onRetry && (
        <div>
          <button
            type="button"
            onClick={onRetry}
            className="rounded-[12px] px-3 py-1.5 text-sm font-medium transition-transform duration-[var(--motion-fast)] active:scale-[0.97]"
            style={{ background: 'var(--accent)', color: 'var(--card)' }}
          >
            {t.errorPanel.retry}
          </button>
        </div>
      )}

      <button
        type="button"
        aria-expanded={open}
        aria-controls="error-detail"
        aria-label={t.errorPanel.reference}
        onClick={() => setOpen((was) => !was)}
        data-error-reference
        className="self-start font-mono text-xs transition-transform duration-[var(--motion-fast)] active:scale-[0.97]"
        // Dimmed by token, never by opacity: this line has to survive being
        // photographed and re-compressed by a messenger.
        style={{ color: 'var(--ink-muted)' }}
      >
        {error.code} · {error.ref} {open ? '▴' : '▾'}
      </button>

      {open && (
        <div
          id="error-detail"
          className="flex flex-col gap-1 font-mono text-xs animate-[fade-in_var(--motion-fast)_var(--ease-out)_backwards]"
          style={{ color: 'var(--ink-faint)' }}
        >
          <span>{detailLine(error)}</span>
          <span>{error.at.toLocaleString(locale)}</span>
          <button
            type="button"
            onClick={copyToClipboard}
            className="self-start underline"
            style={{ color: 'var(--accent)' }}
          >
            {copied ? t.errorPanel.copied : t.errorPanel.copy}
          </button>
        </div>
      )}
    </div>
  )
}

function detailLine(error: AppError): string {
  const status = error.httpStatus ? ` → ${error.httpStatus}` : ''
  return error.endpoint ? `GET ${error.endpoint}${status}` : '—'
}
