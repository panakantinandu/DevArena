/**
 * Thin fetch wrapper. All requests go to the same origin (Vite proxy in dev, Vercel rewrite in
 * production), so the HttpOnly session cookie is first-party and never touched by JS.
 * Unsafe methods echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header (Spring Security SPA mode).
 */
const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? ''

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }

  /** The backend couldn't be reached at all (offline, or a free-tier instance waking up). */
  get isNetwork() {
    return this.status === 0
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(name + '='))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

let csrfPrimed: Promise<void> | null = null

async function ensureCsrf() {
  if (readCookie('XSRF-TOKEN')) return
  csrfPrimed ??= fetch(`${API_BASE}/api/auth/csrf`, { credentials: 'include' }).then(
    () => undefined,
    () => undefined,
  )
  await csrfPrimed
  csrfPrimed = null
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    await ensureCsrf()
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let res: Response
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK', "Can't reach DevArena right now. The server may be waking up; retrying shortly.")
  }

  if (res.status === 204) return undefined as T
  const text = await res.text()
  const data = text ? safeJson(text) : undefined

  if (!res.ok) {
    // Render/Vercel return HTML for 502/503 while an instance boots.
    if (res.status >= 502 && res.status <= 504) {
      throw new ApiError(0, 'UNAVAILABLE', 'The server is starting up. This can take up to a minute on the free tier.')
    }
    const err = (data ?? {}) as { code?: string; message?: string; fieldErrors?: Record<string, string> }
    throw new ApiError(res.status, err.code ?? 'ERROR', err.message ?? 'Something went wrong', err.fieldErrors ?? {})
  }
  return data as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  delete: <T>(path: string) => request<T>('DELETE', path),
}

export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message
  if (err instanceof Error) return err.message
  return 'Something went wrong'
}
