import type { CSSProperties } from 'react'

/** Maps a CVSS base score (0–10) to its CVSS 3.x severity-band color. */
function band(score: number): string {
  if (score >= 9.0) return 'var(--critical-600)'
  if (score >= 7.0) return 'var(--high-600)'
  if (score >= 4.0) return 'var(--medium-700)'
  if (score > 0) return 'var(--low-600)'
  return 'var(--text-faint)'
}

/** CvssScore — monospace base score with a severity-tinted track; `variant="plain"` drops the track. */
export function CvssScore({ score, variant = 'track', style }: { score: number | null; variant?: 'track' | 'plain'; style?: CSSProperties }) {
  const has = typeof score === 'number'
  const fg = has ? band(score as number) : 'var(--text-faint)'
  const val = has ? (score as number).toFixed(1) : '—'
  const pct = has ? Math.max(0, Math.min(100, ((score as number) / 10) * 100)) : 0

  if (variant === 'plain') {
    return (
      <span style={{ fontFamily: 'var(--font-data)', fontWeight: 500, fontSize: 'var(--fs-body-sm)', color: fg, fontVariantNumeric: 'tabular-nums', ...style }}>
        {val}
      </span>
    )
  }
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, ...style }}>
      <span style={{ width: 44, height: 4, borderRadius: 'var(--radius-pill)', background: 'var(--slate-200)', overflow: 'hidden', flex: 'none' }}>
        <span style={{ display: 'block', width: `${pct}%`, height: '100%', background: fg }} />
      </span>
      <span style={{ fontFamily: 'var(--font-data)', fontWeight: 600, fontSize: 'var(--fs-body-sm)', color: fg, fontVariantNumeric: 'tabular-nums', minWidth: 26 }}>
        {val}
      </span>
    </span>
  )
}
