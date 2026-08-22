import { useState } from 'react'
import { ApiError, api } from '../api/client'
import { LocaleSwitcher } from '../components/LocaleSwitcher'
import { useI18n } from '../i18n'

export function Login({ onSignedIn }: { onSignedIn: () => void }) {
  const { t } = useI18n()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await api.post('/v1/auth/login', { email, password })
      onSignedIn()
    } catch (caught) {
      setError(
        caught instanceof ApiError && caught.problem.status === 401
          ? t.login.wrongCredentials
          : t.login.unexpected,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col justify-center gap-6 px-5 py-10">
      <header>
        <h1 className="font-display text-3xl">{t.app.name}</h1>
        <p className="mt-1" style={{ color: 'var(--ink-muted)' }}>
          {t.app.tagline}
        </p>
      </header>

      <form onSubmit={submit} className="card flex flex-col gap-4 p-6">
        <h2 className="text-xl">{t.login.heading}</h2>
        <p className="text-sm" style={{ color: 'var(--ink-muted)' }}>
          {t.login.intro}
        </p>

        <label className="flex flex-col gap-1 text-sm">
          {t.login.email}
          <input
            type="email"
            autoComplete="username"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="rounded-[12px] border px-3 py-2 text-base"
            style={{ borderColor: 'var(--hairline)', background: 'var(--paper)' }}
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          {t.login.password}
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="rounded-[12px] border px-3 py-2 text-base"
            style={{ borderColor: 'var(--hairline)', background: 'var(--paper)' }}
          />
        </label>

        {error && (
          <p role="alert" style={{ color: 'var(--urgent)' }}>
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={busy}
          className="rounded-[12px] px-4 py-2 font-medium disabled:opacity-60"
          style={{ background: 'var(--accent)', color: 'var(--card)' }}
        >
          {busy ? t.login.working : t.login.submit}
        </button>
      </form>

      <div className="flex justify-center">
        <LocaleSwitcher />
      </div>
    </div>
  )
}
