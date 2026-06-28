import type { CSSProperties } from 'react'

/** Maps a 0–10 risk score to its severity-band color (CVSS 3.x bands — same scale as CvssScore). */
function band(score: number): string {
  if (score >= 9.0) return 'var(--critical-600)'
  if (score >= 7.0) return 'var(--high-600)'
  if (score >= 4.0) return 'var(--medium-700)'
  if (score > 0) return 'var(--low-600)'
  return 'var(--text-faint)'
}

/**
 * RiskScore — the per-finding effective risk score (0–10) from the backend RiskScoringStrategy (#18):
 * the finding's CVSS when present, otherwise a severity-derived value. A derived score is marked with
 * a small dot + "derived" tooltip so it isn't mistaken for a real CVSS base score.
 */
export function RiskScore({ score, source, style }: { score: number; source: string; style?: CSSProperties }) {
  const fg = band(score)
  const pct = Math.max(0, Math.min(100, (score / 10) * 100))
  const derived = source === 'severity'
  return (
    <span
      style={{ display: 'inline-flex', alignItems: 'center', gap: 8, ...style }}
      title={derived ? 'Derived from severity (no CVSS score recorded)' : 'From the CVSS base score'}
    >
      <span style={{ width: 44, height: 4, borderRadius: 'var(--radius-pill)', background: 'var(--slate-200)', overflow: 'hidden', flex: 'none' }}>
        <span style={{ display: 'block', width: `${pct}%`, height: '100%', background: fg }} />
      </span>
      <span style={{ fontFamily: 'var(--font-data)', fontWeight: 600, fontSize: 'var(--fs-body-sm)', color: fg, fontVariantNumeric: 'tabular-nums', minWidth: 26 }}>
        {score.toFixed(1)}
      </span>
      {derived ? (
        <span
          aria-label="derived"
          style={{ fontSize: 'var(--fs-micro)', fontWeight: 600, color: 'var(--text-faint)', letterSpacing: 'var(--ls-label)', textTransform: 'uppercase' }}
        >
          der
        </span>
      ) : null}
    </span>
  )
}
