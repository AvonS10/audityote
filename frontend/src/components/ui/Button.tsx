import { useState, type ButtonHTMLAttributes, type CSSProperties } from 'react'
import { Icon, type IconName } from '../Icon'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

const SIZES: Record<Size, { height: string; padding: string; font: string; gap: string; icon: number }> = {
  sm: { height: 'var(--control-h-sm)', padding: '0 10px', font: 'var(--fs-body-sm)', gap: '5px', icon: 14 },
  md: { height: 'var(--control-h-md)', padding: '0 14px', font: 'var(--fs-body)', gap: '6px', icon: 16 },
  lg: { height: 'var(--control-h-lg)', padding: '0 18px', font: 'var(--fs-body)', gap: '8px', icon: 16 },
}

const VARIANTS: Record<Variant, { base: CSSProperties; hover: string; press: string }> = {
  primary: {
    base: { backgroundColor: 'var(--primary)', color: 'var(--text-on-primary)', border: '1px solid var(--primary)' },
    hover: 'var(--primary-hover)',
    press: 'var(--primary-press)',
  },
  secondary: {
    base: { backgroundColor: 'var(--surface-card)', color: 'var(--text-strong)', border: '1px solid var(--border-default)' },
    hover: 'var(--surface-inset)',
    press: 'var(--surface-sunken)',
  },
  ghost: {
    base: { backgroundColor: 'transparent', color: 'var(--text-body)', border: '1px solid transparent' },
    hover: 'color-mix(in srgb, var(--text-strong) 7%, transparent)',
    press: 'color-mix(in srgb, var(--text-strong) 12%, transparent)',
  },
  danger: {
    base: { backgroundColor: 'var(--critical-600)', color: '#fff', border: '1px solid var(--critical-600)' },
    hover: 'var(--critical-500)',
    press: 'var(--critical-500)',
  },
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  iconLeft?: IconName
  iconRight?: IconName
  fullWidth?: boolean
}

/** Themed button, ported from the design system (variants, sizes, hover/press tints). */
export function Button({
  children, variant = 'primary', size = 'md', iconLeft, iconRight,
  fullWidth = false, disabled = false, style, type = 'button', ...rest
}: ButtonProps) {
  const sz = SIZES[size]
  const v = VARIANTS[variant]
  const [state, setState] = useState<'idle' | 'hover' | 'press'>('idle')
  const bg = disabled ? undefined : state === 'press' ? v.press : state === 'hover' ? v.hover : undefined

  return (
    <button
      type={type}
      disabled={disabled}
      onMouseEnter={() => setState('hover')}
      onMouseLeave={() => setState('idle')}
      onMouseDown={() => setState('press')}
      onMouseUp={() => setState('hover')}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: sz.gap,
        height: sz.height,
        padding: sz.padding,
        width: fullWidth ? '100%' : undefined,
        fontFamily: 'var(--font-body)',
        fontSize: sz.font,
        fontWeight: 600,
        lineHeight: 1,
        borderRadius: 'var(--radius-sm)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        whiteSpace: 'nowrap',
        userSelect: 'none',
        transition: 'box-shadow 120ms ease, background-color 120ms ease',
        opacity: disabled ? 0.5 : 1,
        boxShadow: state === 'hover' && variant !== 'ghost' ? 'var(--shadow-xs)' : 'none',
        ...v.base,
        backgroundColor: bg ?? v.base.backgroundColor,
        ...style,
      }}
      {...rest}
    >
      {iconLeft ? <Icon name={iconLeft} size={sz.icon} /> : null}
      {children}
      {iconRight ? <Icon name={iconRight} size={sz.icon} /> : null}
    </button>
  )
}
