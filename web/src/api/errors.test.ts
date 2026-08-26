import { beforeEach, describe, expect, it } from 'vitest'
import {
  AppError,
  clearErrorLog,
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
