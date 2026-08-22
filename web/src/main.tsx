import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes, useParams } from 'react-router-dom'
import useSWR from 'swr'
import './index.css'
import { api } from './api/client'
import type { components } from './api/schema'
import { Shell } from './components/Shell'
import { LocaleProvider } from './i18n'
import { ChildDetail } from './pages/ChildDetail'
import { Children } from './pages/Children'
import { Help } from './pages/Help'
import { Login } from './pages/Login'

type MeResponse = components['schemas']['MeResponse']

function ChildRoute() {
  const { id } = useParams()
  return id ? <ChildDetail childId={id} /> : null
}

function App() {
  // GET /v1/me is the auth gate: a 401 means show the sign-in form.
  const { data, error, mutate } = useSWR<MeResponse>('/v1/me', api.get, {
    shouldRetryOnError: false,
  })

  if (error) return <Login onSignedIn={() => void mutate()} />
  if (!data) return null

  return (
    <Shell>
      <Routes>
        <Route path="/" element={<Children />} />
        <Route path="/children/:id" element={<ChildRoute />} />
        <Route path="/help" element={<Help />} />
      </Routes>
    </Shell>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <LocaleProvider>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </LocaleProvider>
  </StrictMode>,
)
