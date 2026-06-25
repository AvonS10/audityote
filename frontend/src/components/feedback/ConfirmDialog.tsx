import { useEffect } from 'react'
import { Button } from '../ui/Button'
import { Icon, type IconName } from '../Icon'

type Tone = 'danger' | 'default'

interface ConfirmDialogProps {
  open: boolean
  title: string
  body: string
  confirmLabel: string
  cancelLabel?: string
  tone?: Tone
  icon?: IconName
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * Modal confirmation for destructive/irreversible actions (ported from the design system). Closes
 * on Escape and backdrop click. Used for Delete (and later Accept risk / Reopen) per PLAN §7.9.
 */
export function ConfirmDialog({
  open, title, body, confirmLabel, cancelLabel = 'Cancel', tone = 'danger', icon = 'trash', busy = false, onConfirm, onCancel,
}: ConfirmDialogProps) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onCancel])

  if (!open) return null

  const danger = tone === 'danger'
  const accent = danger ? 'var(--critical-600)' : 'var(--primary)'
  const accentBg = danger ? 'var(--critical-100)' : 'var(--primary-soft)'

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onCancel()
      }}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1000,
        background: 'var(--scrim)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
        backdropFilter: 'blur(1.5px)',
      }}
    >
      <div
        className="cm-root"
        style={{ width: '100%', maxWidth: 440, background: 'var(--surface-card)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-pop)', overflow: 'hidden' }}
      >
        <div style={{ padding: '22px 24px 18px', display: 'flex', gap: 14 }}>
          <span style={{ flex: 'none', width: 40, height: 40, borderRadius: '50%', background: accentBg, color: accent, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon name={icon} size={20} />
          </span>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 7, paddingTop: 2 }}>
            <h2 style={{ margin: 0, fontFamily: 'var(--font-display)', fontSize: 'var(--fs-h3)', fontWeight: 600, color: 'var(--text-strong)', letterSpacing: '-0.005em' }}>{title}</h2>
            <p style={{ margin: 0, fontSize: 'var(--fs-body-sm)', lineHeight: 1.6, color: 'var(--text-muted)' }}>{body}</p>
          </div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, padding: '14px 24px', background: 'var(--surface-inset)', borderTop: '1px solid var(--border-subtle)' }}>
          <Button variant="ghost" onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} iconLeft={icon} onClick={onConfirm} disabled={busy}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
