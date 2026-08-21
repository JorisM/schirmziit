export type ApiProblem = {
  type: string
  title: string
  status: number
  detail: string
}

export class ApiError extends Error {
  constructor(readonly problem: ApiProblem) {
    super(problem.detail)
    this.name = 'ApiError'
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method,
    headers: body ? { 'content-type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as ApiProblem | null
    throw new ApiError(
      problem ?? {
        type: 'about:blank',
        title: 'error',
        status: response.status,
        detail: response.statusText,
      },
    )
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
}
