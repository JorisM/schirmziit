import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes, useParams } from 'react-router-dom'
import useSWR from 'swr'
import './index.css'
import { api } from './api/client'
import type { components } from './api/schema'
import { ChildDetail } from './pages/ChildDetail'
import { Children } from './pages/Children'
import { Login } from './pages/Login'

type MeResponse = components['schemas']['MeResponse']

function ChildRoute() {
  const { id } = useParams()
  return id ? <ChildDetail childId={id} /> : <p>Unknown child</p>
}

function App() {
  // GET /v1/me is the auth gate: a 401 means show the login form.
  const { data, error, mutate } = useSWR<MeResponse>('/v1/me', api.get, {
    shouldRetryOnError: false,
  })

  if (error) return <Login onSignedIn={() => void mutate()} />
  if (!data) return <p className="p-6">Loading…</p>

  return (
    <Routes>
      <Route path="/" element={<Children />} />
      <Route path="/children/:id" element={<ChildRoute />} />
    </Routes>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
