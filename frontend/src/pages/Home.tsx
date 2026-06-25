import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { CoyoteLockup } from '../components/Coyote'

/**
 * Temporary signed-in landing for sub-step 8b — proves the auth round-trip (login → session → /me)
 * and sign-out. Replaced by the real AppShell + dashboard in sub-step 8c.
 */
export function Home() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function signOut() {
    await logout()
    navigate('/login?reason=signed-out', { replace: true })
  }

  return (
    <div className="cm-root" style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 'var(--space-9)' }}>
      <div className="bg-surface-card border border-subtle rounded-md shadow-sm" style={{ maxWidth: 460, width: '100%', padding: 'var(--space-8)' }}>
        <div style={{ marginBottom: 'var(--space-6)' }}>
          <CoyoteLockup />
        </div>
        <p className="cm-eyebrow">Signed in</p>
        <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h2)', margin: 'var(--space-2) 0 var(--space-5)' }}>
          Welcome, {user?.name}
        </h1>
        <dl style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 'var(--space-3) var(--space-6)', margin: 0, marginBottom: 'var(--space-7)' }}>
          <dt className="text-muted" style={{ fontSize: 'var(--fs-body-sm)' }}>Email</dt>
          <dd className="font-mono text-strong" style={{ margin: 0, fontSize: 'var(--fs-body-sm)' }}>{user?.email}</dd>
          <dt className="text-muted" style={{ fontSize: 'var(--fs-body-sm)' }}>Role</dt>
          <dd className="text-strong" style={{ margin: 0, fontSize: 'var(--fs-body-sm)' }}>{user?.role}</dd>
        </dl>
        <Button variant="secondary" iconLeft="log-out" onClick={signOut}>
          Sign out
        </Button>
      </div>
    </div>
  )
}
