import type { CSSProperties } from 'react'
import { bandFor } from '../../lib/posture'

/** Polar→cartesian for the gauge arc (180° sweep, left→right). */
function pt(cx: number, cy: number, r: number, deg: number): [number, number] {
  const a = (deg - 180) * (Math.PI / 180)
  return [cx + r * Math.cos(a), cy + r * Math.sin(a)]
}
function arcPath(cx: number, cy: number, r: number, startDeg: number, endDeg: number): string {
  const [x1, y1] = pt(cx, cy, r, startDeg)
  const [x2, y2] = pt(cx, cy, r, endDeg)
  const large = endDeg - startDeg > 180 ? 1 : 0
  return `M ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2}`
}

/**
 * RiskMeter — the overall risk-posture gauge: a 180° arc whose filled sweep + label reflect the
 * 0–100 posture score (higher = worse). Ported near-verbatim from the design system (layout/RiskMeter).
 */
export function RiskMeter({ score = 0, size = 168, label = 'Risk posture', style }: { score?: number; size?: number; label?: string; style?: CSSProperties }) {
  const s = Math.max(0, Math.min(100, score))
  const band = bandFor(s)
  const w = size
  const h = size * 0.62
  const cx = w / 2
  const cy = h - 6
  const r = w / 2 - 12
  const stroke = 11
  const endDeg = (s / 100) * 180
  return (
    <div style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center', ...style }}>
      <svg width={w} height={h + 2} viewBox={`0 0 ${w} ${h + 2}`}>
        <path d={arcPath(cx, cy, r, 0, 180)} fill="none" stroke="var(--slate-200)" strokeWidth={stroke} strokeLinecap="round" />
        {s > 0 ? (
          <path d={arcPath(cx, cy, r, 0, Math.max(2, endDeg))} fill="none" stroke={band.color} strokeWidth={stroke} strokeLinecap="round" />
        ) : null}
        <text x={cx} y={cy - 14} textAnchor="middle" style={{ fontFamily: 'var(--font-display)', fontSize: 30, fontWeight: 600, fill: 'var(--text-strong)' }}>
          {Math.round(s)}
        </text>
        <text x={cx} y={cy + 2} textAnchor="middle" style={{ fontFamily: 'var(--font-body)', fontSize: 10.5, fontWeight: 600, letterSpacing: '0.04em', fill: band.color, textTransform: 'uppercase' }}>
          {band.label}
        </text>
      </svg>
      <span className="cm-eyebrow text-muted" style={{ marginTop: 2 }}>{label}</span>
    </div>
  )
}
