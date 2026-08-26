import { useState } from 'react'
import { Link } from 'react-router-dom'
import useSWR from 'swr'
import { api, AppError } from '../api/client'
import { unexpected } from '../api/errors'
import type { components } from '../api/schema'
import { DestructiveAction } from '../components/DestructiveAction'
import { ErrorPanel } from '../components/ErrorPanel'
import { formatDuration, useI18n } from '../i18n'
import { useCountUp } from '../motion'

type ChildResponse = components['schemas']['ChildResponse']

/**
 * One child, with today's total counting up beside the name.
 *
 * The count-up lives on the parent's list, not on the child's own screen: the
 * gesture celebrates the act of looking, and a number sprinting upward reads as
 * a score to the person it describes.
 */
function ChildCard({
  child,
  index,
  onRemove,
}: {
  child: ChildResponse
  index: number
  onRemove: () => Promise<void>
}) {
  const { t } = useI18n()
  const ms = useCountUp(child.today_ms)
  return (
    <li
      className="animate-[rise-in_var(--motion-base)_var(--ease-out)_backwards] flex flex-col gap-2"
      style={{ animationDelay: `calc(${index} * var(--motion-stagger))` }}
    >
      <Link
        to={`/children/${child.id}`}
        className="card flex items-baseline justify-between p-5 transition-transform duration-[var(--motion-fast)] ease-[var(--ease-out)] hover:-translate-y-0.5 active:scale-[0.99]"
      >
        <span className="font-display text-xl">{child.display_name}</span>
        <span className="text-right">
          <span className="tabular block text-lg">{formatDuration(ms, t)}</span>
          <span className="text-sm" style={{ color: 'var(--ink-faint)' }}>
            {t.children.todayTotal}
          </span>
        </span>
      </Link>
      {/* Outside the Link, not inside it: a button nested in an anchor is
          invalid HTML and a keyboard user cannot reach it. */}
      <div className="flex justify-end">
        <DestructiveAction
          label={t.children.remove}
          body={t.children.removeBody}
          confirmLabel={t.children.removeConfirm}
          onConfirm={onRemove}
        />
      </div>
    </li>
  )
}

export function Children() {
  const { t } = useI18n()
  const { data, error, mutate } = useSWR<ChildResponse[]>(
    `/v1/children?tz=${Intl.DateTimeFormat().resolvedOptions().timeZone}`,
    api.get,
  )
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [addError, setAddError] = useState<AppError | null>(null)

  async function add(event: React.FormEvent) {
    event.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    setAddError(null)
    try {
      await api.post('/v1/children', { display_name: name.trim() })
      setName('')
      await mutate()
    } catch (caught) {
      // Previously uncaught, which showed as nothing at all: the field kept the
      // typed name and no child appeared, with no way to tell a rejected name
      // from a dropped connection. `unexpected` for anything that is not an
      // AppError, so the panel always has a code to put on screen.
      setAddError(caught instanceof AppError ? caught : unexpected(caught, { endpoint: '/v1/children' }))
    } finally {
      setBusy(false)
    }
  }

  if (error) {
    return <ErrorPanel error={error as AppError} onRetry={() => void mutate()} />
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl">{t.children.heading}</h1>

      {data && data.length === 0 && (
        <section className="card p-6">
          <p className="font-medium">{t.children.empty}</p>
          <p className="mt-1 text-sm" style={{ color: 'var(--ink-muted)' }}>
            {t.children.emptyHint}
          </p>
        </section>
      )}

      <ul className="grid items-start gap-3 sm:grid-cols-2">
        {(data ?? []).map((child, index) => (
          <ChildCard
            key={child.id}
            child={child}
            index={index}
            // Deliberately not an optimistic mutate: the row must stay put
            // until the server has actually accepted the delete, so a failed
            // call cannot leave a child missing from the screen but present in
            // the family. DestructiveAction surfaces the error.
            onRemove={async () => {
              await api.del(`/v1/children/${child.id}`)
              await mutate()
            }}
          />
        ))}
      </ul>

      <form onSubmit={add} className="flex flex-col gap-2">
        {addError && <ErrorPanel error={addError} />}
        <div className="flex flex-wrap gap-2">
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder={t.children.addPlaceholder}
            aria-label={t.children.add}
            className="min-w-48 flex-1 rounded-[12px] border px-3 py-2"
            style={{ borderColor: 'var(--hairline)', background: 'var(--card)' }}
          />
          <button
            type="submit"
            disabled={busy || !name.trim()}
            className="rounded-[12px] px-4 py-2 font-medium transition-transform duration-[var(--motion-fast)] ease-[var(--ease-out)] active:scale-95 disabled:opacity-50"
            style={{ background: 'var(--accent)', color: 'var(--card)' }}
          >
            {t.children.add}
          </button>
        </div>
      </form>
    </div>
  )
}
