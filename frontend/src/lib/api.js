/**
 * HTTP client for the SmartHire REST API.
 *
 * The browser talks to Java Spring Boot only (never to the Python service).
 * Access tokens live in memory; the refresh token lives in localStorage so a page
 * reload can silently restore the session.
 */

const BASE = import.meta.env.VITE_API_URL || ''

let accessToken = null
let refreshPromise = null

const REFRESH_KEY = 'smarthire.refresh'

export class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors || {}
  }
}

export function setAccessToken(token) {
  accessToken = token || null
  if (token) {
    // kept only so a reload can re-authenticate silently
    sessionStorage.setItem('smarthire.access', token)
  } else {
    sessionStorage.removeItem('smarthire.access')
  }
}

export function getAccessToken() {
  if (!accessToken) {
    accessToken = sessionStorage.getItem('smarthire.access')
  }
  return accessToken
}

export function setRefreshToken(token) {
  if (token) localStorage.setItem(REFRESH_KEY, token)
  else localStorage.removeItem(REFRESH_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}

export function clearTokens() {
  accessToken = null
  sessionStorage.removeItem('smarthire.access')
  localStorage.removeItem(REFRESH_KEY)
}

async function toApiError(response) {
  const raw = await response.text()
  let payload = null
  if (raw) {
    try {
      payload = JSON.parse(raw)
    } catch {
      // Spring's CORS rejection and other proxies answer with plain text
      payload = null
    }
  }
  const message =
    payload?.message ||
    payload?.error ||
    (raw && raw.length < 300 ? raw : '') ||
    `Request failed (${response.status})`
  return new ApiError(response.status, message, payload?.fieldErrors)
}

async function tryRefresh() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const response = await fetch(`${BASE}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!response.ok) {
        clearTokens()
        return false
      }
      const data = await response.json()
      setAccessToken(data.accessToken)
      setRefreshToken(data.refreshToken)
      return true
    })().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

export async function api(path, { method = 'GET', body, formData, signal } = {}) {
  const headers = { Accept: 'application/json' }
  let payload

  if (formData) {
    payload = formData
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }

  const token = getAccessToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: payload,
    signal,
  })

  // one silent refresh, then replay the original request
  if (response.status === 401 && getRefreshToken()) {
    const refreshed = await tryRefresh()
    if (refreshed) {
      headers.Authorization = `Bearer ${getAccessToken()}`
      response = await fetch(`${BASE}${path}`, { method, headers, body: payload, signal })
    }
  }

  if (response.status === 204) return null
  if (!response.ok) throw await toApiError(response)
  if (response.headers.get('content-type')?.includes('application/json')) {
    return response.json()
  }
  return response.text()
}

export const apiGet = (path, opts) => api(path, { ...opts, method: 'GET' })
export const apiPost = (path, body, opts) => api(path, { ...opts, method: 'POST', body })
export const apiPut = (path, body, opts) => api(path, { ...opts, method: 'PUT', body })
export const apiPatch = (path, body, opts) => api(path, { ...opts, method: 'PATCH', body })
export const apiDelete = (path, opts) => api(path, { ...opts, method: 'DELETE' })
