import type { SelectHTMLAttributes } from 'react'
import { Icon } from '../Icon'

interface Option {
  value: string
  label: string
}

interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
  options: Option[]
}

/** Themed native select (accessible) with a custom chevron, styled from the design tokens. */
export function Select({ options, style, ...rest }: SelectProps) {
  return (
    <span style={{ position: 'relative', display: 'inline-flex', alignItems: 'center' }}>
      <select
        style={{
          appearance: 'none',
          height: 'var(--control-h-md)',
          padding: '0 32px 0 12px',
          fontFamily: 'var(--font-body)',
          fontSize: 'var(--fs-body-sm)',
          color: 'var(--text-strong)',
          background: 'var(--surface-card)',
          border: '1px solid var(--border-default)',
          borderRadius: 'var(--radius-sm)',
          cursor: 'pointer',
          outline: 'none',
          ...style,
        }}
        {...rest}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <span style={{ position: 'absolute', right: 10, pointerEvents: 'none', display: 'flex' }}>
        <Icon name="chevron-down" size={15} color="var(--text-muted)" />
      </span>
    </span>
  )
}
