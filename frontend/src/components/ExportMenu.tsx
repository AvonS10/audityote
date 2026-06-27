import { Fragment, useEffect, useRef, useState } from 'react'
import { Button } from './ui/Button'
import { Icon, type IconName } from './Icon'
import { downloadReport } from '../lib/reports'

export interface ReportGroup {
  label: string
  csvPath: string
  pdfPath: string
}

/**
 * Export dropdown: an "Export" button revealing CSV / PDF download choices (PLAN §7 "Export
 * PDF/CSV"). Accepts one or more report groups — with a single group it lists CSV/PDF directly; with
 * several (e.g. Findings register + Audit log) each gets a labelled section. Mirrors the AppShell
 * account-menu pattern (click-outside to close); downloads via the blob helper.
 */
export function ExportMenu({ reports, disabled = false }: { reports: ReportGroup[]; disabled?: boolean }) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
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

  const run = async (path: string) => {
    setOpen(false)
    setBusy(true)
    setFailed(false)
    try {
      await downloadReport(path)
    } catch {
      setFailed(true)
    } finally {
      setBusy(false)
    }
  }

  const multi = reports.length > 1

  return (
    <div ref={ref} style={{ position: 'relative', display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      {failed ? <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--critical-600)' }}>Export failed</span> : null}
      <Button variant="secondary" iconLeft="download" iconRight="chevron-down" disabled={disabled || busy} onClick={() => setOpen((o) => !o)}>
        {busy ? 'Exporting…' : 'Export'}
      </Button>
      {open ? (
        <div
          role="menu"
          style={{
            position: 'absolute', top: 'calc(100% + 4px)', right: 0, zIndex: 30, minWidth: 184,
            background: 'var(--surface-card)', border: '1px solid var(--border-default)',
            borderRadius: 'var(--radius-md)', boxShadow: 'var(--shadow-pop)', padding: 4,
          }}
        >
          {reports.map((r, i) => (
            <Fragment key={r.label}>
              {multi ? (
                <div className="cm-eyebrow" style={{ color: 'var(--text-faint)', padding: '8px 9px 4px' }}>{r.label}</div>
              ) : null}
              <MenuItem icon="file-text" label={multi ? 'CSV' : 'Download CSV'} onClick={() => run(r.csvPath)} />
              <MenuItem icon="file-text" label={multi ? 'PDF' : 'Download PDF'} onClick={() => run(r.pdfPath)} />
              {multi && i < reports.length - 1 ? <div style={{ height: 1, background: 'var(--border-subtle)', margin: '4px 0' }} /> : null}
            </Fragment>
          ))}
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
        display: 'flex', alignItems: 'center', gap: 9, width: '100%', height: 32, padding: '0 9px',
        border: 'none', borderRadius: 'var(--radius-sm)', background: hover ? 'var(--surface-inset)' : 'transparent',
        cursor: 'pointer', textAlign: 'left', fontFamily: 'var(--font-body)', fontSize: 'var(--fs-body-sm)',
        fontWeight: 500, color: 'var(--text-body)',
      }}
    >
      <Icon name={icon} size={16} color="var(--text-muted)" />
      {label}
    </button>
  )
}
