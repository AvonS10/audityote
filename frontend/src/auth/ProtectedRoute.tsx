import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

/**
 * Gates routes behind authentication. While the initial /me probe is in flight it renders nothing;
 * an anonymous user is redirected to /login (remembering where they were headed). This is UI
 * convenience only — the real authorization is enforced server-side by Spring Security.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="cm-root" style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <span style={{ color: 'var(--text-muted)', fontSize: 'var(--fs-body-sm)' }}>Loading…</span>
      </div>
    )
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <>{children}</>
}
