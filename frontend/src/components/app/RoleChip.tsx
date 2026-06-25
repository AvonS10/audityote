import { Icon } from '../Icon'

/** Role pill — Reviewer (shield-check) / Analyst (file-text). Ported from the design AppShell. */
export function RoleChip({ role, onDark = false }: { role: string; onDark?: boolean }) {
  const isReviewer = role.toUpperCase() === 'REVIEWER'
  const label = isReviewer ? 'Reviewer' : 'Analyst'
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        height: 17,
        padding: '0 7px',
        borderRadius: 'var(--radius-pill)',
        fontSize: 10,
        fontWeight: 700,
        letterSpacing: '0.04em',
        textTransform: 'uppercase',
        background: onDark ? 'color-mix(in srgb, #fff 15%, transparent)' : 'var(--primary-soft)',
        color: onDark ? 'var(--text-on-dark)' : 'var(--primary)',
        border: onDark ? 'none' : '1px solid var(--primary-soft-border)',
      }}
    >
      <Icon name={isReviewer ? 'shield-check' : 'file-text'} size={11} />
      {label}
    </span>
  )
}
