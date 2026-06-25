import type { CSSProperties } from 'react'

/** SeverityBadge — risk severity pill with a leading filled dot. Colors are semantic and fixed. */
const MAP: Record<string, { fg: string; bg: string; dot: string; label: string }> = {
  critical: { fg: 'var(--critical-600)', bg: 'var(--critical-100)', dot: 'var(--critical-500)', label: 'Critical' },
  high: { fg: 'var(--high-600)', bg: 'var(--high-100)', dot: 'var(--high-500)', label: 'High' },
  medium: { fg: 'var(--medium-700)', bg: 'var(--medium-100)', dot: 'var(--medium-500)', label: 'Medium' },
  low: { fg: 'var(--low-600)', bg: 'var(--low-100)', dot: 'var(--low-500)', label: 'Low' },
}

export function SeverityBadge({ level = 'medium', size = 'md', style }: { level?: string; size?: 'sm' | 'md'; style?: CSSProperties }) {
  const s = MAP[level] ?? MAP.medium
  const small = size === 'sm'
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: small ? 5 : 6,
        height: small ? 19 : 22,
        padding: small ? '0 7px 0 6px' : '0 9px 0 7px',
        background: s.bg,
        color: s.fg,
        borderRadius: 'var(--radius-xs)',
        fontSize: small ? 'var(--fs-micro)' : 'var(--fs-caption)',
        fontWeight: 600,
        letterSpacing: '0.01em',
        whiteSpace: 'nowrap',
        ...style,
      }}
    >
      <span style={{ width: small ? 6 : 7, height: small ? 6 : 7, borderRadius: '50%', background: s.dot, flex: 'none' }} />
      {s.label}
    </span>
  )
}
