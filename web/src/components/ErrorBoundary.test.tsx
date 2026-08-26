import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'
import { clearErrorLog, recentErrors } from '../api/errors'
import { LocaleProvider } from '../i18n'

function Explodes(): never {
  throw new Error('render blew up')
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    clearErrorLog()
    // React logs the caught error; the noise is expected here.
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })
  afterEach(() => vi.restoreAllMocks())

  it('turns a render crash into something a parent can report', () => {
    render(
      <LocaleProvider>
        <ErrorBoundary>
          <Explodes />
        </ErrorBoundary>
      </LocaleProvider>,
    )

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/SZ-E707 · [0-9a-f]{6}/)).toBeInTheDocument()
  })

  it('records the crash, so the copy block shows what led up to it', () => {
    render(
      <LocaleProvider>
        <ErrorBoundary>
          <Explodes />
        </ErrorBoundary>
      </LocaleProvider>,
    )
    expect(recentErrors().at(-1)?.code).toBe('SZ-E707')
  })

  it('renders its children when nothing is wrong', () => {
    render(
      <LocaleProvider>
        <ErrorBoundary>
          <p>all fine</p>
        </ErrorBoundary>
      </LocaleProvider>,
    )
    expect(screen.getByText('all fine')).toBeInTheDocument()
  })
})
