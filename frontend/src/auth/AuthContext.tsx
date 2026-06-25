import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from '../lib/api'

export interface User {
  id: number
  email: string
  name: string
  /** Canonical role name from the API: 'ANALYST' | 'REVIEWER'. */
  role: string
}

type Status = 'loading' | 'authenticated' | 'anonymous'

interface AuthContextValue {
  user: User | null
  status: Status
  login: (email: string, password: string) => Promise<User>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Holds the signed-in user. On mount it probes GET /api/auth/me (which also primes the CSRF
 * cookie); a 401 simply means anonymous. login/logout update the state after the server call.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [status, setStatus] = useState<Status>('loading')

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

  const login = useCallback(async (email: string, password: string) => {
    const u = await api.post<User>('/auth/login', { email, password })
    setUser(u)
    setStatus('authenticated')
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

  return <AuthContext.Provider value={{ user, status, login, logout }}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
