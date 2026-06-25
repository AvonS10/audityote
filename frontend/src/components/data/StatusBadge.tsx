import type { CSSProperties } from 'react'

/** StatusBadge — workflow state chip (outlined ring + soft fill). Colors are semantic and fixed. */
const MAP: Record<string, { fg: string; bg: string; label: string }> = {
  open: { fg: 'var(--status-open)', bg: 'var(--status-open-bg)', label: 'Open' },
  'in-progress': { fg: 'var(--status-progress)', bg: 'var(--status-progress-bg)', label: 'In Progress' },
  submitted: { fg: 'var(--status-submitted)', bg: 'var(--status-submitted-bg)', label: 'Submitted' },
  approved: { fg: 'var(--status-approved)', bg: 'var(--status-approved-bg)', label: 'Approved' },
  returned: { fg: 'var(--status-returned)', bg: 'var(--status-returned-bg)', label: 'Returned' },
  remediated: { fg: 'var(--status-remediated)', bg: 'var(--status-remediated-bg)', label: 'Remediated' },
  accepted: { fg: 'var(--status-accepted)', bg: 'var(--status-accepted-bg)', label: 'Accepted' },
}

export function StatusBadge({ status = 'open', size = 'md', style }: { status?: string; size?: 'sm' | 'md'; style?: CSSProperties }) {
  const s = MAP[status] ?? MAP.open
  const small = size === 'sm'
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: small ? 5 : 6,
        height: small ? 19 : 22,
        padding: small ? '0 8px' : '0 10px',
        background: s.bg,
        color: s.fg,
        border: `1px solid color-mix(in srgb, ${s.fg} 22%, transparent)`,
        borderRadius: 'var(--radius-pill)',
        fontSize: small ? 'var(--fs-micro)' : 'var(--fs-caption)',
        fontWeight: 600,
        whiteSpace: 'nowrap',
        ...style,
      }}
    >
      <span style={{ width: small ? 5 : 6, height: small ? 5 : 6, borderRadius: '50%', background: s.fg, flex: 'none' }} />
      {s.label}
    </span>
  )
}
