/** Usuario y roles informados por la sesión del backend. */
export interface AuthenticatedUser {
  username: string
  roles: string[]
}

interface CsrfResponse {
  token: string
  headerName: string
}

interface ApiError {
  code: string
  message: string
}

let csrf: CsrfResponse | null = null

async function loadCsrf(): Promise<CsrfResponse> {
  const response = await fetch('/api/auth/csrf', { credentials: 'include' })
  if (!response.ok) {
    throw new Error('No fue posible preparar una conexión segura.')
  }
  csrf = (await response.json()) as CsrfResponse
  return csrf
}

/**
 * Centraliza las llamadas HTTP, adjunta CSRF en escrituras y normaliza los
 * errores del backend para que las pantallas no repitan esa lógica.
 */
async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = options.method?.toUpperCase() ?? 'GET'
  const headers = new Headers(options.headers)

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const currentCsrf = csrf ?? (await loadCsrf())
    headers.set(currentCsrf.headerName, currentCsrf.token)
  }

  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'include',
  })

  if (!response.ok) {
    const fallback = 'Ocurrió un problema. Intenta nuevamente.'
    let message = fallback
    let payload: unknown
    try {
      const error = (await response.json()) as ApiError
      payload = error
      message = error.message || fallback
    } catch {
      message = fallback
    }
    throw new ApiRequestError(message, response.status, payload)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

/** Error HTTP tipado que conserva el estado y la respuesta estructurada. */
export class ApiRequestError extends Error {
  constructor(message: string, readonly status: number, readonly payload?: unknown) {
    super(message)
  }
}

export { apiRequest }

/** Operaciones remotas relacionadas con la sesión de usuario. */
export const authApi = {
  prepare: loadCsrf,
  me: () => apiRequest<AuthenticatedUser>('/api/auth/me'),
  login: (username: string, password: string) =>
    apiRequest<AuthenticatedUser>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  logout: async () => {
    await apiRequest<void>('/api/auth/logout', { method: 'POST' })
    csrf = null
    await loadCsrf()
  },
}
