/**
 * Tiny REST client for the ControlMap API. Same-origin (`/api`, via the Vite dev proxy or the prod
 * reverse proxy), so the session cookie rides along with `credentials: 'include'`. For unsafe
 * methods it echoes the CSRF token from the readable `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN`
 * header (priming it with a GET first if needed). Non-2xx responses throw {@link ApiError}.
 */
const BASE = '/api'

export interface ApiErrorBody {
  timestamp?: string
  status: number
  error: string
  message: string
  fieldErrors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  readonly status: number
  readonly body: ApiErrorBody | null
  constructor(status: number, body: ApiErrorBody | null) {
    super(body?.message ?? `Request failed (${status})`)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]+)'))
  return match ? decodeURIComponent(match[1]) : null
}

async function csrfToken(): Promise<string | null> {
  let token = readCookie('XSRF-TOKEN')
  if (!token) {
    // Any GET makes the backend set the XSRF-TOKEN cookie; prime it before the first mutation.
    await fetch(`${BASE}/health`, { credentials: 'include' })
    token = readCookie('XSRF-TOKEN')
  }
  return token
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  if (method !== 'GET' && method !== 'HEAD') {
    const token = await csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const res = await fetch(`${BASE}${path}`, {
    method,
    credentials: 'include',
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) throw new ApiError(res.status, (data as ApiErrorBody) ?? null)
  return data as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
}
