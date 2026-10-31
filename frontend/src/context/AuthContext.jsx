import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { apiGet, apiPost, clearTokens, getRefreshToken, setAccessToken, setRefreshToken } from '../lib/api.js'

const AuthContext = createContext(null)

/**
 * Session state for the whole app.
 * - `user` mirrors GET /api/auth/me
 * - on mount we silently restore a session from the stored refresh token
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [booting, setBooting] = useState(true)

  const loadMe = useCallback(async () => {
    const me = await apiGet('/api/auth/me')
    setUser(me)
    return me
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (getRefreshToken()) {
        try {
          await loadMe()
        } catch {
          clearTokens()
          if (!cancelled) setUser(null)
        }
      }
      if (!cancelled) setBooting(false)
    })()
    return () => {
      cancelled = true
    }
  }, [loadMe])

  const applySession = useCallback((data) => {
    setAccessToken(data.accessToken)
    setRefreshToken(data.refreshToken)
    setUser(data.user)
    return data.user
  }, [])

  const login = useCallback(
    async (email, password) => applySession(await apiPost('/api/auth/login', { email, password })),
    [applySession],
  )

  const register = useCallback(
    async (payload) => applySession(await apiPost('/api/auth/register', payload)),
    [applySession],
  )

  const logout = useCallback(() => {
    clearTokens()
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({
      user,
      booting,
      login,
      register,
      logout,
      reloadUser: loadMe,
      isAuthenticated: Boolean(user),
      role: user?.role || null,
      isStaff: user?.role === 'ADMIN' || user?.role === 'RECRUITER',
      isAdmin: user?.role === 'ADMIN',
      isCandidate: user?.role === 'CANDIDATE',
    }),
    [user, booting, login, register, logout, loadMe],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>')
  return context
}
