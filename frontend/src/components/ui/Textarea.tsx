import { useState, type TextareaHTMLAttributes } from 'react'

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean
}

/** Themed multi-line input, ported from the design system. */
export function Textarea({ invalid = false, disabled = false, rows = 4, style, ...rest }: TextareaProps) {
  const [focus, setFocus] = useState(false)
  return (
    <textarea
      rows={rows}
      disabled={disabled}
      onFocus={() => setFocus(true)}
      onBlur={() => setFocus(false)}
      style={{
        width: '100%',
        padding: '9px 12px',
        resize: 'vertical',
        minHeight: 80,
        fontFamily: 'var(--font-body)',
        fontSize: 'var(--fs-body)',
        lineHeight: 1.55,
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
