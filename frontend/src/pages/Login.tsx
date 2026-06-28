import { useState, type ReactNode } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../lib/api'
import { useAuth } from '../auth/AuthContext'
import { Icon } from '../components/Icon'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { CoyoteBadge, Habitat } from '../components/Coyote'

const REASON_NOTICE: Record<string, string> = {
  'signed-out': "You've been signed out.",
  expired: 'Your session expired. Please sign in again.',
}

function BrandPanel() {
  return (
    <div
      style={{
        flex: '0 0 46%',
        background: 'var(--forest-ink)',
        color: 'var(--text-on-dark)',
        padding: '44px 48px',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        minWidth: 0,
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0, pointerEvents: 'none' }}>
        <Habitat height={300} onDark opacity={0.9} />
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 11, position: 'relative' }}>
        <CoyoteBadge size={32} />
        <span style={{ fontFamily: 'var(--font-display)', fontSize: 21, fontWeight: 700, letterSpacing: '-0.01em' }}>
          Audit<span style={{ opacity: 0.6 }}>Yote</span>
        </span>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 420, position: 'relative' }}>
        <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 34, fontWeight: 600, lineHeight: 1.18, letterSpacing: '-0.01em', margin: 0 }}>
          Governance, risk &amp; compliance for security teams.
        </h1>
        <p style={{ fontSize: 15, lineHeight: 1.6, color: 'color-mix(in srgb, var(--text-on-dark) 72%, transparent)', margin: 0 }}>
          Log findings, map them to ISO 27001, OWASP and NIST controls, and route every decision through a reviewed,
          auditable workflow.
        </p>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 9, fontSize: 12.5, color: 'color-mix(in srgb, var(--text-on-dark) 60%, transparent)', position: 'relative' }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Icon name="shield-check" size={14} />Authorized access only — all activity is logged and audited.
        </span>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Icon name="check" size={14} />SOC 2 Type II · 256-bit TLS · SSO enforced
        </span>
      </div>
    </div>
  )
}

function Field({ label, error, children }: { label: string; error?: string | null; children: ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <label style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600, color: 'var(--text-strong)' }}>{label}</label>
      {children}
      {error ? (
        <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--critical-600)', display: 'flex', alignItems: 'center', gap: 5 }}>
          <Icon name="alert-triangle" size={12} />
          {error}
        </span>
      ) : null}
    </div>
  )
}

function LoginForm() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation() as { state?: { from?: { pathname?: string } } }
  const [params] = useSearchParams()
  const notice = REASON_NOTICE[params.get('reason') ?? '']

  const [email, setEmail] = useState('')
  const [pw, setPw] = useState('')
  const [touched, setTouched] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)

  const errors = {
    email: !email.trim()
      ? 'Enter your work email.'
      : !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)
        ? 'Enter a valid email address.'
        : null,
    pw: !pw ? 'Enter your password.' : null,
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setTouched(true)
    setServerError(null)
    if (errors.email || errors.pw) return
    setSubmitting(true)
    try {
      await login(email, pw)
      navigate(location.state?.from?.pathname ?? '/', { replace: true })
    } catch (err) {
      setServerError(
        err instanceof ApiError && err.status === 401
          ? 'Invalid email or password.'
          : 'Something went wrong. Please try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={submit} style={{ width: '100%', maxWidth: 348, display: 'flex', flexDirection: 'column', gap: 20 }}>
      {notice ? (
        <div
          role="status"
          style={{
            display: 'flex', alignItems: 'center', gap: 9, padding: '10px 12px', background: 'var(--surface-inset)',
            border: '1px solid var(--border-default)', borderRadius: 'var(--radius-sm)', fontSize: 'var(--fs-body-sm)', color: 'var(--text-body)',
          }}
        >
          <Icon name={params.get('reason') === 'expired' ? 'info' : 'log-out'} size={15} color="var(--text-muted)" />
          {notice}
        </div>
      ) : null}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 26, fontWeight: 600, color: 'var(--text-strong)', margin: 0, letterSpacing: '-0.01em' }}>
          Sign in
        </h2>
        <p style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-muted)', margin: 0 }}>
          Use your corporate credentials to continue.
        </p>
      </div>

      {serverError ? (
        <div
          role="alert"
          style={{
            display: 'flex', alignItems: 'center', gap: 9, padding: '10px 12px', background: 'var(--critical-100)',
            border: '1px solid var(--critical-500)', borderRadius: 'var(--radius-sm)', fontSize: 'var(--fs-body-sm)', color: 'var(--critical-600)',
          }}
        >
          <Icon name="alert-triangle" size={15} />
          {serverError}
        </div>
      ) : null}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Field label="Work email" error={touched ? errors.email : null}>
          <Input
            type="email"
            placeholder="you@bank.com"
            value={email}
            invalid={!!(touched && errors.email)}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
          />
        </Field>
        <Field label="Password" error={touched ? errors.pw : null}>
          <Input
            type="password"
            placeholder="••••••••••••"
            value={pw}
            invalid={!!(touched && errors.pw)}
            onChange={(e) => setPw(e.target.value)}
            autoComplete="current-password"
          />
        </Field>
      </div>

      <Button type="submit" variant="primary" fullWidth disabled={submitting}>
        {submitting ? 'Signing in…' : 'Sign in'}
      </Button>

      <p style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)', margin: 0, textAlign: 'center', lineHeight: 1.5 }}>
        Protected system. Unauthorized use is prohibited and monitored.
      </p>
    </form>
  )
}

export function Login() {
  return (
    <div className="cm-root" style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg-canvas)' }}>
      <BrandPanel />
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '32px 40px', minWidth: 0 }}>
        <LoginForm />
      </div>
    </div>
  )
}
