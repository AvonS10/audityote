import type { CSSProperties } from 'react'

/** Initials avatar with a deterministic color, ported from the design system. No photos by design. */
const PALETTE: [string, string][] = [
  ['#103A5E', '#1C5D99'],
  ['#0E7490', '#0F766E'],
  ['#87600B', '#A87908'],
  ['#7A1410', '#9B1C1C'],
  ['#15532E', '#15803D'],
  ['#3A2E5B', '#5A4B86'],
]

function hash(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return Math.abs(h)
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return '?'
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase()
}

export function Avatar({ name = '', size = 26, style }: { name?: string; size?: number; style?: CSSProperties }) {
  const [c1, c2] = PALETTE[hash(name) % PALETTE.length]
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        borderRadius: '50%',
        flex: 'none',
        background: `linear-gradient(160deg, ${c2}, ${c1})`,
        color: '#fff',
        fontFamily: 'var(--font-body)',
        fontWeight: 600,
        fontSize: Math.round(size * 0.4),
        letterSpacing: '0.01em',
        boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.12)',
        ...style,
      }}
    >
      {initials(name)}
    </span>
  )
}
