import type { CSSProperties } from 'react'

/**
 * ControlMap coyote brand motif + desert-habitat backdrop, ported from the design system.
 * The mark is a PNG recolored per theme via CSS (.cm-coy--* classes in tokens/base.css swap the
 * forest/steel variants). Brand/chrome only — never inside data tables, forms, or dashboards.
 */

function CoyoteMark({ size = 30, box, onDark = false, style }: { size?: number; box?: number; onDark?: boolean; style?: CSSProperties }) {
  const variants = onDark ? (['onforest', 'onsteel'] as const) : (['forest', 'steel'] as const)
  const imgStyle: CSSProperties = box
    ? { width: box, height: box, objectFit: 'contain' }
    : { height: size, width: 'auto' }
  return (
    <span style={{ display: 'inline-flex', lineHeight: 0, ...style }} aria-hidden="true">
      <img className={`cm-coy--${variants[0]}`} src={`/brand/coyote-${variants[0]}.png`} alt="" style={imgStyle} />
      <img className={`cm-coy--${variants[1]}`} src={`/brand/coyote-${variants[1]}.png`} alt="" style={imgStyle} />
    </span>
  )
}

/** Brand tile — the light coyote in a primary gradient square. */
export function CoyoteBadge({ size = 32, radius = 8 }: { size?: number; radius?: number }) {
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        borderRadius: radius,
        flex: 'none',
        background: 'linear-gradient(155deg, var(--primary), var(--primary-press))',
        boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.16)',
      }}
    >
      <CoyoteMark box={Math.round(size * 0.82)} onDark />
    </span>
  )
}

/** Full wordmark lockup (badge + "AuditYote"). */
export function CoyoteLockup({ size = 32, fontSize = 16, onDark = false }: { size?: number; fontSize?: number; onDark?: boolean }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
      <CoyoteBadge size={size} />
      <span
        style={{
          fontFamily: 'var(--font-display)',
          fontSize,
          fontWeight: 700,
          letterSpacing: '-0.01em',
          color: onDark ? 'var(--text-on-dark)' : 'var(--text-strong)',
        }}
      >
        Audit<span style={{ opacity: 0.6 }}>Yote</span>
      </span>
    </span>
  )
}

const HAB_BACK = '0,150 90,128 180,142 300,112 430,138 560,118 700,140 840,116 980,138 1120,124 1200,140 1200,220 0,220'
const HAB_MESA = '0,178 140,160 200,138 360,138 412,160 520,170 660,166 760,150 824,130 1000,130 1064,152 1180,166 1200,170 1200,220 0,220'
const HAB_HILL = '0,189 160,177 320,185 470,173 640,185 800,175 960,187 1120,177 1200,185 1200,220 0,220'
const HAB_GROUND = '0,197 1200,193 1200,220 0,220'

function saguaro(x: number, top: number): string {
  const b = 196
  return [
    `M${x - 5},${b} L${x - 5},${top + 6} L${x - 2},${top} L${x + 2},${top} L${x + 5},${top + 6} L${x + 5},${b} Z`,
    `M${x - 5},${b - 22} L${x - 12},${b - 24} L${x - 12},${b - 38} L${x - 9},${b - 39} L${x - 8},${b - 26} L${x - 5},${b - 25} Z`,
    `M${x + 5},${b - 16} L${x + 12},${b - 18} L${x + 12},${b - 30} L${x + 9},${b - 31} L${x + 8},${b - 20} L${x + 5},${b - 19} Z`,
  ].join(' ')
}
function sage(x: number, w: number, h: number): string {
  const b = 197
  return `M${x - w},${b} L${x - w * 0.5},${b - h * 0.7} L${x - w * 0.2},${b - h * 0.4} L${x},${b - h} L${x + w * 0.2},${b - h * 0.45} L${x + w * 0.5},${b - h * 0.7} L${x + w},${b} Z`
}
function grass(x: number): string {
  const b = 197
  return `M${x},${b} L${x - 3},${b - 11} L${x - 1},${b} Z M${x + 2},${b} L${x + 3},${b - 14} L${x + 5},${b} Z M${x + 5},${b} L${x + 8},${b - 9} L${x + 7},${b} Z`
}
const HAB_PLANTS = [
  saguaro(206, 150), saguaro(545, 138), saguaro(958, 156),
  sage(110, 16, 16), sage(360, 20, 19), sage(690, 15, 14), sage(820, 22, 20), sage(1090, 18, 17),
  grass(70), grass(285), grass(470), grass(640), grass(760), grass(900), grass(1040), grass(1160),
].join(' ')

/** Flat, layered, angular southwestern-desert silhouettes — fills open background space. */
export function Habitat({ height = 180, onDark = false, opacity = 1, style }: { height?: number; onDark?: boolean; opacity?: number; style?: CSSProperties }) {
  const tone = (pct: number) =>
    onDark ? `color-mix(in srgb, #ffffff ${pct}%, transparent)` : `color-mix(in srgb, var(--primary) ${pct}%, transparent)`
  return (
    <svg
      viewBox="0 0 1200 220"
      width="100%"
      height={height}
      preserveAspectRatio="xMidYMax slice"
      style={{ display: 'block', opacity, ...style }}
      aria-hidden="true"
    >
      <polygon points={HAB_BACK} fill={tone(onDark ? 5 : 7)} />
      <polygon points={HAB_MESA} fill={tone(onDark ? 8 : 11)} />
      <polygon points={HAB_HILL} fill={tone(onDark ? 12 : 15)} />
      <polygon points={HAB_GROUND} fill={tone(onDark ? 16 : 20)} />
      <path d={HAB_PLANTS} fill={tone(onDark ? 20 : 26)} />
    </svg>
  )
}
