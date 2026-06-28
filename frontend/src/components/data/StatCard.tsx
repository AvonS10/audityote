import type { CSSProperties } from 'react'
import { Icon, type IconName } from '../Icon'

/**
 * StatCard — a single summary metric: uppercase label, big tabular value, optional delta and a
 * coloured accent rail. Ported from the design system (layout/StatCard) for the Risk Posture band.
 */
export function StatCard({
  label,
  value,
  sub,
  delta,
  deltaDir,
  icon,
  accent = 'var(--primary)',
  style,
}: {
  label: string
  value: string | number
  sub?: string
  delta?: string
  deltaDir?: 'up' | 'down'
  icon?: IconName
  accent?: string
  style?: CSSProperties
}) {
  const up = deltaDir === 'up'
  const deltaColor =
    delta == null ? undefined : up ? 'var(--critical-600)' : deltaDir === 'down' ? 'var(--positive-600)' : 'var(--text-muted)'
  return (
    <div
      className="bg-surface-card border border-subtle rounded-md shadow-xs"
      style={{ position: 'relative', padding: '14px 16px', overflow: 'hidden', display: 'flex', flexDirection: 'column', gap: 8, ...style }}
    >
      <span style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: 'var(--rail-w)', background: accent }} />
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span className="cm-eyebrow text-muted">{label}</span>
        {icon ? <Icon name={icon} size={15} color="var(--text-faint)" /> : null}
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <span
          className="font-display text-strong"
          style={{ fontSize: 'var(--fs-stat)', fontWeight: 600, lineHeight: 1, fontVariantNumeric: 'tabular-nums' }}
        >
          {value}
        </span>
        {delta != null ? (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 2, fontSize: 'var(--fs-caption)', fontWeight: 600, color: deltaColor }}>
            <Icon name={up ? 'arrow-up' : 'arrow-down'} size={13} />
            {delta}
          </span>
        ) : null}
      </div>
      {sub ? <span className="text-muted" style={{ fontSize: 'var(--fs-caption)' }}>{sub}</span> : null}
    </div>
  )
}
