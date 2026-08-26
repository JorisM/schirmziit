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
 * The browser tells us very little here on purpose — a cross-origin failure, a
 * refused connection and a bad certificate are all one opaque `TypeError`,
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
 * failures across sessions would be storing data about a family that nobody
 * asked for.
 */
export function recentErrors(): readonly AppError[] {
  return log
}

export function clearErrorLog() {
  log = []
}

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
 * path with the host stripped in the constructor. The user agent is in here
 * deliberately — it is the difference between "a rendering bug" and "a
 * rendering bug on an eight-year-old iPad" — and this block only ever leaves
 * the machine when the reader chooses to paste it.
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
        `  ${entry.code} · ${entry.ref} · ${entry.at.toLocaleTimeString('sv-SE')} · ${
          where(entry) || '—'
        }`,
      )
    }
  }
  return lines.join('\n')
}
