import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getFrameworks } from '../lib/catalog'
import { getFindings, type FindingFilters, type FindingSummary } from '../lib/findings'
import { relativeTime } from '../lib/time'
import { Avatar } from '../components/Avatar'
import { ExportMenu } from '../components/ExportMenu'
import { Icon } from '../components/Icon'
import { auditReportPath, findingsReportPath } from '../lib/reports'
import { Button } from '../components/ui/Button'
import { SearchInput } from '../components/ui/SearchInput'
import { Select } from '../components/ui/Select'
import { SeverityBadge } from '../components/data/SeverityBadge'
import { StatusBadge } from '../components/data/StatusBadge'
import { CvssScore } from '../components/data/CvssScore'
import { RiskScore } from '../components/data/RiskScore'
import { FrameworkTag } from '../components/data/FrameworkTag'

type Status = 'loading' | 'ready' | 'error'

const STATUS_OPTIONS = [
  { value: '', label: 'All statuses' },
  { value: 'open', label: 'Open' },
  { value: 'in-progress', label: 'In Progress' },
  { value: 'submitted', label: 'Submitted' },
  { value: 'approved', label: 'Approved' },
  { value: 'returned', label: 'Returned' },
  { value: 'remediated', label: 'Remediated' },
  { value: 'accepted', label: 'Accepted' },
]
const SEVERITY_OPTIONS = [
  { value: '', label: 'All severities' },
  { value: 'critical', label: 'Critical' },
  { value: 'high', label: 'High' },
  { value: 'medium', label: 'Medium' },
  { value: 'low', label: 'Low' },
]

export function Dashboard() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState<FindingFilters>({})
  const [findings, setFindings] = useState<FindingSummary[]>([])
  const [total, setTotal] = useState(0)
  const [frameworkOptions, setFrameworkOptions] = useState([{ value: '', label: 'All frameworks' }])
  const [status, setStatus] = useState<Status>('loading')

  useEffect(() => {
    getFrameworks()
      .then((fws) => setFrameworkOptions([{ value: '', label: 'All frameworks' }, ...fws.map((f) => ({ value: f.slug, label: f.name }))]))
      .catch(() => {})
  }, [])

  const load = useCallback(async (f: FindingFilters) => {
    setStatus('loading')
    try {
      const page = await getFindings(f)
      setFindings(page.content)
      setTotal(page.totalElements)
      setStatus('ready')
    } catch {
      setStatus('error')
    }
  }, [])

  useEffect(() => {
    const t = setTimeout(() => load(filters), 250)
    return () => clearTimeout(t)
  }, [filters, load])

  const setFilter = (key: keyof FindingFilters) => (value: string) =>
    setFilters((f) => ({ ...f, [key]: value || undefined }))

  const showDeleted = !!filters.deleted

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div>
        <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', fontWeight: 600, margin: 0, letterSpacing: '-0.01em' }}>
          Findings
        </h1>
        <p className="text-muted" style={{ fontSize: 'var(--fs-body-sm)', margin: '4px 0 0' }}>
          Security findings mapped to controls, tracked through review.
        </p>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <SearchInput placeholder="Search findings…" width={240} value={filters.q ?? ''} onChange={(e) => setFilter('q')(e.target.value)} />
        <Select value={filters.status ?? ''} onChange={(e) => setFilter('status')(e.target.value)} options={STATUS_OPTIONS} />
        <Select value={filters.severity ?? ''} onChange={(e) => setFilter('severity')(e.target.value)} options={SEVERITY_OPTIONS} />
        <Select value={filters.framework ?? ''} onChange={(e) => setFilter('framework')(e.target.value)} options={frameworkOptions} />
        <Button
          variant={showDeleted ? 'primary' : 'secondary'}
          iconLeft="trash"
          onClick={() => setFilters((f) => ({ ...f, deleted: f.deleted ? undefined : true }))}
          title="Show deleted findings (retained read-only for audit)"
        >
          Deleted
        </Button>
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 12 }}>
          <span className="text-muted" style={{ fontSize: 'var(--fs-caption)', fontVariantNumeric: 'tabular-nums' }}>
            <b className="text-strong" style={{ fontWeight: 600 }}>{total}</b> {showDeleted ? 'deleted' : 'findings'}
          </span>
          <ExportMenu
            reports={[
              { label: 'Findings register', csvPath: findingsReportPath('csv'), pdfPath: findingsReportPath('pdf') },
              { label: 'Audit log', csvPath: auditReportPath('csv'), pdfPath: auditReportPath('pdf') },
            ]}
          />
          <Button variant="primary" iconLeft="plus" onClick={() => navigate('/findings/new')}>
            New finding
          </Button>
        </div>
      </div>

      {status === 'error' ? (
        <ErrorBanner onRetry={() => load(filters)} />
      ) : status === 'loading' && findings.length === 0 ? (
        <TableSkeleton />
      ) : (
        <FindingsTable rows={findings} deleted={showDeleted} onRowClick={(id) => navigate(`/findings/${id}`)} onClear={() => setFilters({})} />
      )}
    </div>
  )
}

// ---- table ----

interface Col {
  key: string
  label: string
  sortable: boolean
  align?: 'left' | 'right'
  w?: number
}

const COLS: Col[] = [
  { key: 'title', label: 'Finding', sortable: true, align: 'left' },
  { key: 'severity', label: 'Severity', sortable: true, w: 108 },
  { key: 'cvss', label: 'CVSS', sortable: true, w: 92 },
  { key: 'risk', label: 'Risk', sortable: true, w: 120 },
  { key: 'status', label: 'Status', sortable: true, w: 132 },
  { key: 'frameworks', label: 'Frameworks', sortable: false, w: 176 },
  { key: 'owner', label: 'Owner', sortable: true, w: 150 },
  { key: 'updated', label: 'Updated', sortable: true, align: 'right', w: 104 },
]

const SEV_ORDER: Record<string, number> = { critical: 0, high: 1, medium: 2, low: 3 }

function compare(a: FindingSummary, b: FindingSummary, key: string): number {
  switch (key) {
    case 'severity':
      return (SEV_ORDER[a.severity] ?? 9) - (SEV_ORDER[b.severity] ?? 9)
    case 'cvss':
      return (a.cvss ?? -1) - (b.cvss ?? -1)
    case 'risk':
      return a.riskScore - b.riskScore
    case 'updated':
      return new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime()
    case 'status':
      return a.status.localeCompare(b.status)
    case 'owner':
      return a.owner.localeCompare(b.owner)
    default:
      return a.title.localeCompare(b.title)
  }
}

function FindingsTable({ rows, deleted = false, onRowClick, onClear }: { rows: FindingSummary[]; deleted?: boolean; onRowClick: (id: number) => void; onClear: () => void }) {
  const [sort, setSort] = useState<{ key: string; dir: 'asc' | 'desc' }>({ key: 'severity', dir: 'asc' })
  const toggle = (key: string) => setSort((s) => (s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' }))

  const sorted = useMemo(() => {
    const arr = [...rows]
    arr.sort((a, b) => (sort.dir === 'asc' ? 1 : -1) * compare(a, b, sort.key))
    return arr
  }, [rows, sort])

  return (
    <div className="bg-surface-card border border-subtle rounded-md shadow-xs" style={{ overflow: 'hidden' }}>
      <div className="cm-findings-scroll" style={{ overflowX: 'auto' }}>
        <table className="cm-findings" style={{ width: '100%', borderCollapse: 'collapse', minWidth: 1040 }}>
          <thead>
            <tr>
              {COLS.map((col) => {
                const active = sort.key === col.key
                return (
                  <th
                    key={col.key}
                    onClick={() => col.sortable && toggle(col.key)}
                    className="cm-eyebrow"
                    style={{
                      textAlign: col.align ?? 'left',
                      width: col.w,
                      padding: '0 16px',
                      height: 38,
                      color: active ? 'var(--text-strong)' : 'var(--text-muted)',
                      whiteSpace: 'nowrap',
                      borderBottom: '1px solid var(--border-default)',
                      background: 'var(--surface-inset)',
                      cursor: col.sortable ? 'pointer' : 'default',
                      userSelect: 'none',
                    }}
                  >
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, justifyContent: col.align === 'right' ? 'flex-end' : 'flex-start' }}>
                      {col.label}
                      {col.sortable ? (
                        <Icon
                          name={active ? (sort.dir === 'asc' ? 'chevron-up' : 'chevron-down') : 'chevron-down'}
                          size={13}
                          color={active ? 'var(--primary)' : 'var(--text-faint)'}
                        />
                      ) : null}
                    </span>
                  </th>
                )
              })}
            </tr>
          </thead>
          <tbody>
            {sorted.map((f) => (
              <FindingRow key={f.id} f={f} deleted={deleted} onClick={() => onRowClick(f.id)} />
            ))}
          </tbody>
        </table>
      </div>
      {sorted.length === 0 ? (
        <div style={{ padding: 40, textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
          <Icon name="search" size={22} color="var(--text-faint)" />
          <div>
            <p className="text-strong" style={{ fontWeight: 600, margin: 0, fontSize: 'var(--fs-body-sm)' }}>
              {deleted ? 'No deleted findings' : 'No findings match your filters'}
            </p>
            <p className="text-muted" style={{ margin: '4px 0 0', fontSize: 'var(--fs-body-sm)' }}>
              {deleted ? 'Deleted findings are retained here read-only for audit.' : 'Try removing a filter or clearing your search.'}
            </p>
          </div>
          <Button variant="secondary" iconLeft="x" onClick={onClear}>
            Clear filters
          </Button>
        </div>
      ) : null}
    </div>
  )
}

function FindingRow({ f, deleted = false, onClick }: { f: FindingSummary; deleted?: boolean; onClick: () => void }) {
  const [hover, setHover] = useState(false)
  const td = { padding: '0 16px', height: 'var(--row-h)', borderBottom: '1px solid var(--border-subtle)', verticalAlign: 'middle' } as const
  return (
    <tr
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{ background: hover ? 'var(--surface-inset)' : 'var(--surface-card)', transition: 'background 90ms ease', cursor: 'pointer', opacity: deleted ? 0.72 : 1 }}
    >
      <td style={td}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 1, minWidth: 0 }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
            <span className="text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 320 }}>
              {f.title}
            </span>
            {deleted ? (
              <span style={{ flex: 'none', display: 'inline-flex', alignItems: 'center', gap: 4, height: 18, padding: '0 7px', borderRadius: 'var(--radius-xs)', background: 'var(--critical-100)', color: 'var(--critical-600)', fontSize: 'var(--fs-micro)', fontWeight: 600 }}>
                <Icon name="trash" size={11} color="var(--critical-600)" />
                Deleted
              </span>
            ) : null}
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 11, color: 'var(--text-faint)' }}>
            <span className="font-mono">{f.reference}</span>
            {f.asset ? <span style={{ width: 3, height: 3, borderRadius: '50%', background: 'var(--border-strong)' }} /> : null}
            {f.asset ? <span>{f.asset}</span> : null}
          </span>
        </div>
      </td>
      <td style={td}><SeverityBadge level={f.severity} /></td>
      <td style={td}><CvssScore score={f.cvss} /></td>
      <td style={td}><RiskScore score={f.riskScore} source={f.riskSource} /></td>
      <td style={td}><StatusBadge status={f.status} /></td>
      <td style={td}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
          {f.controls.map((c, i) => (
            <FrameworkTag key={i} framework={c.framework} control={c.code} />
          ))}
        </div>
      </td>
      <td style={td}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Avatar name={f.owner} size={24} />
          <span className="text-body" style={{ fontSize: 'var(--fs-body-sm)', whiteSpace: 'nowrap' }}>{f.owner}</span>
        </span>
      </td>
      <td style={{ ...td, textAlign: 'right' }}>
        <span className="text-muted" style={{ fontSize: 'var(--fs-caption)', whiteSpace: 'nowrap' }}>{relativeTime(f.updatedAt)}</span>
      </td>
    </tr>
  )
}

function TableSkeleton() {
  return (
    <div className="bg-surface-card border border-subtle rounded-md shadow-xs" style={{ padding: 18, display: 'flex', flexDirection: 'column', gap: 16 }}>
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
          <span className="cm-skeleton" style={{ flex: 1, height: 14 }} />
          <span className="cm-skeleton" style={{ width: 80, height: 14 }} />
          <span className="cm-skeleton" style={{ width: 60, height: 14 }} />
          <span className="cm-skeleton" style={{ width: 100, height: 14 }} />
        </div>
      ))}
    </div>
  )
}

function ErrorBanner({ onRetry }: { onRetry: () => void }) {
  return (
    <div
      role="alert"
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '12px 16px',
        background: 'var(--critical-100)',
        border: '1px solid var(--critical-500)',
        borderRadius: 'var(--radius-md)',
        color: 'var(--critical-600)',
        fontSize: 'var(--fs-body-sm)',
      }}
    >
      <Icon name="alert-triangle" size={16} />
      <span style={{ flex: 1 }}>Couldn't load findings.</span>
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
