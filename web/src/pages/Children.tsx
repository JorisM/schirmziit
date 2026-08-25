import { useState } from 'react'
import { Link } from 'react-router-dom'
import useSWR from 'swr'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { useI18n } from '../i18n'

type ChildResponse = components['schemas']['ChildResponse']

export function Children() {
  const { t } = useI18n()
  const { data, error, mutate } = useSWR<ChildResponse[]>(
    `/v1/children?tz=${Intl.DateTimeFormat().resolvedOptions().timeZone}`,
    api.get,
  )
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)

  async function add(event: React.FormEvent) {
    event.preventDefault()
    if (!name.trim()) return
    setBusy(true)
    try {
      await api.post('/v1/children', { display_name: name.trim() })
      setName('')
      await mutate()
    } finally {
      setBusy(false)
    }
  }

  if (error) {
    return (
      <p role="alert" style={{ color: 'var(--urgent)' }}>
        {t.errors.generic}
      </p>
    )
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

      <ul className="grid gap-3 sm:grid-cols-2">
        {(data ?? []).map((child) => (
          <li key={child.id}>
            <Link
              to={`/children/${child.id}`}
              className="card flex items-baseline justify-between p-5 transition-transform hover:-translate-y-0.5"
            >
              <span className="font-display text-xl">{child.display_name}</span>
              <span className="text-sm underline" style={{ color: 'var(--accent)' }}>
                {t.children.openChild}
              </span>
            </Link>
          </li>
        ))}
      </ul>

      <form onSubmit={add} className="flex flex-wrap gap-2">
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
          className="rounded-[12px] px-4 py-2 font-medium disabled:opacity-50"
          style={{ background: 'var(--accent)', color: 'var(--card)' }}
        >
          {t.children.add}
        </button>
      </form>
    </div>
  )
}
