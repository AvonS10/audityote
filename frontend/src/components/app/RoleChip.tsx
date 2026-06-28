import { Icon, type IconName } from '../Icon'

const ROLES: Record<string, { label: string; icon: IconName }> = {
  ADMIN: { label: 'Admin', icon: 'settings' },
  REVIEWER: { label: 'Reviewer', icon: 'shield-check' },
  ANALYST: { label: 'Analyst', icon: 'file-text' },
}

/** Role pill — Admin / Reviewer / Analyst. Ported from the design AppShell. */
export function RoleChip({ role, onDark = false }: { role: string; onDark?: boolean }) {
  const r = ROLES[role.toUpperCase()] ?? ROLES.ANALYST
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
      <Icon name={r.icon} size={11} />
      {r.label}
    </span>
  )
}
