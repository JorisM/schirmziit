import { useState } from 'react'
import { AppError, unexpected } from '../api/errors'
import { ErrorPanel } from './ErrorPanel'
import { useI18n } from '../i18n'

/**
 * A two-press destructive control: the first press only asks.
 *
 * Removing a child and disconnecting a phone are irreversible, and both sit on
 * screens a parent opens daily — a single tap next to a name is one mis-tap away
 * from a deletion. The question is asked in place rather than in a modal so the
 * row it belongs to stays visible and named right above the sentence explaining
 * what will happen.
 *
 * A failed call keeps the question open and shows the failure with its code: a
 * confirmation that closes on failure reads as "done".
 */
export function DestructiveAction({
  label,
  body,
  confirmLabel,
  onConfirm,
}: {
  label: string
  body: string
  confirmLabel: string
  onConfirm: () => Promise<void>
}) {
  const { t } = useI18n()
  const [asking, setAsking] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<AppError | null>(null)

  if (!asking) {
    return (
      <button
        type="button"
        onClick={() => setAsking(true)}
        className="rounded-[10px] px-2 py-1 text-sm underline transition-transform duration-[var(--motion-fast)] ease-[var(--ease-out)] active:scale-95"
        style={{ color: 'var(--ink-muted)' }}
      >
        {label}
      </button>
    )
  }

  async function confirm() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
      setAsking(false)
    } catch (caught) {
      // `unexpected` for anything that is not already an AppError, so the panel
      // always has a code and a reference to show — a destructive action that
      // failed is exactly the one a parent will want to report.
      setError(caught instanceof AppError ? caught : unexpected(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div
      role="group"
      className="animate-[rise-in_var(--motion-base)_var(--ease-out)] flex flex-col gap-2 rounded-[12px] p-3"
      style={{ background: 'var(--sunken)' }}
    >
      <p className="text-sm" style={{ color: 'var(--ink-muted)' }}>
        {body}
      </p>
      {/* No `onRetry`: the confirm button below is the retry, and two buttons
          meaning the same thing in one box is a worse question. */}
      {error && <ErrorPanel error={error} />}
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={confirm}
          disabled={busy}
          className="rounded-[10px] px-3 py-1.5 text-sm font-medium transition-transform duration-[var(--motion-fast)] ease-[var(--ease-out)] active:scale-95 disabled:opacity-50"
          style={{ background: 'var(--urgent)', color: 'var(--card)' }}
        >
          {confirmLabel}
        </button>
        <button
          type="button"
          onClick={() => {
            setAsking(false)
            setError(null)
          }}
          className="rounded-[10px] px-3 py-1.5 text-sm transition-transform duration-[var(--motion-fast)] ease-[var(--ease-out)] active:scale-95"
          style={{ color: 'var(--ink-muted)' }}
        >
          {t.app.cancel}
        </button>
      </div>
    </div>
  )
}
