import { useEffect, useRef, useState } from 'react'
import { Button } from './ui/Button'
import { Icon, type IconName } from './Icon'
import { downloadReport } from '../lib/reports'

/**
 * Export dropdown: one "Export" button revealing CSV / PDF download choices (PLAN §7 "Export
 * PDF/CSV"). Mirrors the AppShell account-menu pattern (click-outside to close). Each item downloads
 * via the blob helper, showing a busy state on the trigger and a transient note on failure.
 */
export function ExportMenu({ csvPath, pdfPath, disabled = false }: { csvPath: string; pdfPath: string; disabled?: boolean }) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState<'csv' | 'pdf' | null>(null)
  const [failed, setFailed] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  const run = async (kind: 'csv' | 'pdf', path: string) => {
    setOpen(false)
    setBusy(kind)
    setFailed(false)
    try {
      await downloadReport(path)
    } catch {
      setFailed(true)
    } finally {
      setBusy(null)
    }
  }

  return (
    <div ref={ref} style={{ position: 'relative', display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      {failed ? <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--critical-600)' }}>Export failed</span> : null}
      <Button
        variant="secondary"
        iconLeft="download"
        iconRight="chevron-down"
        disabled={disabled || busy !== null}
        onClick={() => setOpen((o) => !o)}
      >
        {busy ? 'Exporting…' : 'Export'}
      </Button>
      {open ? (
        <div
          role="menu"
          style={{
            position: 'absolute',
            top: 'calc(100% + 4px)',
            right: 0,
            zIndex: 30,
            minWidth: 168,
            background: 'var(--surface-card)',
            border: '1px solid var(--border-default)',
            borderRadius: 'var(--radius-md)',
            boxShadow: 'var(--shadow-pop)',
            padding: 4,
          }}
        >
          <MenuItem icon="file-text" label="Download CSV" onClick={() => run('csv', csvPath)} />
          <MenuItem icon="file-text" label="Download PDF" onClick={() => run('pdf', pdfPath)} />
        </div>
      ) : null}
    </div>
  )
}

function MenuItem({ icon, label, onClick }: { icon: IconName; label: string; onClick: () => void }) {
  const [hover, setHover] = useState(false)
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 9,
        width: '100%',
        height: 34,
        padding: '0 9px',
        border: 'none',
        borderRadius: 'var(--radius-sm)',
        background: hover ? 'var(--surface-inset)' : 'transparent',
        cursor: 'pointer',
        textAlign: 'left',
        fontFamily: 'var(--font-body)',
        fontSize: 'var(--fs-body-sm)',
        fontWeight: 500,
        color: 'var(--text-body)',
      }}
    >
      <Icon name={icon} size={16} color="var(--text-muted)" />
      {label}
    </button>
  )
}
