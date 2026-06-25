import type { CSSProperties } from 'react'

const FRAMEWORKS: Record<string, { label: string; full: string; tint: string }> = {
  iso27001: { label: 'ISO 27001', full: 'ISO/IEC 27001:2022', tint: 'var(--blue-600)' },
  owasp: { label: 'OWASP', full: 'OWASP Top 10:2025', tint: 'var(--high-600)' },
  nist: { label: 'NIST CSF', full: 'NIST CSF 2.0', tint: 'var(--status-submitted)' },
}

/** FrameworkTag — compact framework reference: brand-tinted dot + abbreviation + optional control code. */
export function FrameworkTag({ framework, control, style }: { framework: string; control?: string; style?: CSSProperties }) {
  const f = FRAMEWORKS[framework]
  const text = f ? f.label : framework
  const tint = f ? f.tint : 'var(--slate-600)'
  return (
    <span
      title={f ? `${f.full}${control ? ' · ' + control : ''}` : text}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 5,
        height: 20,
        padding: '0 7px',
        background: 'var(--surface-card)',
        color: 'var(--text-body)',
        border: '1px solid var(--border-default)',
        borderRadius: 'var(--radius-xs)',
        fontSize: 'var(--fs-micro)',
        fontWeight: 500,
        whiteSpace: 'nowrap',
        ...style,
      }}
    >
      <span style={{ width: 5, height: 5, borderRadius: '50%', background: tint, flex: 'none' }} />
      <span style={{ fontWeight: 600, color: 'var(--text-strong)' }}>{text}</span>
      {control ? (
        <span style={{ fontFamily: 'var(--font-data)', fontSize: 10, color: 'var(--text-muted)', letterSpacing: '-0.01em' }}>{control}</span>
      ) : null}
    </span>
  )
}
