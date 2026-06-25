import { useState, type InputHTMLAttributes } from 'react'
import { Icon } from '../Icon'

interface SearchInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  width?: number | string
}

/** Text input with a leading search icon, styled from the design tokens. */
export function SearchInput({ width = 320, style, ...rest }: SearchInputProps) {
  const [focus, setFocus] = useState(false)
  return (
    <span style={{ position: 'relative', display: 'inline-flex', alignItems: 'center', width }}>
      <span style={{ position: 'absolute', left: 11, pointerEvents: 'none', display: 'flex' }}>
        <Icon name="search" size={15} color="var(--text-muted)" />
      </span>
      <input
        type="search"
        onFocus={() => setFocus(true)}
        onBlur={() => setFocus(false)}
        style={{
          width: '100%',
          height: 'var(--control-h-md)',
          padding: '0 12px 0 34px',
          fontFamily: 'var(--font-body)',
          fontSize: 'var(--fs-body-sm)',
          color: 'var(--text-strong)',
          background: 'var(--surface-card)',
          border: `1px solid ${focus ? 'var(--border-focus)' : 'var(--border-default)'}`,
          borderRadius: 'var(--radius-sm)',
          outline: 'none',
          boxShadow: focus ? 'var(--focus-ring)' : 'none',
          transition: 'border-color 120ms ease, box-shadow 120ms ease',
          ...style,
        }}
        {...rest}
      />
    </span>
  )
}
