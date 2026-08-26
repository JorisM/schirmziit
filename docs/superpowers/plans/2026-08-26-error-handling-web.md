# Error Handling on the Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every error the dashboard shows carries its code and reference on screen, reads in the viewer's own language, and can be copied as a report — with a failed *refresh* never blanking data that is already loaded.

**Architecture:** A typed `AppError` is built at the one boundary that can fail (`api/client.ts`) and threaded to the screen that asked for the work; string-typed error state is removed, so a view cannot render an error without a code. Construction records into a 50-entry in-memory ring buffer. One `ErrorPanel` renders every error, inline or as a banner, with a tappable mono line that expands into the copyable detail.

**Tech Stack:** React 19, SWR, Tailwind v4 with the `--motion-*` tokens, vitest + Testing Library, the generated `web/src/i18n/errors.ts` from the foundation plan.

**Spec:** `docs/superpowers/specs/2026-08-26-error-handling-design.md`

**Depends on:** `docs/superpowers/plans/2026-08-26-error-handling-foundation.md` (merged). It provides `components['schemas']['ErrorCode']` and `['Problem']` in `web/src/api/schema.d.ts`, and `errorCopy` / `ErrorCopyCode` in `web/src/i18n/errors.ts`.

## Global Constraints

- Four languages, always: de (Schweizer Hochdeutsch, du-form, no ß), fr, it, en. Per-code copy is generated from `copy/errors.toml`; the panel's own chrome ("Try again", "Copy details") is hand-written and must land in all four locale files in the same commit.
- **`problem.detail` is never rendered.** It is English and exists for the log and the copy block. The client looks copy up by `code`.
- **A failed refresh never clears loaded data.** Inline placement is for data that failed to load; banner placement is for a refresh that failed while good data is on screen. Blanking a loaded chart is the "lost day" mistake one layer up.
- **No flourish on an error state** — entry motion and press feedback only, on the `--motion-*` tokens, which a single media query zeroes.
- The mono line is dimmed **by token, not opacity**: it must stay legible in a screenshot.
- Never in the copy payload: email, child name, request or response bodies, or the server **host** — endpoint is a path only. A self-hoster pasting a screenshot into a public issue must not publish their homelab hostname.
- Gate: `just web-check` (which now also runs `gen-copy-check`). Run `just rust-check` too if anything under `crates/` changes.
- Commits: `type: subject`, blank line, `refs: SZ-ERRORS`. No AI-attribution trailers.

---

## File Structure

**Create**
- `web/src/api/errors.ts` — `AppError`, the code mapping for transport failures, the ring buffer, the copy-details payload.
- `web/src/api/errors.test.ts`
- `web/src/components/ErrorPanel.tsx` — the one error component, inline and banner.
- `web/src/components/ErrorPanel.test.tsx`
- `web/src/components/ErrorBoundary.tsx` — render crashes and unhandled rejections become `SZ-E707`.
- `web/src/components/ErrorBoundary.test.tsx`
- `web/src/hygiene.test.ts` — asserts no view renders `problem.detail`.

**Modify**
- `web/src/api/client.ts` — build `AppError`; `ApiError` goes away.
- `web/src/pages/{Children,ChildDetail,Login}.tsx` — render `ErrorPanel`; drop string error state.
- `web/src/i18n/types.ts` + `{de,fr,it,en}.ts` — add `errorPanel` chrome strings, remove the now-unused `errors` block and `child.historyError`.
- `web/src/main.tsx` — mount the boundary and the global handlers.
- `web/vite.config.ts` — `VITE_APP_VERSION` from package.json.
- `copy/errors.toml` — correct the reach of `SZ-E503` (see Task 1, Step 6).

---

### Task 1: `AppError` and the ring buffer

**Files:**
- Create: `web/src/api/errors.ts`, `web/src/api/errors.test.ts`
- Modify: `web/src/api/client.ts`, `web/src/api/client.test.ts`, `copy/errors.toml`

**Interfaces:**
- Consumes: `components['schemas']['ErrorCode' | 'Problem']` from `web/src/api/schema.d.ts`.
- Produces: `AppError` (class, fields `code`, `ref`, `at`, `endpoint?`, `httpStatus?`), `fromProblem`, `fromTransport`, `unexpected`, `recentErrors()`, `clearErrorLog()`, `APP_VERSION`. Tasks 2–5 all build on these names.

- [ ] **Step 1: Write the failing test**

`web/src/api/errors.test.ts`:

```tsx
import { beforeEach, describe, expect, it } from 'vitest'
import { AppError, clearErrorLog, fromProblem, fromTransport, recentErrors, unexpected } from './errors'

describe('AppError', () => {
  beforeEach(() => clearErrorLog())

  it('takes its code and reference from the server problem', () => {
    const error = fromProblem(
      {
        type: 'https://schirmziit.ch/problems/not-found',
        title: 'not-found',
        status: 404,
        detail: 'not found',
        code: 'SZ-E201',
        ref: '7f3a9c',
      },
      { endpoint: '/v1/children', httpStatus: 404 },
    )

    expect(error).toBeInstanceOf(AppError)
    expect(error.code).toBe('SZ-E201')
    expect(error.ref).toBe('7f3a9c')
    expect(error.httpStatus).toBe(404)
  })

  it('makes its own reference when the failure never reached the server', () => {
    const error = fromTransport(new TypeError('Failed to fetch'), { endpoint: '/v1/children' })
    expect(error.ref).toMatch(/^[0-9a-f]{6}$/)
  })

  it('reads a browser fetch rejection as offline only when the browser says so', () => {
    // navigator.onLine is true under jsdom, so a bare TypeError is a server
    // that cannot be reached — not a phone in a tunnel. Calling every fetch
    // failure "offline" sends a parent to check Wi-Fi that is working.
    const online = fromTransport(new TypeError('Failed to fetch'), { endpoint: '/v1/me' })
    expect(online.code).toBe('SZ-E505')
  })

  it('reads an abort as a timeout', () => {
    const aborted = new DOMException('aborted', 'AbortError')
    expect(fromTransport(aborted, { endpoint: '/v1/me' }).code).toBe('SZ-E502')
  })

  it('records every error it builds, newest last', () => {
    fromTransport(new TypeError('a'), { endpoint: '/v1/one' })
    fromTransport(new TypeError('b'), { endpoint: '/v1/two' })
    expect(recentErrors().map((e) => e.endpoint)).toEqual(['/v1/one', '/v1/two'])
  })

  it('keeps only the last fifty, so a failing poll cannot grow without bound', () => {
    for (let i = 0; i < 60; i += 1) fromTransport(new TypeError('x'), { endpoint: `/v1/${i}` })
    const log = recentErrors()
    expect(log).toHaveLength(50)
    expect(log[0].endpoint).toBe('/v1/10')
    expect(log[49].endpoint).toBe('/v1/59')
  })

  it('never keeps the host, only the path', () => {
    const error = unexpected(new Error('boom'), { endpoint: 'https://home.example.ch/v1/children' })
    expect(error.endpoint).toBe('/v1/children')
    expect(error.code).toBe('SZ-E707')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/api/errors.test.ts`
Expected: FAIL — `Failed to resolve import "./errors"`.

- [ ] **Step 3: Write the implementation**

`web/src/api/errors.ts`:

```tsx
import type { components } from './schema'

export type ErrorCode = components['schemas']['ErrorCode']
export type Problem = components['schemas']['Problem']

/** Injected at build time; `dev` in a `pnpm dev` session. */
export const APP_VERSION: string = import.meta.env.VITE_APP_VERSION ?? 'dev'

type Context = {
  endpoint?: string
  httpStatus?: number
}

/**
 * Every failure the dashboard can show, as one value.
 *
 * Built only at a boundary — never in a view — so that a screen cannot render
 * an error without a code to put on screen and a reference to report. The
 * string-typed error state this replaced could say anything, and did.
 */
export class AppError extends Error {
  readonly code: ErrorCode
  readonly ref: string
  readonly at: Date
  readonly endpoint?: string
  readonly httpStatus?: number

  constructor(code: ErrorCode, ref: string, context: Context = {}) {
    super(code)
    this.name = 'AppError'
    this.code = code
    this.ref = ref
    this.at = new Date()
    this.endpoint = context.endpoint ? pathOnly(context.endpoint) : undefined
    this.httpStatus = context.httpStatus
    record(this)
  }
}

/**
 * Path only, never the host: a self-hoster pasting a screenshot into a public
 * issue would otherwise publish the address of the machine in their flat.
 */
function pathOnly(endpoint: string): string {
  try {
    return new URL(endpoint, 'http://placeholder.invalid').pathname
  } catch {
    return endpoint
  }
}

function makeRef(): string {
  const bytes = new Uint8Array(3)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

export function fromProblem(problem: Problem, context: Context = {}): AppError {
  return new AppError(problem.code, problem.ref, context)
}

/**
 * A fetch that never produced a response.
 *
 * The browser tells us very little here on purpose — a cross-origin failure,
 * a refused connection and a bad certificate are all one opaque `TypeError`,
 * which is why the dashboard has no TLS code of its own.
 */
export function fromTransport(cause: unknown, context: Context = {}): AppError {
  const code: ErrorCode =
    cause instanceof DOMException && cause.name === 'AbortError'
      ? 'SZ-E502'
      : navigator.onLine === false
        ? 'SZ-E501'
        : 'SZ-E505'
  return new AppError(code, makeRef(), context)
}

/** A response that arrived but was not the JSON the API promises. */
export function badResponseBody(context: Context = {}): AppError {
  return new AppError('SZ-E504', makeRef(), context)
}

/** A render crash or an unhandled rejection — a bug in the dashboard itself. */
export function unexpected(_cause: unknown, context: Context = {}): AppError {
  return new AppError('SZ-E707', makeRef(), context)
}

const LOG_LIMIT = 50
let log: AppError[] = []

function record(error: AppError) {
  log.push(error)
  if (log.length > LOG_LIMIT) log = log.slice(-LOG_LIMIT)
}

/**
 * In memory only, so it dies on reload. That is deliberate: the screenshot is
 * the report, and "copy details" is used in the moment. Persisting a log of
 * failures across sessions would be storing data about a family for no reason
 * anyone asked for.
 */
export function recentErrors(): readonly AppError[] {
  return log
}

export function clearErrorLog() {
  log = []
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/api/errors.test.ts`
Expected: PASS, 7 tests.

- [ ] **Step 5: Rewrite the client boundary**

Replace the `ApiError` class and the error branch of `request` in `web/src/api/client.ts`:

```tsx
import { AppError, badResponseBody, fromProblem, fromTransport, type Problem } from './errors'

export { AppError }

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(apiUrl(path), {
      method,
      headers: body ? { 'content-type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
      // `include`, not `same-origin`: on the split hosts the session cookie is
      // set by and sent to `api.`, which is a different origin from the page.
      // Both are under `schirmziit.ch`, so this stays a same-SITE cookie and no
      // third-party-cookie policy applies. Same-origin self-hosting is
      // unaffected — `include` behaves identically there.
      credentials: 'include',
    })
  } catch (cause) {
    throw fromTransport(cause, { endpoint: path })
  }

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as Problem | null
    // A body that is not the API's problem shape means something answered in
    // the server's place — a captive portal, a proxy error page. It must throw
    // rather than be read as anything else.
    if (!problem || typeof problem.code !== 'string') {
      throw badResponseBody({ endpoint: path, httpStatus: response.status })
    }
    throw fromProblem(problem, { endpoint: path, httpStatus: response.status })
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}
```

Delete the old `ApiError` class and the `ApiProblem` type from `client.ts` — `Problem` from the generated schema replaces it, and two problem types would drift.

- [ ] **Step 6: Correct the reach of `SZ-E503`**

The dashboard cannot tell a TLS failure from any other fetch rejection — the browser gives one opaque `TypeError` — so `copy/errors.toml` claiming `web` can emit `SZ-E503` is wrong. Change that entry's reach:

```toml
[SZ-E503]
weight = "urgent"
reach = ["ios", "android"]
```

Then `just gen-copy` and commit the regenerated `web/src/i18n/errors.ts` along with it. This is a correction to the foundation plan's guess, found by implementing against it.

- [ ] **Step 7: Update the client tests**

In `web/src/api/client.test.ts`, the existing non-JSON test (around line 50) asserts only that it throws. Extend it:

```tsx
  it('throws on a non-JSON error body instead of reading it as success', async () => {
    // ...existing fetch stub returning HTML with a 502...
    await expect(api.get('/v1/children')).rejects.toMatchObject({ code: 'SZ-E504' })
  })
```

Keep every other assertion in that file as it is; adjust only the ones that referenced `ApiError` or `problem.detail`.

- [ ] **Step 8: Run the suite and prove the log cap is real**

Run: `cd web && pnpm vitest run src/api`
Expected: PASS.

Then change `if (log.length > LOG_LIMIT) log = log.slice(-LOG_LIMIT)` to `if (log.length > LOG_LIMIT) log = log` and re-run.
Expected: `keeps only the last fifty` FAILS. Revert.

- [ ] **Step 9: Commit**

```bash
git add web/src/api copy/errors.toml web/src/i18n/errors.ts
git commit -F - <<'MSG'
feat: one typed error value at the dashboard's boundary

refs: SZ-ERRORS
MSG
```

---

### Task 2: The copy-details payload

**Files:**
- Modify: `web/src/api/errors.ts`, `web/src/api/errors.test.ts`

**Interfaces:**
- Consumes: `AppError`, `recentErrors`, `APP_VERSION` from Task 1.
- Produces: `copyDetails(error: AppError): string`. Task 3's panel calls it.

- [ ] **Step 1: Write the failing test**

Append to `web/src/api/errors.test.ts`:

```tsx
describe('copyDetails', () => {
  beforeEach(() => clearErrorLog())

  it('leads with the code and reference, exactly as they appear on screen', () => {
    const error = fromProblem(
      { type: 't', title: 't', status: 502, detail: 'bad gateway', code: 'SZ-E504', ref: '7f3a9c' },
      { endpoint: '/v1/children', httpStatus: 502 },
    )
    const [first] = copyDetails(error).split('\n')
    expect(first).toBe('SZ-E504 · 7f3a9c')
  })

  it('carries the version, the surface, the endpoint and the status', () => {
    const error = fromProblem(
      { type: 't', title: 't', status: 502, detail: 'x', code: 'SZ-E504', ref: '7f3a9c' },
      { endpoint: '/v1/children', httpStatus: 502 },
    )
    const text = copyDetails(error)
    expect(text).toContain('web')
    expect(text).toContain('GET /v1/children → 502')
  })

  it('never carries the host, an email or a child name', () => {
    const error = fromProblem(
      { type: 't', title: 't', status: 404, detail: 'anna@example.ch asked for Mia', code: 'SZ-E201', ref: 'abc123' },
      { endpoint: 'https://home.example.ch/v1/children/mia-id', httpStatus: 404 },
    )
    const text = copyDetails(error)
    expect(text).not.toContain('home.example.ch')
    expect(text).not.toContain('anna@example.ch')
    expect(text).not.toContain('Mia')
  })

  it('lists what failed before it, because "it has been failing all morning" is the useful part', () => {
    fromTransport(new TypeError('a'), { endpoint: '/v1/one' })
    fromTransport(new TypeError('b'), { endpoint: '/v1/two' })
    const latest = fromTransport(new TypeError('c'), { endpoint: '/v1/three' })

    const text = copyDetails(latest)
    expect(text).toContain('/v1/one')
    expect(text).toContain('/v1/two')
    // The newest is the header block, not a repeat in the list.
    expect(text.match(/\/v1\/three/g)).toHaveLength(1)
  })
})
```

Add `copyDetails` to the import at the top of the file.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/api/errors.test.ts`
Expected: FAIL — `copyDetails is not a function`.

- [ ] **Step 3: Implement**

Append to `web/src/api/errors.ts`:

```tsx
const PREVIOUS_SHOWN = 4

/** `2026-08-26 14:02:11 +02:00` — sortable, and unambiguous about the zone. */
function stamp(at: Date): string {
  const local = at.toLocaleString('sv-SE')
  const offset = -at.getTimezoneOffset()
  const sign = offset < 0 ? '-' : '+'
  const hours = String(Math.floor(Math.abs(offset) / 60)).padStart(2, '0')
  const minutes = String(Math.abs(offset) % 60).padStart(2, '0')
  return `${local} ${sign}${hours}:${minutes}`
}

function where(error: AppError): string {
  if (!error.endpoint) return ''
  const status = error.httpStatus ? ` → ${error.httpStatus}` : ''
  return `GET ${error.endpoint}${status}`
}

/**
 * The block behind "copy details".
 *
 * It holds what a maintainer needs and nothing that describes a family: no
 * email, no child name, no request or response body, and the endpoint as a
 * path with the host stripped in the constructor.
 */
export function copyDetails(error: AppError): string {
  const lines = [
    `${error.code} · ${error.ref}`,
    stamp(error.at),
    `schirmziit ${APP_VERSION} · web · ${navigator.userAgent}`,
    where(error),
  ].filter(Boolean)

  const previous = recentErrors()
    .filter((entry) => entry !== error)
    .slice(-PREVIOUS_SHOWN)
  if (previous.length > 0) {
    lines.push('', 'before this:')
    for (const entry of previous) {
      lines.push(
        `  ${entry.code} · ${entry.ref} · ${entry.at.toLocaleTimeString('sv-SE')} · ${where(entry) || '—'}`,
      )
    }
  }
  return lines.join('\n')
}
```

The user agent is in there deliberately: it is the difference between "a rendering bug" and "a rendering bug on an eight-year-old iPad", and this block only ever leaves the machine when the reader chooses to paste it.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/api/errors.test.ts`
Expected: PASS.

- [ ] **Step 5: Prove the host test is not vacuous**

Change the constructor to `this.endpoint = context.endpoint` (dropping `pathOnly`).
Expected: `never carries the host…` FAILS. Revert.

- [ ] **Step 6: Commit**

```bash
git add web/src/api
git commit -F - <<'MSG'
feat: a copyable report block for any dashboard error

refs: SZ-ERRORS
MSG
```

---

### Task 3: `ErrorPanel`

**Files:**
- Create: `web/src/components/ErrorPanel.tsx`, `web/src/components/ErrorPanel.test.tsx`
- Modify: `web/src/i18n/types.ts`, `web/src/i18n/{de,fr,it,en}.ts`

**Interfaces:**
- Consumes: `AppError`, `copyDetails` (Tasks 1–2), `errorCopy` / `ErrorCopyCode` from `web/src/i18n/errors.ts`.
- Produces: `<ErrorPanel error={AppError} onRetry?={() => void} variant?={'inline' | 'banner'} />`. Task 4 places it.

- [ ] **Step 1: Add the chrome strings, in all four languages**

In `web/src/i18n/types.ts`, add to `Strings`:

```tsx
  errorPanel: {
    retry: string
    details: string
    copy: string
    copied: string
    reference: string
  }
```

Then in each locale file. `reference` is the accessible label for the mono line, which otherwise reads as gibberish to a screen reader:

```tsx
// de.ts
  errorPanel: {
    retry: 'Nochmal versuchen',
    details: 'Details',
    copy: 'Details kopieren',
    copied: 'Kopiert',
    reference: 'Fehlercode und Referenz',
  },
// fr.ts
  errorPanel: {
    retry: 'Réessayer',
    details: 'Détails',
    copy: 'Copier les détails',
    copied: 'Copié',
    reference: "Code d'erreur et référence",
  },
// it.ts
  errorPanel: {
    retry: 'Riprova',
    details: 'Dettagli',
    copy: 'Copia i dettagli',
    copied: 'Copiato',
    reference: 'Codice di errore e riferimento',
  },
// en.ts
  errorPanel: {
    retry: 'Try again',
    details: 'Details',
    copy: 'Copy details',
    copied: 'Copied',
    reference: 'Error code and reference',
  },
```

- [ ] **Step 2: Write the failing test**

`web/src/components/ErrorPanel.test.tsx`:

```tsx
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

  it('says what happened and what to do, in the reader\'s language', () => {
    show(problem('SZ-E901'))
    // English fallback in tests: navigator.language is en-US under jsdom.
    expect(screen.getByText(/went wrong on the server/i)).toBeInTheDocument()
    expect(screen.getByText(/try again in a moment/i)).toBeInTheDocument()
  })

  it('never shows the server\'s English detail', () => {
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

    clearErrorLog()
    show(problem('SZ-E901'))
    expect(screen.queryByRole('button', { name: /try again/i })).toBeNull()
  })

  it('does not paint an expected, self-correcting failure as urgent', () => {
    // An offline phone in a Swiss valley painting the dashboard red teaches a
    // parent to ignore the colour that means something actually broke.
    const offline = fromTransport(new TypeError('x'), { endpoint: '/v1/children' })
    const { container } = show(offline)
    expect(container.querySelector('[data-weight="neutral"]')).not.toBeNull()

    clearErrorLog()
    const { container: urgent } = show(problem('SZ-E901'))
    expect(urgent.querySelector('[data-weight="urgent"]')).not.toBeNull()
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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/components/ErrorPanel.test.tsx`
Expected: FAIL — cannot resolve `./ErrorPanel`.

- [ ] **Step 4: Implement**

`web/src/components/ErrorPanel.tsx`:

```tsx
import { useState } from 'react'
import { copyDetails, type AppError } from '../api/errors'
import { errorCopy, type ErrorCopyCode } from '../i18n/errors'
import { useI18n } from '../i18n'

type Placement = 'inline' | 'banner'

/**
 * Every error the dashboard shows, in one component.
 *
 * `inline` replaces the data that failed to load and takes the footprint its
 * skeleton had, so nothing jumps. `banner` sits above data that is already on
 * screen when a *refresh* failed — the numbers stay, the banner says they are
 * stale. Blanking a loaded chart because a poll failed is the same mistake as
 * losing a day, one layer up.
 *
 * Entry motion and press feedback, and no flourish: the flourish belongs to
 * the data. Animating a failure is the interface enjoying itself at the
 * parent's expense.
 */
export function ErrorPanel({
  error,
  onRetry,
  variant = 'inline',
}: {
  error: AppError
  onRetry?: () => void
  variant?: Placement
}) {
  const { t, locale } = useI18n()
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)

  // A code with no web copy still has to read as something: an Android-only
  // failure arriving here is a bug, and a blank panel would hide it.
  const entry = errorCopy[error.code as ErrorCopyCode] ?? errorCopy['SZ-E901']
  const copy = entry[locale]
  const urgent = entry.weight === 'urgent'

  async function copyToClipboard() {
    await navigator.clipboard.writeText(copyDetails(error))
    setCopied(true)
  }

  return (
    <div
      role="alert"
      data-weight={entry.weight}
      className={[
        'flex flex-col gap-2 animate-[rise-in_var(--motion-base)_var(--ease-out)_backwards]',
        variant === 'banner' ? 'card mb-4 p-4' : 'p-1',
      ].join(' ')}
      style={{ borderColor: urgent ? 'var(--urgent)' : 'var(--hairline)' }}
    >
      <p className="font-medium" style={{ color: urgent ? 'var(--urgent)' : 'var(--ink)' }}>
        {copy.title}
      </p>
      <p className="text-sm" style={{ color: 'var(--ink-muted)' }}>
        {copy.action}
      </p>

      {onRetry && (
        <div>
          <button
            type="button"
            onClick={onRetry}
            className="rounded-[12px] px-3 py-1.5 text-sm font-medium transition-transform duration-[var(--motion-fast)] active:scale-[0.97]"
            style={{ background: 'var(--accent)', color: 'var(--card)' }}
          >
            {t.errorPanel.retry}
          </button>
        </div>
      )}

      <button
        type="button"
        aria-expanded={open}
        aria-controls="error-detail"
        aria-label={t.errorPanel.reference}
        onClick={() => setOpen((was) => !was)}
        data-error-reference
        className="self-start font-mono text-xs transition-transform duration-[var(--motion-fast)] active:scale-[0.97]"
        // Dimmed by token, never by opacity: this line has to survive being
        // photographed and re-compressed by a messenger.
        style={{ color: 'var(--ink-muted)' }}
      >
        {error.code} · {error.ref} {open ? '▴' : '▾'}
      </button>

      {open && (
        <div
          id="error-detail"
          className="flex flex-col gap-1 font-mono text-xs animate-[fade-in_var(--motion-fast)_var(--ease-out)_backwards]"
          style={{ color: 'var(--ink-faint)' }}
        >
          <span>{detailLine(error)}</span>
          <span>{error.at.toLocaleString(locale)}</span>
          <button
            type="button"
            onClick={copyToClipboard}
            className="self-start underline"
            style={{ color: 'var(--accent)' }}
          >
            {copied ? t.errorPanel.copied : t.errorPanel.copy}
          </button>
        </div>
      )}
    </div>
  )
}

function detailLine(error: AppError): string {
  const status = error.httpStatus ? ` → ${error.httpStatus}` : ''
  return error.endpoint ? `GET ${error.endpoint}${status}` : '—'
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/components/ErrorPanel.test.tsx`
Expected: PASS, 9 tests.

- [ ] **Step 6: Prove the weight test is not vacuous**

Change `const urgent = entry.weight === 'urgent'` to `const urgent = true` and hardcode `data-weight="urgent"`.
Expected: `does not paint an expected, self-correcting failure as urgent` FAILS. Revert.

- [ ] **Step 7: Check the reduced-motion path by reading, not by test**

vitest asserts settled values; it cannot prove a keyframe. Both animations here ride `--motion-base` / `--motion-fast`, which `@media (prefers-reduced-motion: reduce)` in `index.css` sets to `0ms` — so the panel lands on its final state instantly. Confirm by eye: run `pnpm dev`, turn on Reduce Motion in macOS Accessibility settings, and trigger an error (stop the API). The panel must appear finished, never half-risen.

- [ ] **Step 8: Commit**

```bash
git add web/src/components/ErrorPanel.tsx web/src/components/ErrorPanel.test.tsx web/src/i18n
git commit -F - <<'MSG'
feat: one error panel for the dashboard, with the code on screen

refs: SZ-ERRORS
MSG
```

---

### Task 4: Place the panel, and stop rendering `detail`

**Files:**
- Modify: `web/src/pages/{Children,ChildDetail,Login}.tsx`
- Modify: `web/src/i18n/types.ts`, `web/src/i18n/{de,fr,it,en}.ts` (remove the dead strings)
- Create: `web/src/hygiene.test.ts`
- Modify: `web/src/pages/ChildDetail.test.tsx` (the `historyError` test moves to the new copy), `web/src/pages/Children.test.tsx`
- Modify: `web/src/components/DayStrip.tsx` (a `data-day-bar` hook, only if it has none)

**Interfaces:**
- Consumes: `ErrorPanel` (Task 3), `AppError` (Task 1).
- Produces: no new exports. This is the task that makes the feature visible.

- [ ] **Step 1: Write the failing tests**

`web/src/hygiene.test.ts`:

```tsx
import { describe, expect, it } from 'vitest'

/**
 * `detail` is the server's English sentence. It exists for the log and the
 * copy-details block. Rendering it puts English in the middle of a German
 * dashboard, which is exactly what it used to do at ChildDetail.tsx:50.
 */
describe('the dashboard never renders the server\'s detail', () => {
  const sources = import.meta.glob(['./pages/*.tsx', './components/*.tsx'], {
    query: '?raw',
    eager: true,
  }) as Record<string, { default: string }>

  it('has sources to check', () => {
    expect(Object.keys(sources).length).toBeGreaterThan(5)
  })

  for (const [path, module] of Object.entries(sources)) {
    if (path.includes('.test.')) continue
    it(`${path} does not read .detail`, () => {
      expect(module.default).not.toMatch(/\.detail\b/)
    })
  }
})
```

In `web/src/pages/ChildDetail.test.tsx` there is already a test that stubs a
failing day-bucket fetch and asserts `locales.en.child.historyError` (around
line 146). That string is being deleted, so the test moves to the new copy —
and gains the case it never covered. Two tests, because the two placements are
different rules:

```tsx
  it('shows the strip failure in the strip\'s own footprint on a first load', async () => {
    vi.spyOn(api, 'get').mockImplementation(async (path: string) => {
      const url = new URL(path, 'http://x')
      if (url.searchParams.get('bucket') === 'day')
        throw fromProblem(
          { type: 't', title: 't', status: 502, detail: 'bad gateway', code: 'SZ-E901', ref: 'aa11bb' },
          { endpoint: path, httpStatus: 502 },
        )
      return usage('hour', url.searchParams.get('from')!, url.searchParams.get('to')!) as never
    })

    renderPage()

    await waitFor(() => expect(screen.getByText(/SZ-E901 · aa11bb/)).toBeInTheDocument())
    // A fortnight of zero-height bars must never render in place of the error —
    // that is indistinguishable from a genuinely quiet fortnight.
    expect(document.querySelectorAll('[data-day-bar]')).toHaveLength(0)
  })

  it('keeps the loaded strip on screen when a refresh fails', async () => {
    // The banner case: SWR keeps the last good `data` while `error` is set, so
    // the parent keeps the fortnight they were looking at and is told it is
    // stale. Blanking it would lose a day at the presentation layer.
    const from = /* the page's 14-day window */ ''
    const stripKey = `/v1/children/kid/usage?from=${from}&to=${localToday()}&bucket=day&tz=${Intl.DateTimeFormat().resolvedOptions().timeZone}`

    vi.spyOn(api, 'get').mockImplementation(async (path: string) => {
      const url = new URL(path, 'http://x')
      if (url.searchParams.get('bucket') === 'day')
        throw fromProblem(
          { type: 't', title: 't', status: 502, detail: 'bad gateway', code: 'SZ-E901', ref: 'aa11bb' },
          { endpoint: path, httpStatus: 502 },
        )
      return usage('hour', url.searchParams.get('from')!, url.searchParams.get('to')!) as never
    })

    render(
      <MemoryRouter>
        <LocaleProvider>
          <SWRConfig
            value={{
              provider: () => new Map(),
              dedupingInterval: 0,
              // Stands in for "this already loaded once": SWR treats it as the
              // current data while the revalidation below fails.
              fallback: { [stripKey]: usage('day', from, localToday()) },
            }}
          >
            <ChildDetail childId="kid" />
          </SWRConfig>
        </LocaleProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByText(/SZ-E901 · aa11bb/)).toBeInTheDocument())
    expect(document.querySelectorAll('[data-day-bar]').length).toBeGreaterThan(0)
  })
```

Two details to settle while writing this: build `from` the way the page does
(`STRIP_DAYS - 1` days back from `localToday()`), and check what `DayStrip`
actually puts on each bar — if there is no `data-day-bar` attribute, add one
there rather than asserting on a class name, which is styling and will move.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd web && pnpm vitest run src/hygiene.test.ts`
Expected: FAIL on `./pages/ChildDetail.tsx does not read .detail`.

- [ ] **Step 3: Rewrite the error branches**

`ChildDetail.tsx` — the whole-page failure becomes inline, and the strip failure becomes a banner **only when the strip has previously loaded**; on a first load with no data it is inline in the strip's own footprint:

```tsx
  if (error) {
    return <ErrorPanel error={error as AppError} onRetry={() => void mutate()} />
  }
```

and in the strip section:

```tsx
      <section className="card p-6">
        {strip ? (
          <>
            {stripError && <ErrorPanel error={stripError as AppError} variant="banner" />}
            <DayStrip series={strip.series} from={from} to={today()} selected={selected} onSelect={setSelected} />
          </>
        ) : stripError ? (
          // Never zero-fill in place of a failed fetch: fourteen grey bars read
          // as a genuinely quiet fortnight, which is exactly the lost day this
          // app promises never to show.
          <ErrorPanel error={stripError as AppError} />
        ) : (
          <StripSkeleton />
        )}
      </section>
```

Take `mutate` from the page's `useSWR` calls so retry re-fetches rather than reloading the page.

`Children.tsx`:

```tsx
  if (error) {
    return <ErrorPanel error={error as AppError} onRetry={() => void mutate()} />
  }
```

`Login.tsx` — replace `useState<string | null>` with `useState<AppError | null>`:

```tsx
  const [error, setError] = useState<AppError | null>(null)
  // ...
    } catch (caught) {
      setError(caught instanceof AppError ? caught : unexpected(caught))
    } finally {
      setBusy(false)
    }
  // ...
        {error && <ErrorPanel error={error} />}
```

`SZ-E101` already says "That email or password is wrong" in four languages, so `t.login.wrongCredentials` and `t.login.unexpected` become dead.

- [ ] **Step 4: Remove the strings that no longer render**

From `web/src/i18n/types.ts` and all four locale files, delete:
- the whole `errors: { generic, notFound, offline }` block — `errorCopy` replaces it,
- `child.historyError`,
- `login.wrongCredentials` and `login.unexpected`.

Removing them from the type first makes the compiler list every locale file still carrying them, which is faster and safer than grepping.

- [ ] **Step 5: Run the tests**

Run: `cd web && pnpm vitest run && pnpm tsc -b --noEmit`
Expected: PASS. Any page test that asserted on the old English strings needs its expectation moved to the new copy — update the expectation, never the panel.

- [ ] **Step 6: Prove the banner rule is not vacuous**

In `ChildDetail.tsx`, change the strip branch so `stripError` returns the inline panel even when `strip` has data (i.e. put the `stripError ?` test first).
Expected: `keeps the loaded day on screen when a refresh fails` FAILS, because the chart is gone. Revert.

- [ ] **Step 7: Look at it**

Run `pnpm dev`, sign in, then stop the API. Check three things by eye: the whole-page failure fills the space its skeleton had without the layout jumping; a poll failure leaves the chart in place with a banner above it; the mono line is readable at arm's length.

- [ ] **Step 8: Commit**

```bash
git add web/src
git commit -F - <<'MSG'
fix: show every dashboard error with its code, in the reader's language

The server's English `detail` was rendered straight into a German UI at
ChildDetail.tsx:50. A failed poll blanked the day it was meant to report.

refs: SZ-ERRORS
MSG
```

---

### Task 5: Render crashes and unhandled rejections

**Files:**
- Create: `web/src/components/ErrorBoundary.tsx`, `web/src/components/ErrorBoundary.test.tsx`
- Modify: `web/src/main.tsx`, `web/vite.config.ts`

**Interfaces:**
- Consumes: `unexpected`, `AppError` (Task 1), `ErrorPanel` (Task 3).
- Produces: `<ErrorBoundary>`, `installGlobalErrorHandlers()`.

- [ ] **Step 1: Write the failing test**

`web/src/components/ErrorBoundary.test.tsx`:

```tsx
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/components/ErrorBoundary.test.tsx`
Expected: FAIL — cannot resolve `./ErrorBoundary`.

- [ ] **Step 3: Implement**

`web/src/components/ErrorBoundary.tsx`:

```tsx
import { Component, type ReactNode } from 'react'
import { AppError, unexpected } from '../api/errors'
import { ErrorPanel } from './ErrorPanel'

/**
 * A bug in the dashboard is still an error a parent should be able to report.
 * Without this, a render crash is a white page: nothing to read, nothing to
 * screenshot, nothing to say beyond "it broke".
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, { error: AppError | null }> {
  state: { error: AppError | null } = { error: null }

  static getDerivedStateFromError(cause: unknown) {
    return { error: unexpected(cause) }
  }

  render() {
    if (this.state.error) {
      return (
        <div className="mx-auto max-w-md p-6">
          <ErrorPanel error={this.state.error} onRetry={() => window.location.reload()} />
        </div>
      )
    }
    return this.props.children
  }
}

/**
 * The two failures React never sees: an async rejection nobody caught, and an
 * error thrown outside a component. Recorded rather than shown — the page is
 * still usable — so that the next visible error's copy block carries them.
 */
export function installGlobalErrorHandlers(target: Window = window) {
  target.addEventListener('unhandledrejection', (event) => {
    unexpected(event.reason)
  })
  target.addEventListener('error', (event) => {
    unexpected(event.error)
  })
}
```

`getDerivedStateFromError` builds the `AppError`, which records it — so the ordering the second test asserts holds without a separate `componentDidCatch`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/components/ErrorBoundary.test.tsx`
Expected: PASS, 3 tests.

- [ ] **Step 5: Mount it, and add the version**

In `web/src/main.tsx`, wrap the app in `<ErrorBoundary>` inside `<LocaleProvider>` (so the panel has its dictionary) and call `installGlobalErrorHandlers()` once at module scope.

In `web/vite.config.ts`, feed the version in:

```ts
import { readFileSync } from 'node:fs'

// Read rather than imported: an import attribute (`with { type: 'json' }`) is
// still uneven across the Node versions this repo is built on, and a config
// that fails to parse takes the whole build with it.
const { version } = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'))

export default defineConfig({
  // ...existing plugins, server and test blocks
  define: {
    // The version a parent reads out of the copy-details block. Without it the
    // report says "dev" and nobody can tell which build broke.
    'import.meta.env.VITE_APP_VERSION': JSON.stringify(version),
  },
})
```

- [ ] **Step 6: Run every gate**

Run: `just web-check`
Expected: PASS — including `gen-check` and `gen-copy-check`.

Run: `just rust-check`
Expected: PASS. `copy/errors.toml` changed in Task 1, so the catalog tests run again.

- [ ] **Step 7: Look at the built dashboard**

Run `pnpm build && pnpm preview`, then break the API and click through: sign-in failure, list failure, day failure, refresh failure. Read the four German strings out loud — this is the last point before they are what a parent sees.

- [ ] **Step 8: Commit**

```bash
git add web/src web/vite.config.ts
git commit -F - <<'MSG'
feat: a render crash becomes a reportable error, not a white page

refs: SZ-ERRORS
MSG
```

---

## What this plan deliberately leaves out

- iOS and Android. Their plans consume the same catalog and the `ErrorCopy.strings` / `error_copy.xml` the foundation already generates.
- Per-endpoint error typing in `openapi.json`. `Problem` is a registered component, which is what this plan needs; annotating every handler's error responses is a separate, mechanical change to `crates/server`.
- Persisting the ring buffer across reloads. In memory only, on purpose.
- A diagnostics screen listing past errors. The copy-details block already carries the last five.
