import { useEffect } from 'react'
import { Icon, type IconName } from '../Icon'

export type ToastTone = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  tone: ToastTone
  title: string
  message?: string
  /** Auto-dismiss after N ms; omitted = sticky (errors persist until dismissed). */
  duration?: number
}

const TONES: Record<ToastTone, { icon: IconName; fg: string; bg: string }> = {
  success: { icon: 'check', fg: 'var(--positive-600)', bg: 'var(--positive-100)' },
  error: { icon: 'alert-triangle', fg: 'var(--negative-600)', bg: 'var(--negative-100)' },
  info: { icon: 'info', fg: 'var(--status-submitted)', bg: 'var(--status-submitted-bg)' },
}

/** Toast — a single transient result notification (ported from the design system). */
export function Toast({ tone, title, message, duration, onDismiss }: ToastItem & { onDismiss: () => void }) {
  const t = TONES[tone] ?? TONES.success
  useEffect(() => {
    if (!duration) return
    const id = setTimeout(onDismiss, duration)
    return () => clearTimeout(id)
  }, [duration, onDismiss])

  return (
    <div
      role="status"
      style={{
        display: 'flex', alignItems: 'flex-start', gap: 11, width: 360, maxWidth: 'calc(100vw - 32px)',
        padding: '12px 12px 12px 14px', background: 'var(--surface-card)',
        border: '1px solid var(--border-default)', borderLeft: `3px solid ${t.fg}`,
        borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-lg)',
      }}
    >
      <span style={{ flex: 'none', width: 22, height: 22, borderRadius: '50%', background: t.bg, color: t.fg, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginTop: 1 }}>
        <Icon name={t.icon} size={14} color={t.fg} />
      </span>
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <span style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600, color: 'var(--text-strong)' }}>{title}</span>
        {message ? <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-muted)', lineHeight: 1.5 }}>{message}</span> : null}
      </div>
      <button
        type="button"
        title="Dismiss"
        onClick={onDismiss}
        style={{ flex: 'none', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 24, height: 24, border: 'none', background: 'transparent', cursor: 'pointer', color: 'var(--text-faint)', borderRadius: 'var(--radius-sm)' }}
      >
        <Icon name="x" size={14} />
      </button>
    </div>
  )
}

/**
 * ToastViewport — fixed container, bottom-right, newest on top, stacks upward (PLAN §7.9).
 */
export function ToastViewport({ toasts, onDismiss }: { toasts: ToastItem[]; onDismiss: (id: number) => void }) {
  return (
    <div style={{ position: 'fixed', bottom: 20, right: 20, zIndex: 1100, display: 'flex', flexDirection: 'column-reverse', gap: 10, alignItems: 'flex-end', pointerEvents: 'none' }}>
      {toasts.map((t) => (
        <div key={t.id} style={{ pointerEvents: 'auto' }}>
          <Toast {...t} onDismiss={() => onDismiss(t.id)} />
        </div>
      ))}
    </div>
  )
}
