import { useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { changePassword, updateProfile } from '../lib/account'
import { ApiError } from '../lib/api'
import { Icon, type IconName } from '../components/Icon'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { RoleChip } from '../components/app/RoleChip'
import { useToast } from '../components/feedback/ToastProvider'

function Card({ title, icon, children }: { title: string; icon: IconName; children: ReactNode }) {
  return (
    <section className="bg-surface-card border border-subtle rounded-md shadow-xs" style={{ overflow: 'hidden' }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '13px 18px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--surface-inset)' }}>
        <Icon name={icon} size={16} color="var(--text-muted)" />
        <h2 className="font-display text-strong" style={{ fontSize: 'var(--fs-h3)', fontWeight: 600, margin: 0 }}>{title}</h2>
      </header>
      <div style={{ padding: 18, display: 'flex', flexDirection: 'column', gap: 16 }}>{children}</div>
    </section>
  )
}

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, maxWidth: 420 }}>
      <span className="text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>
        {label}
        {hint ? <span className="text-faint" style={{ fontWeight: 400, fontSize: 'var(--fs-caption)' }}> — {hint}</span> : null}
      </span>
      {children}
    </label>
  )
}

function ReadOnlyRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxWidth: 420 }}>
      <span className="cm-eyebrow text-faint">{label}</span>
      <span style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-body)', display: 'flex', alignItems: 'center', gap: 8 }}>{children}</span>
    </div>
  )
}

export function AccountSettings() {
  const { user, refresh } = useAuth()
  const { toast } = useToast()

  // Published public demo accounts are locked out of self-service edits (enforced server-side with a
  // 403; this only mirrors that so the controls read as disabled instead of failing on submit).
  const locked = user?.demo === true

  const [name, setName] = useState(user?.name ?? '')
  const [savingName, setSavingName] = useState(false)

  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [pwError, setPwError] = useState<string | null>(null)
  const [savingPw, setSavingPw] = useState(false)

  const nameDirty = name.trim() !== '' && name.trim() !== (user?.name ?? '')

  async function saveName() {
    if (!nameDirty || locked) return
    setSavingName(true)
    try {
      await updateProfile(name.trim())
      await refresh()
      toast({ tone: 'success', title: 'Profile updated' })
    } catch (err) {
      toast({ tone: 'error', title: 'Could not update profile', message: err instanceof ApiError ? err.message : 'Something went wrong.' })
    } finally {
      setSavingName(false)
    }
  }

  async function savePassword() {
    if (locked) return
    setPwError(null)
    if (next.length < 8) {
      setPwError('New password must be at least 8 characters.')
      return
    }
    if (next !== confirm) {
      setPwError('New password and confirmation do not match.')
      return
    }
    setSavingPw(true)
    try {
      await changePassword(current, next)
      setCurrent('')
      setNext('')
      setConfirm('')
      toast({ tone: 'success', title: 'Password changed' })
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Something went wrong.'
      setPwError(msg)
    } finally {
      setSavingPw(false)
    }
  }

  return (
    <div style={{ maxWidth: 760, display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div>
        <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', fontWeight: 600, margin: 0, letterSpacing: '-0.01em' }}>Account settings</h1>
        <p className="text-muted" style={{ fontSize: 'var(--fs-body-sm)', margin: '4px 0 0' }}>Manage your profile and password.</p>
      </div>

      {locked ? (
        <div
          role="status"
          style={{
            display: 'flex', alignItems: 'center', gap: 9, padding: '10px 12px', background: 'var(--surface-inset)',
            border: '1px solid var(--border-default)', borderRadius: 'var(--radius-sm)', fontSize: 'var(--fs-body-sm)', color: 'var(--text-body)',
          }}
        >
          <Icon name="shield" size={15} color="var(--text-muted)" />
          This is a shared demo account. Profile and password changes are disabled so it stays available for everyone.
        </div>
      ) : null}

      <Card title="Profile" icon="settings">
        <Field label="Display name">
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name" maxLength={120} disabled={locked} />
        </Field>
        <ReadOnlyRow label="Email">
          <span className="font-mono text-strong">{user?.email}</span>
        </ReadOnlyRow>
        <ReadOnlyRow label="Role">
          <RoleChip role={user?.role ?? ''} />
          <span className="text-faint" style={{ fontSize: 'var(--fs-caption)' }}>Managed by your organization</span>
        </ReadOnlyRow>
        <div>
          <Button variant="primary" disabled={!nameDirty || savingName || locked} onClick={saveName}>
            {savingName ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      </Card>

      <Card title="Password" icon="shield">
        <Field label="Current password">
          <Input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} disabled={locked} />
        </Field>
        <Field label="New password" hint="at least 8 characters">
          <Input type="password" autoComplete="new-password" value={next} invalid={!!pwError} onChange={(e) => { setNext(e.target.value); setPwError(null) }} disabled={locked} />
        </Field>
        <Field label="Confirm new password">
          <Input type="password" autoComplete="new-password" value={confirm} invalid={!!pwError} onChange={(e) => { setConfirm(e.target.value); setPwError(null) }} disabled={locked} />
        </Field>
        {pwError ? (
          <span role="alert" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 'var(--fs-caption)', color: 'var(--critical-600)' }}>
            <Icon name="alert-triangle" size={13} />{pwError}
          </span>
        ) : null}
        <div>
          <Button variant="primary" disabled={savingPw || !current || !next || !confirm || locked} onClick={savePassword}>
            {savingPw ? 'Changing…' : 'Change password'}
          </Button>
        </div>
      </Card>
    </div>
  )
}
