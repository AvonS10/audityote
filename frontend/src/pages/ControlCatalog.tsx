import { useCallback, useEffect, useMemo, useState } from 'react'
import { getControls, getFrameworks, type Control, type Framework } from '../lib/catalog'
import { Icon } from '../components/Icon'
import { Select } from '../components/ui/Select'
import { SearchInput } from '../components/ui/SearchInput'

type Status = 'loading' | 'ready' | 'error'

type Row = Control | { header: string }

/** Read-only reference of the seeded framework controls (PLAN §9): framework picker, search, and a
 *  controls table grouped by category — wired to GET /api/frameworks and /api/controls. */
export function ControlCatalog() {
  const [frameworks, setFrameworks] = useState<Framework[]>([])
  const [fw, setFw] = useState('')
  const [controls, setControls] = useState<Control[]>([])
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

  const loadControls = useCallback(async (slug: string) => {
    setStatus('loading')
    try {
      setControls(await getControls(slug))
      setStatus('ready')
    } catch {
      setStatus('error')
    }
  }, [])

  useEffect(() => {
    loadFrameworks()
  }, [loadFrameworks])

  useEffect(() => {
    if (fw) loadControls(fw)
  }, [fw, loadControls])

  const meta = frameworks.find((f) => f.slug === fw)

  const rows = useMemo(() => {
    const query = q.trim().toLowerCase()
    const matched = controls.filter(
      (c) => !query || `${c.code} ${c.title} ${c.description ?? ''} ${c.category ?? ''}`.toLowerCase().includes(query),
    )
    const display: Row[] = []
    let lastGroup: string | null = null
    for (const c of matched) {
      if (c.category && c.category !== lastGroup) {
        display.push({ header: c.category })
        lastGroup = c.category
      }
      display.push(c)
    }
    return { matched, display }
  }, [controls, q])

  return (
    <div style={{ maxWidth: 1080, display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', fontWeight: 600, margin: 0, letterSpacing: '-0.01em' }}>
            Control catalog
          </h1>
          <p className="text-muted" style={{ fontSize: 'var(--fs-body-sm)', margin: '4px 0 0' }}>
            Reference library of seeded framework controls. Read-only.
          </p>
        </div>
        {frameworks.length > 0 ? (
          <Select
            value={fw}
            onChange={(e) => {
              setFw(e.target.value)
              setQ('')
            }}
            options={frameworks.map((f) => ({ value: f.slug, label: `${f.name} ${f.version}` }))}
          />
        ) : null}
      </div>

      {status === 'error' ? (
        <Banner onRetry={() => (frameworks.length ? loadControls(fw) : loadFrameworks())} />
      ) : (
        <>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <SearchInput placeholder="Search by code, title or description…" value={q} onChange={(e) => setQ(e.target.value)} />
            {meta ? (
              <span className="text-muted" style={{ fontSize: 'var(--fs-caption)' }}>
                <b className="text-strong" style={{ fontWeight: 600 }}>
                  {rows.matched.length}
                </b>{' '}
                controls<span className="text-faint"> · {meta.name}</span>
              </span>
            ) : null}
          </div>

          <div
            className="bg-surface-card border border-subtle rounded-md shadow-xs"
            style={{ overflow: 'hidden' }}
          >
            {status === 'loading' ? (
              <CatalogSkeleton />
            ) : (
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    {['Code', 'Control', 'Description'].map((h, i) => (
                      <th
                        key={h}
                        className="cm-eyebrow"
                        style={{
                          textAlign: 'left',
                          padding: '10px 18px',
                          width: i === 0 ? 110 : i === 1 ? 252 : 'auto',
                          background: 'var(--surface-inset)',
                          borderBottom: '1px solid var(--border-default)',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.display.map((row) =>
                    'header' in row ? (
                      <tr key={`h-${row.header}`}>
                        <td
                          colSpan={3}
                          className="cm-eyebrow"
                          style={{ padding: '9px 18px', background: 'var(--surface-sunken)', borderBottom: '1px solid var(--border-subtle)', fontWeight: 700 }}
                        >
                          {row.header}
                        </td>
                      </tr>
                    ) : (
                      <tr key={row.id} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                        <td style={{ padding: '13px 18px', verticalAlign: 'top' }}>
                          <span className="font-mono text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>
                            {row.code}
                          </span>
                        </td>
                        <td style={{ padding: '13px 18px', verticalAlign: 'top' }}>
                          <span className="text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>
                            {row.title}
                          </span>
                        </td>
                        <td style={{ padding: '13px 18px', verticalAlign: 'top' }}>
                          <span className="text-body" style={{ fontSize: 'var(--fs-body-sm)', lineHeight: 1.55 }}>
                            {row.description}
                          </span>
                        </td>
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            )}
            {status === 'ready' && rows.matched.length === 0 ? (
              <div className="text-muted" style={{ padding: 40, textAlign: 'center', fontSize: 'var(--fs-body-sm)' }}>
                No controls match “{q}”.
              </div>
            ) : null}
          </div>
        </>
      )}
    </div>
  )
}

function CatalogSkeleton() {
  return (
    <div style={{ padding: 18, display: 'flex', flexDirection: 'column', gap: 14 }}>
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} style={{ display: 'flex', gap: 18 }}>
          <span className="cm-skeleton" style={{ width: 70, height: 14 }} />
          <span className="cm-skeleton" style={{ width: 200, height: 14 }} />
          <span className="cm-skeleton" style={{ flex: 1, height: 14 }} />
        </div>
      ))}
    </div>
  )
}

function Banner({ onRetry }: { onRetry: () => void }) {
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
      <span style={{ flex: 1 }}>Couldn't load the control catalog.</span>
      <button
        type="button"
        onClick={onRetry}
        style={{
          height: 'var(--control-h-sm)',
          padding: '0 12px',
          borderRadius: 'var(--radius-sm)',
          border: '1px solid var(--critical-500)',
          background: 'var(--surface-card)',
          color: 'var(--critical-600)',
          fontWeight: 600,
          fontSize: 'var(--fs-body-sm)',
          cursor: 'pointer',
        }}
      >
        Retry
      </button>
    </div>
  )
}
