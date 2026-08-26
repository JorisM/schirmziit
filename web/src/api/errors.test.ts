import { beforeEach, describe, expect, it } from 'vitest'
import {
  AppError,
  clearErrorLog,
  copyDetails,
  fromProblem,
  fromTransport,
  recentErrors,
  unexpected,
} from './errors'

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
    const error = unexpected(new Error('boom'), {
      endpoint: 'https://home.example.ch/v1/children',
    })
    expect(error.endpoint).toBe('/v1/children')
    expect(error.code).toBe('SZ-E707')
  })
})

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
      {
        type: 't',
        title: 't',
        status: 404,
        detail: 'anna@example.ch asked for Mia',
        code: 'SZ-E201',
        ref: 'abc123',
      },
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
