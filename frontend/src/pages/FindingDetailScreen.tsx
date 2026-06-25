import { useEffect, useState, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../lib/api'
import { getAllControls } from '../lib/catalog'
import type { Control } from '../lib/catalog'
import {
  addControlMapping,
  deleteFinding,
  getFinding,
  removeControlMapping,
  type FindingDetail as FindingDetailData,
} from '../lib/findings'
import { formatDate, relativeTime } from '../lib/time'
import { useAuth } from '../auth/AuthContext'
import { Avatar } from '../components/Avatar'
import { Icon, type IconName } from '../components/Icon'
import { Button } from '../components/ui/Button'
import { SearchInput } from '../components/ui/SearchInput'
import { Select } from '../components/ui/Select'
import { SeverityBadge } from '../components/data/SeverityBadge'
import { StatusBadge } from '../components/data/StatusBadge'
import { CvssScore } from '../components/data/CvssScore'
import { FrameworkTag } from '../components/data/FrameworkTag'
import { ConfirmDialog } from '../components/feedback/ConfirmDialog'

const FW_FULL: Record<string, string> = {
  iso27001: 'ISO/IEC 27001:2022',
  owasp: 'OWASP Top 10:2025',
  nist: 'NIST CSF 2.0',
}
const EDITABLE = ['open', 'in-progress', 'returned']

function SectionCard({ title, icon, action, children, pad }: { title?: string; icon?: IconName; action?: ReactNode; children: ReactNode; pad?: string }) {
  return (
    <section className="bg-surface-card border border-subtle rounded-md shadow-xs" style={{ overflow: 'hidden' }}>
      {title ? (
        <header style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '13px 18px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--surface-inset)' }}>
          {icon ? <Icon name={icon} size={16} color="var(--text-muted)" /> : null}
          <h2 className="font-display text-strong" style={{ fontSize: 'var(--fs-h3)', fontWeight: 600, margin: 0, letterSpacing: '-0.005em' }}>{title}</h2>
          {action ? <div style={{ marginLeft: 'auto' }}>{action}</div> : null}
        </header>
      ) : null}
      <div style={{ padding: pad ?? '18px' }}>{children}</div>
    </section>
  )
}

function MetaItem({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span className="cm-eyebrow" style={{ color: 'var(--text-faint)' }}>{label}</span>
      <span style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-body)', display: 'flex', alignItems: 'center', gap: 7 }}>{children}</span>
    </div>
  )
}

function MiniIconButton({ icon, title, onClick }: { icon: IconName; title: string; onClick: () => void }) {
  const [hover, setHover] = useState(false)
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 28, height: 28, borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-default)', background: hover ? 'var(--surface-inset)' : 'var(--surface-card)', cursor: 'pointer', color: 'var(--text-muted)' }}
    >
      <Icon name={icon} size={14} />
    </button>
  )
}

function AddPanel({ allControls, mappedIds, onAdd, onClose }: { allControls: Control[]; mappedIds: Set<number>; onAdd: (id: number) => void; onClose: () => void }) {
  const [q, setQ] = useState('')
  const [fw, setFw] = useState('')
  const query = q.trim().toLowerCase()
  const results = allControls.filter(
    (c) =>
      !mappedIds.has(c.id) &&
      (fw === '' || c.framework === fw) &&
      (query === '' || `${c.code} ${c.title} ${FW_FULL[c.framework] ?? ''}`.toLowerCase().includes(query)),
  )
  return (
    <div style={{ marginTop: 14, border: '1px solid var(--border-default)', borderRadius: 'var(--radius-md)', overflow: 'hidden', background: 'var(--surface-card)', boxShadow: 'var(--shadow-sm)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px', background: 'var(--surface-inset)', borderBottom: '1px solid var(--border-subtle)', flexWrap: 'wrap' }}>
        <SearchInput placeholder="Search controls by code or title…" width={280} value={q} onChange={(e) => setQ(e.target.value)} />
        <Select
          value={fw}
          onChange={(e) => setFw(e.target.value)}
          options={[
            { value: '', label: 'All frameworks' },
            { value: 'iso27001', label: 'ISO 27001' },
            { value: 'owasp', label: 'OWASP' },
            { value: 'nist', label: 'NIST CSF' },
          ]}
        />
        <div style={{ marginLeft: 'auto' }}>
          <MiniIconButton icon="x" title="Close" onClick={onClose} />
        </div>
      </div>
      <div style={{ maxHeight: 260, overflowY: 'auto', padding: 8, display: 'flex', flexDirection: 'column', gap: 4 }}>
        {results.map((c) => (
          <div key={c.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '9px 10px', borderRadius: 'var(--radius-sm)' }}>
            <FrameworkTag framework={c.framework} control={c.code} />
            <span className="text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>{c.title}</span>
            <div style={{ marginLeft: 'auto' }}>
              <Button size="sm" variant="secondary" iconLeft="plus" onClick={() => onAdd(c.id)}>Map</Button>
            </div>
          </div>
        ))}
        {results.length === 0 ? (
          <div className="text-muted" style={{ padding: 26, textAlign: 'center', fontSize: 'var(--fs-body-sm)' }}>No matching controls.</div>
        ) : null}
      </div>
    </div>
  )
}

function ControlMapping({ finding, canEdit, onAdd, onRemove }: { finding: FindingDetailData; canEdit: boolean; onAdd: (id: number) => void; onRemove: (id: number) => void }) {
  const [adding, setAdding] = useState(false)
  const [allControls, setAllControls] = useState<Control[]>([])

  useEffect(() => {
    if (adding && allControls.length === 0) {
      getAllControls().then(setAllControls).catch(() => {})
    }
  }, [adding, allControls.length])

  const mappedIds = new Set(finding.controls.map((c) => c.controlId))
  const frameworks = [...new Set(finding.controls.map((c) => c.framework))]

  return (
    <SectionCard
      title="Control mapping"
      icon="map"
      action={
        canEdit ? (
          <Button size="sm" variant={adding ? 'ghost' : 'primary'} iconLeft={adding ? 'x' : 'plus'} onClick={() => setAdding((a) => !a)}>
            {adding ? 'Done' : 'Add control mapping'}
          </Button>
        ) : null
      }
    >
      <div className="text-muted" style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12, fontSize: 'var(--fs-caption)', flexWrap: 'wrap' }}>
        <span>
          <b className="text-strong" style={{ fontWeight: 600 }}>{finding.controls.length}</b> controls mapped
        </span>
        {frameworks.map((fw) => (
          <FrameworkTag key={fw} framework={fw} />
        ))}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {finding.controls.map((c) => (
          <div key={c.controlId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', background: 'var(--surface-card)' }}>
            <FrameworkTag framework={c.framework} control={c.code} />
            <span className="text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>{c.title}</span>
            <span className="text-faint" style={{ fontSize: 'var(--fs-caption)' }}>{FW_FULL[c.framework]}</span>
            {canEdit ? (
              <div style={{ marginLeft: 'auto' }}>
                <MiniIconButton icon="x" title="Remove mapping" onClick={() => onRemove(c.controlId)} />
              </div>
            ) : null}
          </div>
        ))}
        {finding.controls.length === 0 ? (
          <div className="text-muted" style={{ padding: 20, textAlign: 'center', fontSize: 'var(--fs-body-sm)', border: '1px dashed var(--border-default)', borderRadius: 'var(--radius-sm)' }}>
            No controls mapped yet.{canEdit ? ' Use “Add control mapping”.' : ''}
          </div>
        ) : null}
      </div>
      {adding ? <AddPanel allControls={allControls} mappedIds={mappedIds} onAdd={onAdd} onClose={() => setAdding(false)} /> : null}
    </SectionCard>
  )
}

export function FindingDetailScreen() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()

  const [finding, setFinding] = useState<FindingDetailData | null>(null)
  const [load, setLoad] = useState<'loading' | 'ready' | 'error'>('loading')
  const [actionError, setActionError] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    getFinding(id!)
      .then((f) => {
        setFinding(f)
        setLoad('ready')
      })
      .catch(() => setLoad('error'))
  }, [id])

  const isOwner = !!user && !!finding && user.name === finding.owner
  const canEdit = isOwner && !!finding && EDITABLE.includes(finding.status)

  async function mutate(promise: Promise<FindingDetailData>) {
    setActionError(null)
    try {
      setFinding(await promise)
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong.')
    }
  }

  async function confirmDelete() {
    setDeleting(true)
    setActionError(null)
    try {
      await deleteFinding(id!)
      navigate('/')
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not delete the finding.')
      setDeleting(false)
      setConfirmOpen(false)
    }
  }

  if (load === 'loading') {
    return <div className="text-muted" style={{ padding: 'var(--space-9)', textAlign: 'center', fontSize: 'var(--fs-body-sm)' }}>Loading…</div>
  }
  if (load === 'error' || !finding) {
    return (
      <div style={{ maxWidth: 900 }}>
        <div role="alert" style={{ padding: '14px 16px', background: 'var(--critical-100)', border: '1px solid var(--critical-500)', borderRadius: 'var(--radius-md)', color: 'var(--critical-600)', fontSize: 'var(--fs-body-sm)' }}>
          Couldn't load this finding.
        </div>
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 900, display: 'flex', flexDirection: 'column', gap: 18 }}>
      {actionError ? (
        <div role="alert" style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '10px 12px', background: 'var(--critical-100)', border: '1px solid var(--critical-500)', borderRadius: 'var(--radius-sm)', fontSize: 'var(--fs-body-sm)', color: 'var(--critical-600)' }}>
          <Icon name="alert-triangle" size={15} />
          {actionError}
        </div>
      ) : null}

      <SectionCard pad="20px 22px">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span className="font-mono text-muted" style={{ fontSize: 12 }}>{finding.reference}</span>
            {finding.asset?.name ? <span style={{ width: 3, height: 3, borderRadius: '50%', background: 'var(--border-strong)' }} /> : null}
            {finding.asset?.name ? <span className="text-muted" style={{ fontSize: 'var(--fs-caption)' }}>{finding.asset.name}</span> : null}
            <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
              {canEdit ? <Button size="sm" variant="secondary" onClick={() => navigate(`/findings/${finding.id}/edit`)}>Edit finding</Button> : null}
              {canEdit ? <Button size="sm" variant="danger" iconLeft="trash" onClick={() => setConfirmOpen(true)}>Delete</Button> : null}
            </div>
          </div>
          <h1 className="font-display text-strong" style={{ fontSize: 27, fontWeight: 600, margin: 0, lineHeight: 1.2, letterSpacing: '-0.01em' }}>{finding.title}</h1>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 28, flexWrap: 'wrap', paddingTop: 4 }}>
            <MetaItem label="Severity"><SeverityBadge level={finding.severity} /></MetaItem>
            <MetaItem label="CVSS"><CvssScore score={finding.cvss} /></MetaItem>
            <MetaItem label="Status"><StatusBadge status={finding.status} /></MetaItem>
            <MetaItem label="Owner"><Avatar name={finding.owner} size={22} /><span style={{ fontSize: 'var(--fs-body-sm)' }}>{finding.owner}</span></MetaItem>
            <MetaItem label="Created"><span className="font-mono">{formatDate(finding.createdAt)}</span></MetaItem>
            <MetaItem label="Last updated"><span>{relativeTime(finding.updatedAt)}</span></MetaItem>
          </div>
        </div>
      </SectionCard>

      <SectionCard title="Description" icon="file-text">
        <p className="text-body" style={{ margin: 0, fontSize: 'var(--fs-body)', lineHeight: 1.6 }}>
          {finding.description || <span className="text-faint">No description provided.</span>}
        </p>
      </SectionCard>

      {finding.asset ? (
        <SectionCard title="Affected asset" icon="shield">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '14px 24px' }}>
            {([['Asset', finding.asset.name], ['Environment', finding.asset.env], ['Component', finding.asset.component], ['Host', finding.asset.url]] as const).map(([k, v]) => (
              <div key={k} style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                <span className="cm-eyebrow" style={{ color: 'var(--text-faint)' }}>{k}</span>
                <span className="font-mono text-strong" style={{ fontSize: 'var(--fs-body-sm)' }}>{v || <span className="text-faint">—</span>}</span>
              </div>
            ))}
          </div>
        </SectionCard>
      ) : null}

      <ControlMapping
        finding={finding}
        canEdit={canEdit}
        onAdd={(controlId) => mutate(addControlMapping(finding.id, controlId))}
        onRemove={(controlId) => mutate(removeControlMapping(finding.id, controlId))}
      />

      <ConfirmDialog
        open={confirmOpen}
        title="Delete finding?"
        body="This permanently deletes the finding along with its control mappings and audit history. This action cannot be undone."
        confirmLabel="Delete finding"
        icon="trash"
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  )
}
