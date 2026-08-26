import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ErrorPanel } from './ErrorPanel'
import { clearErrorLog, fromProblem, fromTransport, type ErrorCode } from '../api/errors'
import { LocaleProvider } from '../i18n'

const problem = (code: ErrorCode, ref = '7f3a9c') =>
  fromProblem(
    { type: 't', title: 't', status: 500, detail: 'internal error', code, ref },
    { endpoint: '/v1/children', httpStatus: 500 },
  )

const show = (error: ReturnType<typeof problem>, props = {}) =>
  render(
    <LocaleProvider>
      <ErrorPanel error={error} {...props} />
    </LocaleProvider>,
  )

describe('ErrorPanel', () => {
  beforeEach(() => clearErrorLog())

  it('puts the code and the reference on screen, so a screenshot is enough', () => {
    show(problem('SZ-E901'))
    expect(screen.getByText(/SZ-E901 · 7f3a9c/)).toBeInTheDocument()
  })

  it("says what happened and what to do, in the reader's language", () => {
    show(problem('SZ-E901'))
    // English fallback in tests: navigator.language is en-US under jsdom.
    expect(screen.getByText(/went wrong on the server/i)).toBeInTheDocument()
    expect(screen.getByText(/try again in a moment/i)).toBeInTheDocument()
  })

  it("never shows the server's English detail", () => {
    show(problem('SZ-E901'))
    expect(screen.queryByText(/internal error/)).toBeNull()
  })

  it('keeps the detail collapsed until it is asked for', async () => {
    show(problem('SZ-E901'))
    const toggle = screen.getByRole('button', { name: /error code and reference/i })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')

    await userEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText(/GET \/v1\/children → 500/)).toBeInTheDocument()
  })

  it('offers a retry only when there is something to retry', async () => {
    const onRetry = vi.fn()
    show(problem('SZ-E901'), { onRetry })
    await userEvent.click(screen.getByRole('button', { name: /try again/i }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('shows no retry when the caller offers none', () => {
    show(problem('SZ-E901'))
    expect(screen.queryByRole('button', { name: /try again/i })).toBeNull()
  })

  it('does not paint an expected, self-correcting failure as urgent', () => {
    // An offline phone in a Swiss valley painting the dashboard red teaches a
    // parent to ignore the colour that means something actually broke.
    const offline = fromTransport(new DOMException('aborted', 'AbortError'), {
      endpoint: '/v1/children',
    })
    const { container } = show(offline)
    expect(container.querySelector('[data-weight="neutral"]')).not.toBeNull()
  })

  it('paints a real failure as urgent', () => {
    const { container } = show(problem('SZ-E901'))
    expect(container.querySelector('[data-weight="urgent"]')).not.toBeNull()
  })

  it('keeps the mono line readable — dimmed by token, never by opacity', () => {
    const { container } = show(problem('SZ-E901'))
    const mono = container.querySelector('[data-error-reference]') as HTMLElement
    expect(mono.style.opacity).toBe('')
  })

  it('announces itself', () => {
    show(problem('SZ-E901'))
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('falls back to the server-error copy for a code the dashboard has no text for', () => {
    // SZ-E603 is Android-only, so it has no web copy. An empty panel would be
    // worse than the wrong words: there would be nothing to read and nothing
    // to report.
    show(problem('SZ-E603'))
    expect(screen.getByText(/SZ-E603 · 7f3a9c/)).toBeInTheDocument()
    expect(screen.getByText(/went wrong/i)).toBeInTheDocument()
  })
})
