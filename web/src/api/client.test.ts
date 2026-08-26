import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, apiUrl } from './client'

function stubFetch(response: Response) {
  const fetchMock = vi.fn<typeof fetch>(async () => response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('apiUrl', () => {
  it('leaves the path alone when no base is configured', () => {
    // The self-hosted default: one binary serves the dashboard and the API from
    // one origin, so a relative path is correct and no CORS is involved.
    expect(apiUrl('/v1/me', '')).toBe('/v1/me')
  })

  it('prefixes the configured API base', () => {
    expect(apiUrl('/v1/me', 'https://api.schirmziit.ch')).toBe('https://api.schirmziit.ch/v1/me')
  })

  it('does not double the slash when the base carries a trailing one', () => {
    // A trailing slash in the env var is the easiest mistake to make, and
    // `//v1/me` is a 404 that looks like a routing bug.
    expect(apiUrl('/v1/me', 'https://api.schirmziit.ch/')).toBe('https://api.schirmziit.ch/v1/me')
  })
})

describe('request', () => {
  it('sends credentials cross-origin', async () => {
    // `same-origin` was correct while the dashboard and API shared a host. On
    // the split hosts it silently drops the session cookie and every call 401s.
    const fetchMock = stubFetch(json({ id: 'x' }))
    await api.get('/v1/me')

    expect(fetchMock.mock.calls[0]?.[1]?.credentials).toBe('include')
  })

  it('throws on a non-JSON error body instead of reading it as success', async () => {
    // A captcha or proxy page must never look like an answer.
    stubFetch(new Response('<html>challenge</html>', { status: 403 }))
    // And it names the case, so the panel can say "often a guest Wi-Fi with a
    // login page" rather than shrugging.
    await expect(api.get('/v1/me')).rejects.toMatchObject({ code: 'SZ-E504' })
  })
})
