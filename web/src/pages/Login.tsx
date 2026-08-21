import { useState } from 'react'
import { ApiError, api } from '../api/client'

export function Login({ onSignedIn }: { onSignedIn: () => void }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await api.post('/v1/auth/login', { email, password })
      setDone(true)
      onSignedIn()
    } catch (caught) {
      // Show the API's own problem detail rather than a generic message.
      setError(caught instanceof ApiError ? caught.problem.detail : 'unexpected error')
    }
  }

  if (done) return <p>Signed in</p>

  return (
    <form onSubmit={submit} className="mx-auto flex max-w-sm flex-col gap-4 p-8">
      <h1 className="text-xl font-semibold">Nestling</h1>
      <label className="flex flex-col gap-1">
        Email
        <input
          className="rounded border p-2"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </label>
      <label className="flex flex-col gap-1">
        Password
        <input
          type="password"
          className="rounded border p-2"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>
      {error && (
        <p role="alert" className="text-red-600">
          {error}
        </p>
      )}
      <button type="submit" className="rounded bg-slate-900 p-2 text-white">
        Sign in
      </button>
    </form>
  )
}
