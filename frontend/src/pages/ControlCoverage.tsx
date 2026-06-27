import { useCallback, useEffect, useMemo, useState } from 'react'
import { getFrameworks, type Framework } from '../lib/catalog'
import { getCoverage, type CoverageRow } from '../lib/coverage'
import { coverageReportPath } from '../lib/reports'
import { ExportMenu } from '../components/ExportMenu'
import { Icon } from '../components/Icon'
import { Select } from '../components/ui/Select'
import { SearchInput } from '../components/ui/SearchInput'
import { SeverityBadge } from '../components/data/SeverityBadge'

type Status = 'loading' | 'ready' | 'error'

/**
 * Control Coverage (PLAN §13): for a chosen framework, which controls have findings mapped against
 * them — coverage %, at-risk and gap counts, and a per-control grid. Wired to GET /api/coverage.
 * Export (PDF/CSV) lands with the ReportFactory in build increment #14.
 */
export function ControlCoverage() {
  const [frameworks, setFrameworks] = useState<Framework[]>([])
  const [fw, setFw] = useState('')
  const [rows, setRows] = useState<CoverageRow[]>([])
  const [q, setQ] = useState('')
  const [status, setStatus] = useState<Status>('loading')

  const loadFrameworks = useCallback(async () => {
    setStatus('loading')
    try {
      const fws = await getFrameworks()
      setFrameworks(fws)
      setFw((cur) => cur || fws[0]?.slug || '')
    } catch {
      setStatus('error')
    }
  }, [])

  const loadCoverage = useCallback(async (slug: string) => {
    setStatus('loading')
    try {
      setRows(await getCoverage(slug))
      setStatus('ready')
    } catch {
      setStatus('error')
    }
  }, [])

  useEffect(() => {
    loadFrameworks()
  }, [loadFrameworks])

  useEffect(() => {
    if (fw) loadCoverage(fw)
  }, [fw, loadCoverage])

  const meta = frameworks.find((f) => f.slug === fw)

  const summary = useMemo(() => {
    const total = rows.length
    const covered = rows.filter((r) => r.findingCount > 0).length
    const atRisk = rows.filter((r) => r.atRisk).length
    const gaps = total - covered
    const pct = total === 0 ? 0 : Math.round((covered / total) * 100)
    return { total, covered, atRisk, gaps, pct }
  }, [rows])

  const visible = useMemo(() => {
    const query = q.trim().toLowerCase()
    if (!query) return rows
    return rows.filter((r) => `${r.control.code} ${r.control.title}`.toLowerCase().includes(query))
  }, [rows, q])

  return (
    <div style={{ maxWidth: 1080, display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', fontWeight: 600, margin: 0, letterSpacing: '-0.01em' }}>
            Control coverage
          </h1>
          <p className="text-muted" style={{ fontSize: 'var(--fs-body-sm)', margin: '4px 0 0' }}>
            Which controls have findings mapped against them — gaps and at-risk controls.
          </p>
        </div>
        {frameworks.length > 0 ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Select
              value={fw}
              onChange={(e) => {
                setFw(e.target.value)
                setQ('')
              }}
              options={frameworks.map((f) => ({ value: f.slug, label: `${f.name} ${f.version}` }))}
            />
            <ExportMenu
              reports={[{ label: 'Coverage', csvPath: coverageReportPath(fw, 'csv'), pdfPath: coverageReportPath(fw, 'pdf') }]}
              disabled={!fw || status !== 'ready'}
            />
          </div>
        ) : null}
      </div>

      {status === 'error' ? (
        <ErrorBanner onRetry={() => (frameworks.length ? loadCoverage(fw) : loadFrameworks())} />
      ) : (
        <>
          <div className="cm-coverage-stats" style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
            <StatTile label="Coverage" value={`${summary.pct}%`} hint={`${summary.covered} of ${summary.total} controls`} tone="primary" loading={status === 'loading'} />
            <StatTile label="Covered" value={summary.covered} hint="have ≥ 1 finding" tone="muted" loading={status === 'loading'} />
            <StatTile label="At risk" value={summary.atRisk} hint="active high/critical" tone="critical" loading={status === 'loading'} />
            <StatTile label="Gaps" value={summary.gaps} hint="no findings mapped" tone="muted" loading={status === 'loading'} />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <SearchInput placeholder="Search by code or title…" value={q} onChange={(e) => setQ(e.target.value)} />
            {meta ? (
              <span className="text-muted" style={{ fontSize: 'var(--fs-caption)' }}>
                <b className="text-strong" style={{ fontWeight: 600 }}>{visible.length}</b> controls
                <span className="text-faint"> · {meta.name}</span>
              </span>
            ) : null}
          </div>

          <div className="bg-surface-card border border-subtle rounded-md shadow-xs" style={{ overflow: 'hidden' }}>
            {status === 'loading' ? (
              <CoverageSkeleton />
            ) : (
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    {[
                      { label: 'Code', w: 110, align: 'left' as const },
                      { label: 'Control', w: undefined, align: 'left' as const },
                      { label: 'Findings', w: 96, align: 'right' as const },
                      { label: 'Highest severity', w: 150, align: 'left' as const },
                      { label: 'Coverage', w: 130, align: 'left' as const },
                    ].map((h) => (
                      <th
                        key={h.label}
                        className="cm-eyebrow"
                        style={{
                          textAlign: h.align,
                          padding: '10px 18px',
                          width: h.w,
                          background: 'var(--surface-inset)',
                          borderBottom: '1px solid var(--border-default)',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {h.label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {visible.map((r) => (
                    <tr key={r.control.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                      <td style={{ padding: '13px 18px', verticalAlign: 'middle' }}>
                        <span className="font-mono text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>{r.control.code}</span>
                      </td>
                      <td style={{ padding: '13px 18px', verticalAlign: 'middle' }}>
                        <span className="text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>{r.control.title}</span>
                      </td>
                      <td style={{ padding: '13px 18px', textAlign: 'right', verticalAlign: 'middle' }}>
                        <span className="font-mono" style={{ fontSize: 'var(--fs-body-sm)', color: r.findingCount > 0 ? 'var(--text-strong)' : 'var(--text-faint)', fontVariantNumeric: 'tabular-nums' }}>
                          {r.findingCount}
                        </span>
                      </td>
                      <td style={{ padding: '13px 18px', verticalAlign: 'middle' }}>
                        {r.highestSeverity ? <SeverityBadge level={r.highestSeverity} size="sm" /> : <span className="text-faint" style={{ fontSize: 'var(--fs-body-sm)' }}>—</span>}
                      </td>
                      <td style={{ padding: '13px 18px', verticalAlign: 'middle' }}>
                        <CoverageStatus row={r} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {status === 'ready' && visible.length === 0 ? (
              <div className="text-muted" style={{ padding: 40, textAlign: 'center', fontSize: 'var(--fs-body-sm)' }}>
                {rows.length === 0 ? 'No controls in this framework.' : `No controls match “${q}”.`}
              </div>
            ) : null}
          </div>
        </>
      )}
    </div>
  )
}

/** One coverage outcome per control: at-risk (active high/critical), covered, or an uncovered gap. */
function CoverageStatus({ row }: { row: CoverageRow }) {
  if (row.atRisk) {
    return (
      <span style={pill('var(--critical-100)', 'var(--critical-600)')}>
        <Icon name="alert-triangle" size={13} color="var(--critical-600)" />
        At risk
      </span>
    )
  }
  if (row.findingCount > 0) {
    return (
      <span style={pill('var(--primary-soft)', 'var(--primary)')}>
        <Icon name="check" size={13} color="var(--primary)" />
        Covered
      </span>
    )
  }
  return (
    <span style={pill('var(--surface-inset)', 'var(--text-muted)')}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', border: '1.5px solid var(--border-strong)' }} />
      Gap
    </span>
  )
}

function pill(bg: string, fg: string) {
  return {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 5,
    height: 22,
    padding: '0 9px 0 7px',
    background: bg,
    color: fg,
    borderRadius: 'var(--radius-xs)',
    fontSize: 'var(--fs-caption)',
    fontWeight: 600,
    whiteSpace: 'nowrap' as const,
  }
}

const TONE: Record<string, string> = {
  primary: 'var(--primary)',
  critical: 'var(--critical-600)',
  muted: 'var(--text-strong)',
}

function StatTile({ label, value, hint, tone, loading }: { label: string; value: string | number; hint: string; tone: keyof typeof TONE; loading: boolean }) {
  return (
    <div className="bg-surface-card border border-subtle rounded-md shadow-xs" style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span className="cm-eyebrow" style={{ color: 'var(--text-muted)' }}>{label}</span>
      {loading ? (
        <span className="cm-skeleton" style={{ width: 56, height: 24, marginTop: 2 }} />
      ) : (
        <span className="font-mono text-strong" style={{ fontSize: 'var(--fs-h2)', fontWeight: 600, lineHeight: 1.1, color: TONE[tone], fontVariantNumeric: 'tabular-nums' }}>
          {value}
        </span>
      )}
      <span className="text-faint" style={{ fontSize: 'var(--fs-caption)' }}>{hint}</span>
    </div>
  )
}

function CoverageSkeleton() {
  return (
    <div style={{ padding: 18, display: 'flex', flexDirection: 'column', gap: 14 }}>
      {Array.from({ length: 7 }).map((_, i) => (
        <div key={i} style={{ display: 'flex', gap: 18, alignItems: 'center' }}>
          <span className="cm-skeleton" style={{ width: 70, height: 14 }} />
          <span className="cm-skeleton" style={{ flex: 1, height: 14 }} />
          <span className="cm-skeleton" style={{ width: 40, height: 14 }} />
          <span className="cm-skeleton" style={{ width: 90, height: 14 }} />
          <span className="cm-skeleton" style={{ width: 90, height: 14 }} />
        </div>
      ))}
    </div>
  )
}

function ErrorBanner({ onRetry }: { onRetry: () => void }) {
  return (
    <div
      role="alert"
      style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', background: 'var(--critical-100)', border: '1px solid var(--critical-500)', borderRadius: 'var(--radius-md)', color: 'var(--critical-600)', fontSize: 'var(--fs-body-sm)' }}
    >
      <Icon name="alert-triangle" size={16} />
      <span style={{ flex: 1 }}>Couldn't load control coverage.</span>
      <button
        type="button"
        onClick={onRetry}
        style={{ height: 'var(--control-h-sm)', padding: '0 12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--critical-500)', background: 'var(--surface-card)', color: 'var(--critical-600)', fontWeight: 600, fontSize: 'var(--fs-body-sm)', cursor: 'pointer' }}
      >
        Retry
      </button>
    </div>
  )
}
