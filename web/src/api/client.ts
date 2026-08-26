import { AppError, badResponseBody, fromProblem, fromTransport, type Problem } from './errors'

export { AppError }
export type { Problem }

/**
 * Where the API lives, relative to the page.
 *
 * Empty is the self-hosted shape: one binary serves this dashboard and the API
 * from the same origin, so relative paths are right and no CORS is in play.
 * Hosted, the dashboard is on `app.` and the API on `api.`, and this is set at
 * build time to the latter.
 */
const API_BASE: string = import.meta.env.VITE_API_BASE ?? ''

export function apiUrl(path: string, base: string = API_BASE): string {
  // A trailing slash in the env var would produce `//v1/me` — a 404 that reads
  // like a routing bug rather than a typo in a deployment variable.
  return base ? `${base.replace(/\/$/, '')}${path}` : path
}

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

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
}
