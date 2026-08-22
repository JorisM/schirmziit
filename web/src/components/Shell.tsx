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
          <Link to="/" className="font-display text-xl leading-none" style={{ color: 'var(--ink)' }}>
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
