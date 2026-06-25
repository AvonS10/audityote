import { useState, type InputHTMLAttributes } from 'react'

type Size = 'sm' | 'md'

const SIZES: Record<Size, { height: string; font: string; pad: string }> = {
  sm: { height: 'var(--control-h-sm)', font: 'var(--fs-body-sm)', pad: '0 10px' },
  md: { height: 'var(--control-h-md)', font: 'var(--fs-body)', pad: '0 12px' },
}

interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  size?: Size
  invalid?: boolean
}

/** Themed text input, ported from the design system (1px hairline, soft focus ring, invalid state). */
export function Input({ size = 'md', invalid = false, disabled = false, style, ...rest }: InputProps) {
  const sz = SIZES[size]
  const [focus, setFocus] = useState(false)
  return (
    <input
      disabled={disabled}
      onFocus={() => setFocus(true)}
      onBlur={() => setFocus(false)}
      style={{
        width: '100%',
        height: sz.height,
        padding: sz.pad,
        fontFamily: 'var(--font-body)',
        fontSize: sz.font,
        color: 'var(--text-strong)',
        background: disabled ? 'var(--surface-inset)' : 'var(--surface-card)',
        border: `1px solid ${invalid ? 'var(--critical-500)' : focus ? 'var(--border-focus)' : 'var(--border-default)'}`,
        borderRadius: 'var(--radius-sm)',
        outline: 'none',
        boxShadow: focus ? 'var(--focus-ring)' : 'none',
        transition: 'border-color 120ms ease, box-shadow 120ms ease',
        opacity: disabled ? 0.6 : 1,
        ...style,
      }}
      {...rest}
    />
  )
}
