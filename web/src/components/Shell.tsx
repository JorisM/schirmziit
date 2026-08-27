import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useI18n } from '../i18n'
import { LocaleSwitcher } from './LocaleSwitcher'

export function Shell({ children }: { children: ReactNode }) {
  const { t } = useI18n()
  return (
    <div className="min-h-dvh">
      <header
        className="border-b"
        style={{ borderColor: 'var(--hairline)', background: 'var(--card)' }}
      >
        <div className="mx-auto flex max-w-4xl flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3">
          <Link
            to="/"
            className="flex items-center gap-2 font-display text-xl leading-none"
            style={{ color: 'var(--ink)' }}
          >
            <svg viewBox="0 0 64 64" width="26" height="26" aria-hidden="true" className="shrink-0">
              <path d="M32,28 L32,47 Q32,52 26.5,52" fill="none" stroke="var(--ink)" strokeWidth="2.6" strokeLinecap="round" />
              <circle cx="20" cy="39.7" r="3.2" fill="var(--ink)" />
              <path d="M16.4,50.5 v-3.2 a3.6,3.6 0 0 1 7.2,0 v3.2 Z" fill="var(--ink)" />
              <circle cx="44" cy="39.7" r="3.2" fill="var(--ink)" />
              <path d="M40.4,50.5 v-3.2 a3.6,3.6 0 0 1 7.2,0 v3.2 Z" fill="var(--ink)" />
              <path d="M8,26 L17.6,14.35 L17.6,34 Q12.8,28.5 8,34 Z" fill="var(--ribbon-1)" />
              <path d="M17.6,14.35 L27.2,10.45 L27.2,34 Q22.4,28.5 17.6,34 Z" fill="var(--ribbon-3)" />
              <path d="M27.2,10.45 L36.8,10.45 L36.8,34 Q32,28.5 27.2,34 Z" fill="var(--accent)" />
              <path d="M36.8,10.45 L46.4,14.35 L46.4,34 Q41.6,28.5 36.8,34 Z" fill="var(--ribbon-3)" />
              <path d="M46.4,14.35 L56,26 L56,34 Q51.2,28.5 46.4,34 Z" fill="var(--ribbon-1)" />
            </svg>
            {t.app.name}
          </Link>
          <span className="hidden text-sm sm:inline" style={{ color: 'var(--ink-faint)' }}>
            {t.app.tagline}
          </span>
          <nav className="ml-auto flex items-center gap-4">
            <Link to="/help" className="text-sm underline" style={{ color: 'var(--accent)' }}>
              {t.app.help}
            </Link>
            <LocaleSwitcher />
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-4xl px-5 py-8">{children}</main>
    </div>
  )
}
