import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../lib/api'
import { Icon } from '../components/Icon'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { AuthField, BrandPanel } from './Login'

function RegisterForm() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [pw, setPw] = useState('')
  const [confirm, setConfirm] = useState('')
  const [touched, setTouched] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)

  const errors = {
    name: !name.trim() ? 'Enter your name.' : null,
    email: !email.trim()
      ? 'Enter your work email.'
      : !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)
        ? 'Enter a valid email address.'
        : null,
    pw: pw.length < 8 ? 'Use at least 8 characters.' : null,
    confirm: confirm !== pw ? 'Passwords do not match.' : null,
  }
  const hasError = Object.values(errors).some(Boolean)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setTouched(true)
    setServerError(null)
    if (hasError) return
    setSubmitting(true)
    try {
      await register(name.trim(), email.trim(), pw)
      navigate('/', { replace: true })
    } catch (err) {
      // The server returns a clear message for a disallowed domain (403) or a taken email (409).
      setServerError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={submit} style={{ width: '100%', maxWidth: 348, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 26, fontWeight: 600, color: 'var(--text-strong)', margin: 0, letterSpacing: '-0.01em' }}>
          Create your account
        </h2>
        <p style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-muted)', margin: 0 }}>
          Register with your company email to get started.
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
        <AuthField label="Full name" error={touched ? errors.name : null}>
          <Input value={name} invalid={!!(touched && errors.name)} onChange={(e) => setName(e.target.value)} autoComplete="name" maxLength={120} />
        </AuthField>
        <AuthField label="Work email" error={touched ? errors.email : null}>
          <Input type="email" placeholder="you@company.com" value={email} invalid={!!(touched && errors.email)} onChange={(e) => setEmail(e.target.value)} autoComplete="username" />
        </AuthField>
        <AuthField label="Password" error={touched ? errors.pw : null}>
          <Input type="password" placeholder="At least 8 characters" value={pw} invalid={!!(touched && errors.pw)} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" />
        </AuthField>
        <AuthField label="Confirm password" error={touched ? errors.confirm : null}>
          <Input type="password" value={confirm} invalid={!!(touched && errors.confirm)} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" />
        </AuthField>
      </div>

      <Button type="submit" variant="primary" fullWidth disabled={submitting}>
        {submitting ? 'Creating account…' : 'Create account'}
      </Button>

      <p style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-muted)', margin: 0, textAlign: 'center' }}>
        Already have an account?{' '}
        <Link to="/login" style={{ color: 'var(--primary)', fontWeight: 600 }}>Sign in</Link>
      </p>
    </form>
  )
}

export function Register() {
  return (
    <div className="cm-root" style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg-canvas)' }}>
      <BrandPanel />
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '32px 40px', minWidth: 0 }}>
        <RegisterForm />
      </div>
    </div>
  )
}
