import { useEffect, useState } from 'react'
import { Button } from '../ui/Button'
import { Icon } from '../Icon'
import { Textarea } from '../ui/Textarea'

/**
 * Return-for-changes dialog: a reviewer sends a submitted finding back, with a **required** comment
 * (mirrors the backend's comment-required rule, PLAN §8). Modal shell matches ConfirmDialog.
 */
export function ReturnDialog({ open, busy = false, onConfirm, onCancel }: {
  open: boolean
  busy?: boolean
  onConfirm: (comment: string) => void
  onCancel: () => void
}) {
  const [comment, setComment] = useState('')
  const [touched, setTouched] = useState(false)

  useEffect(() => {
    if (!open) {
      setComment('')
      setTouched(false)
      return
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onCancel])

  if (!open) return null
  const invalid = comment.trim() === ''

  const submit = () => {
    setTouched(true)
    if (!invalid) onConfirm(comment.trim())
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Return for changes"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onCancel()
      }}
      style={{ position: 'fixed', inset: 0, zIndex: 1000, background: 'var(--scrim)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24, backdropFilter: 'blur(1.5px)' }}
    >
      <div className="cm-root" style={{ width: '100%', maxWidth: 460, background: 'var(--surface-card)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-pop)', overflow: 'hidden' }}>
        <div style={{ padding: '22px 24px 16px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', gap: 14 }}>
            <span style={{ flex: 'none', width: 40, height: 40, borderRadius: '50%', background: 'var(--status-returned-bg)', color: 'var(--status-returned)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="arrow-up-right" size={20} style={{ transform: 'rotate(135deg)' }} />
            </span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 7, paddingTop: 2 }}>
              <h2 style={{ margin: 0, fontFamily: 'var(--font-display)', fontSize: 'var(--fs-h3)', fontWeight: 600, color: 'var(--text-strong)', letterSpacing: '-0.005em' }}>Return for changes</h2>
              <p style={{ margin: 0, fontSize: 'var(--fs-body-sm)', lineHeight: 1.6, color: 'var(--text-muted)' }}>Send this finding back to the analyst. A comment explaining what's needed is required.</p>
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
            <Textarea
              autoFocus
              placeholder="What needs to change before this can be approved?"
              value={comment}
              invalid={touched && invalid}
              onChange={(e) => setComment(e.target.value)}
            />
            {touched && invalid ? <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--critical-600)' }}>A comment is required to return a finding.</span> : null}
          </div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, padding: '14px 24px', background: 'var(--surface-inset)', borderTop: '1px solid var(--border-subtle)' }}>
          <Button variant="ghost" onClick={onCancel} disabled={busy}>Cancel</Button>
          <Button variant="primary" iconLeft="arrow-up-right" disabled={busy} onClick={submit}>Return for changes</Button>
        </div>
      </div>
    </div>
  )
}
