import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../lib/api'
import { changeUserRole, getUsers, resetUserPassword, setUserActive, type UserSummary } from '../lib/users'
import { Avatar } from '../components/Avatar'
import { Icon } from '../components/Icon'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Select } from '../components/ui/Select'
import { RoleChip } from '../components/app/RoleChip'
import { ConfirmDialog } from '../components/feedback/ConfirmDialog'
import { useToast } from '../components/feedback/ToastProvider'

const ROLE_OPTIONS = [
  { value: 'ANALYST', label: 'Analyst' },
  { value: 'REVIEWER', label: 'Reviewer' },
  { value: 'ADMIN', label: 'Admin' },
]

function genTempPassword(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
  return Array.from({ length: 14 }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
}

function StatusBadge({ active }: { active: boolean }) {
  return (
    <span
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 5, height: 20, padding: '0 9px', borderRadius: 'var(--radius-pill)',
        fontSize: 'var(--fs-caption)', fontWeight: 600,
        background: active ? 'var(--status-approved-bg)' : 'var(--surface-inset)',
        color: active ? 'var(--status-approved)' : 'var(--text-muted)',
      }}
    >
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: active ? 'var(--status-approved)' : 'var(--text-faint)' }} />
      {active ? 'Active' : 'Inactive'}
    </span>
  )
}

function ResetPasswordDialog({ user, busy, onConfirm, onCancel }: { user: UserSummary; busy: boolean; onConfirm: (pw: string) => void; onCancel: () => void }) {
  const [pw, setPw] = useState(() => genTempPassword())
  return (
    <div role="dialog" aria-modal="true" style={{ position: 'fixed', inset: 0, zIndex: 80, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'color-mix(in srgb, var(--forest-ink) 38%, transparent)' }}>
      <div className="bg-surface-card border border-default rounded-md shadow-pop" style={{ width: 440, padding: 20, display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
          <Icon name="shield" size={18} color="var(--text-muted)" />
          <h2 className="font-display text-strong" style={{ fontSize: 'var(--fs-h3)', fontWeight: 600, margin: 0 }}>Reset password</h2>
        </div>
        <p className="text-muted" style={{ fontSize: 'var(--fs-body-sm)', margin: 0, lineHeight: 1.5 }}>
          Set a temporary password for <b className="text-strong">{user.name}</b> ({user.email}). Share it securely; they can change it in Account settings.
        </p>
        <div style={{ display: 'flex', gap: 8 }}>
          <Input value={pw} onChange={(e) => setPw(e.target.value)} style={{ flex: 1 }} />
          <Button variant="secondary" onClick={() => setPw(genTempPassword())}>Generate</Button>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, paddingTop: 2 }}>
          <Button variant="ghost" onClick={onCancel} disabled={busy}>Cancel</Button>
          <Button variant="primary" onClick={() => onConfirm(pw)} disabled={busy || pw.length < 8}>
            {busy ? 'Resetting…' : 'Reset password'}
          </Button>
        </div>
      </div>
    </div>
  )
}

export function UsersAdmin() {
  const { user: me } = useAuth()
  const { toast } = useToast()

  const [users, setUsers] = useState<UserSummary[]>([])
  const [load, setLoad] = useState<'loading' | 'ready' | 'error'>('loading')
  const [busyId, setBusyId] = useState<number | null>(null)
  const [confirmDeactivate, setConfirmDeactivate] = useState<UserSummary | null>(null)
  const [resetFor, setResetFor] = useState<UserSummary | null>(null)

  function fetchUsers() {
    setLoad('loading')
    getUsers()
      .then((u) => { setUsers(u); setLoad('ready') })
      .catch(() => setLoad('error'))
  }
  useEffect(fetchUsers, [])

  async function mutate(id: number, promise: Promise<unknown>, successTitle: string) {
    setBusyId(id)
    try {
      await promise
      await new Promise((r) => setTimeout(r, 0))
      fetchUsers()
      toast({ tone: 'success', title: successTitle })
    } catch (err) {
      toast({ tone: 'error', title: 'Action failed', message: err instanceof ApiError ? err.message : 'Something went wrong.' })
    } finally {
      setBusyId(null)
    }
  }

  function onRoleChange(u: UserSummary, role: string) {
    if (role !== u.role) mutate(u.id, changeUserRole(u.id, role), 'Role updated')
  }

  function onToggleActive(u: UserSummary) {
    if (u.active) setConfirmDeactivate(u)
    else mutate(u.id, setUserActive(u.id, true), 'User reactivated')
  }

  function doDeactivate() {
    const u = confirmDeactivate
    setConfirmDeactivate(null)
    if (u) mutate(u.id, setUserActive(u.id, false), 'User deactivated')
  }

  function doResetPassword(pw: string) {
    const u = resetFor
    if (!u) return
    setBusyId(u.id)
    resetUserPassword(u.id, pw)
      .then(() => {
        setResetFor(null)
        toast({ tone: 'success', title: 'Password reset', message: `Temporary password for ${u.email}: ${pw}` })
      })
      .catch((err) => toast({ tone: 'error', title: 'Reset failed', message: err instanceof ApiError ? err.message : 'Something went wrong.' }))
      .finally(() => setBusyId(null))
  }

  const td = { padding: '0 16px', height: 'var(--row-h)', borderBottom: '1px solid var(--border-subtle)', verticalAlign: 'middle' } as const

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 1080 }}>
      <div>
        <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', fontWeight: 600, margin: 0, letterSpacing: '-0.01em' }}>Users</h1>
        <p className="text-muted" style={{ fontSize: 'var(--fs-body-sm)', margin: '4px 0 0' }}>Manage roles and access. Deactivating a user revokes their access immediately.</p>
      </div>

      {load === 'loading' ? (
        <div className="text-muted" style={{ padding: 'var(--space-9)', textAlign: 'center', fontSize: 'var(--fs-body-sm)' }}>Loading…</div>
      ) : load === 'error' ? (
        <div role="alert" style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', background: 'var(--critical-100)', border: '1px solid var(--critical-500)', borderRadius: 'var(--radius-md)', color: 'var(--critical-600)', fontSize: 'var(--fs-body-sm)' }}>
          <Icon name="alert-triangle" size={16} /><span style={{ flex: 1 }}>Couldn't load users.</span>
          <Button size="sm" variant="secondary" onClick={fetchUsers}>Retry</Button>
        </div>
      ) : (
        <div className="bg-surface-card border border-subtle rounded-md shadow-xs" style={{ overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 860 }}>
              <thead>
                <tr>
                  {['User', 'Role', 'Status', 'Actions'].map((h) => (
                    <th key={h} className="cm-eyebrow" style={{ textAlign: 'left', padding: '0 16px', height: 38, color: 'var(--text-muted)', background: 'var(--surface-inset)', borderBottom: '1px solid var(--border-default)', whiteSpace: 'nowrap' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {users.map((u) => {
                  const isSelf = me?.email === u.email
                  const rowBusy = busyId === u.id
                  return (
                    <tr key={u.id} style={{ background: 'var(--surface-card)', opacity: u.active ? 1 : 0.7 }}>
                      <td style={td}>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
                          <Avatar name={u.name} size={28} />
                          <span style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                            <span className="text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>
                              {u.name}{isSelf ? <span className="text-faint" style={{ fontWeight: 400 }}> (you)</span> : null}
                            </span>
                            <span className="font-mono text-faint" style={{ fontSize: 11 }}>{u.email}</span>
                          </span>
                        </span>
                      </td>
                      <td style={td}>
                        {isSelf ? (
                          <RoleChip role={u.role} />
                        ) : (
                          <Select value={u.role} options={ROLE_OPTIONS} disabled={rowBusy} onChange={(e) => onRoleChange(u, e.target.value)} />
                        )}
                      </td>
                      <td style={td}><StatusBadge active={u.active} /></td>
                      <td style={td}>
                        <span style={{ display: 'inline-flex', gap: 8 }}>
                          <Button size="sm" variant="secondary" disabled={rowBusy} onClick={() => setResetFor(u)}>Reset password</Button>
                          <Button
                            size="sm"
                            variant={u.active ? 'danger' : 'secondary'}
                            disabled={rowBusy || isSelf}
                            title={isSelf ? "You can't deactivate your own account" : undefined}
                            onClick={() => onToggleActive(u)}
                          >
                            {u.active ? 'Deactivate' : 'Reactivate'}
                          </Button>
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={!!confirmDeactivate}
        title="Deactivate user?"
        body={confirmDeactivate ? `${confirmDeactivate.name} (${confirmDeactivate.email}) will be signed out and unable to log in until reactivated. Their account and history are preserved.` : ''}
        confirmLabel="Deactivate"
        icon="log-out"
        onConfirm={doDeactivate}
        onCancel={() => setConfirmDeactivate(null)}
      />

      {resetFor ? (
        <ResetPasswordDialog user={resetFor} busy={busyId === resetFor.id} onConfirm={doResetPassword} onCancel={() => setResetFor(null)} />
      ) : null}
    </div>
  )
}
