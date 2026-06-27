import { useState } from 'react'
import { Button } from './ui/Button'
import { downloadReport } from '../lib/reports'

/**
 * Export-to-file button. Downloads the given report path via the blob helper, showing a busy state
 * while in flight and a transient inline note if it fails (downloads have no Toast surface yet).
 */
export function ExportButton({ path, label = 'Export CSV', disabled = false }: { path: string; label?: string; disabled?: boolean }) {
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)

  const run = async () => {
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

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      {failed ? (
        <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--critical-600)' }}>Export failed</span>
      ) : null}
      <Button variant="secondary" iconLeft="download" disabled={disabled || busy} onClick={run}>
        {busy ? 'Exporting…' : label}
      </Button>
    </span>
  )
}
