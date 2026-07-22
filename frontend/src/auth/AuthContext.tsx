import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler } from '../lib/api'

export interface User {
  id: number
  email: string
  name: string
  /** Canonical role name from the API: 'ANALYST' | 'REVIEWER'. */
  role: string
  /** True for a published public demo account: self-service profile/password changes are disabled server-side. */
  demo?: boolean
}

type Status = 'loading' | 'authenticated' | 'anonymous'

interface AuthContextValue {
  user: User | null
  status: Status
  /** True when the session expired mid-use (a 401 on an authenticated request) → ?reason=expired. */
  expired: boolean
  login: (email: string, password: string) => Promise<User>
  /** Self-service registration (#reg): creates the account, auto-logs-in, returns the new user. */
  register: (name: string, email: string, password: string) => Promise<User>
  logout: () => Promise<void>
  /** Re-fetch the signed-in user (e.g. after editing the profile) so the chrome reflects it. */
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Holds the signed-in user. On mount it probes GET /api/auth/me (which also primes the CSRF
 * cookie); a 401 simply means anonymous. A later 401 on an authenticated call flips `expired` so
 * the SPA can redirect to login?reason=expired. login/logout update the state after the server call.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [status, setStatus] = useState<Status>('loading')
  const [expired, setExpired] = useState(false)

  useEffect(() => {
    let active = true
    api
      .get<User>('/auth/me')
      .then((u) => active && (setUser(u), setStatus('authenticated')))
      .catch(() => active && (setUser(null), setStatus('anonymous')))
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null)
      setStatus('anonymous')
      setExpired(true)
    })
    return () => setUnauthorizedHandler(null)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const u = await api.post<User>('/auth/login', { email, password })
    setUser(u)
    setStatus('authenticated')
    setExpired(false)
    return u
  }, [])

  const register = useCallback(async (name: string, email: string, password: string) => {
    const u = await api.post<User>('/auth/register', { name, email, password })
    setUser(u)
    setStatus('authenticated')
    setExpired(false)
    return u
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout')
    } finally {
      setUser(null)
      setStatus('anonymous')
    }
  }, [])

  const refresh = useCallback(async () => {
    try {
      setUser(await api.get<User>('/auth/me'))
    } catch {
      // Leave the current user in place; a 401 is handled by the unauthorized handler above.
    }
  }, [])

  return <AuthContext.Provider value={{ user, status, expired, login, register, logout, refresh }}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
