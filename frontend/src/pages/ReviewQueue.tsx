import { useEffect, useState, type ReactNode } from 'react'
import { ApiError } from '../lib/api'
import {
  getFinding,
  getReviewQueue,
  transitionFinding,
  type FindingDetail,
  type FindingSummary,
} from '../lib/findings'
import { relativeTime } from '../lib/time'
import { useAuth } from '../auth/AuthContext'
import { Avatar } from '../components/Avatar'
import { Icon, type IconName } from '../components/Icon'
import { Button } from '../components/ui/Button'
import { SeverityBadge } from '../components/data/SeverityBadge'
import { StatusBadge } from '../components/data/StatusBadge'
import { CvssScore } from '../components/data/CvssScore'
import { RiskScore } from '../components/data/RiskScore'
import { FrameworkTag } from '../components/data/FrameworkTag'
import { ReturnDialog } from '../components/feedback/ReturnDialog'
import { useToast } from '../components/feedback/ToastProvider'

const FW_FULL: Record<string, string> = {
  iso27001: 'ISO/IEC 27001:2022',
  owasp: 'OWASP Top 10:2025',
  nist: 'NIST CSF 2.0',
}

/** One row in the master list: title + severity, owner, mapping count, time submitted. */
function QueueItem({ f, selected, onSelect }: { f: FindingSummary; selected: boolean; onSelect: (id: number) => void }) {
  const [hover, setHover] = useState(false)
  return (
    <button
      type="button"
      onClick={() => onSelect(f.id)}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        textAlign: 'left', width: '100%', display: 'flex', flexDirection: 'column', gap: 8, padding: '13px 16px', cursor: 'pointer',
        background: selected ? 'var(--primary-soft)' : hover ? 'var(--surface-inset)' : 'transparent',
        border: 'none', borderLeft: `3px solid ${selected ? 'var(--primary)' : 'transparent'}`,
        borderBottom: '1px solid var(--border-subtle)', transition: 'background 90ms ease',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
        <span className="text-strong" style={{ flex: 1, fontSize: 'var(--fs-body-sm)', fontWeight: 600, lineHeight: 1.35 }}>{f.title}</span>
        <SeverityBadge level={f.severity} size="sm" />
      </div>
      <div className="text-muted" style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 'var(--fs-caption)' }}>
        <Avatar name={f.owner} size={18} />
        <span>{f.owner.split(' ')[0]}</span>
        <span style={{ width: 3, height: 3, borderRadius: '50%', background: 'var(--border-strong)' }} />
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><Icon name="map" size={12} />{f.controls.length}</span>
        <span className="text-faint" style={{ marginLeft: 'auto', fontSize: 'var(--fs-micro)' }}>{relativeTime(f.updatedAt)}</span>
      </div>
    </button>
  )
}

function Section({ title, icon, children }: { title: string; icon: IconName; children: ReactNode }) {
  return (
    <section className="bg-surface-card border border-subtle rounded-md shadow-xs" style={{ overflow: 'hidden' }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--surface-inset)' }}>
        <Icon name={icon} size={15} color="var(--text-muted)" />
        <h2 className="font-display text-strong" style={{ fontSize: 'var(--fs-h3)', fontWeight: 600, margin: 0 }}>{title}</h2>
      </header>
      <div style={{ padding: 16 }}>{children}</div>
    </section>
  )
}

function MetaPair({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <span className="cm-eyebrow" style={{ color: 'var(--text-faint)' }}>{label}</span>
      <span style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-body)', display: 'flex', alignItems: 'center', gap: 7 }}>{children}</span>
    </div>
  )
}

/** Centred neutral message — used for "nothing selected", "queue clear", and load errors (no coyote in working chrome). */
function PanelMessage({ icon, title, body, action }: { icon: IconName; title: string; body: string; action?: ReactNode }) {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12, padding: 40, textAlign: 'center' }}>
      <Icon name={icon} size={34} color="var(--text-faint)" />
      <span className="font-display text-strong" style={{ fontSize: 'var(--fs-h2)', fontWeight: 600 }}>{title}</span>
      <span className="text-muted" style={{ fontSize: 'var(--fs-body-sm)', maxWidth: 360 }}>{body}</span>
      {action}
    </div>
  )
}

/** Sign-off detail for the selected finding: full context + Approve / Return-for-changes. */
function ReviewPanel({
  detail, load, isOwnFinding, busy, onApprove, onReturn,
}: {
  detail: FindingDetail | null
  load: 'loading' | 'ready' | 'error'
  isOwnFinding: boolean
  busy: boolean
  onApprove: () => void
  onReturn: () => void
}) {
  if (load === 'loading') {
    return <PanelMessage icon="clipboard-check" title="Loading finding…" body="Fetching the full finding for review." />
  }
  if (load === 'error' || !detail) {
    return <PanelMessage icon="alert-triangle" title="Couldn't load finding" body="The selected finding could not be loaded. Pick another, or try again shortly." />
  }
  return (
    <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
      <div style={{ maxWidth: 760, margin: '0 auto', padding: '24px 28px 36px', display: 'flex', flexDirection: 'column', gap: 22 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span className="font-mono text-muted" style={{ fontSize: 12 }}>{detail.reference}</span>
            {detail.asset?.name ? <span style={{ width: 3, height: 3, borderRadius: '50%', background: 'var(--border-strong)' }} /> : null}
            {detail.asset?.name ? <span className="text-muted" style={{ fontSize: 'var(--fs-caption)' }}>{detail.asset.name}</span> : null}
            <span style={{ marginLeft: 'auto' }}><StatusBadge status={detail.status} /></span>
          </div>
          <h1 className="font-display text-strong" style={{ fontSize: 24, fontWeight: 600, margin: 0, lineHeight: 1.25, letterSpacing: '-0.01em' }}>{detail.title}</h1>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 26, flexWrap: 'wrap', paddingTop: 2 }}>
            <MetaPair label="Severity"><SeverityBadge level={detail.severity} /></MetaPair>
            <MetaPair label="CVSS"><CvssScore score={detail.cvss} /></MetaPair>
            <MetaPair label="Risk score"><RiskScore score={detail.riskScore} source={detail.riskSource} /></MetaPair>
            <MetaPair label="Submitted by"><Avatar name={detail.owner} size={22} /><span>{detail.owner}</span></MetaPair>
            <MetaPair label="Submitted"><span>{relativeTime(detail.updatedAt)}</span></MetaPair>
          </div>
        </div>

        <Section title="Finding summary" icon="file-text">
          <p className="text-body" style={{ margin: 0, fontSize: 'var(--fs-body)', lineHeight: 1.6 }}>
            {detail.description || <span className="text-faint">No description provided.</span>}
          </p>
          {detail.asset?.name ? (
            <div className="text-muted" style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 8, fontSize: 'var(--fs-body-sm)' }}>
              <Icon name="shield" size={14} color="var(--text-faint)" />
              Affected asset: <span className="font-mono text-strong">{detail.asset.name}</span>
            </div>
          ) : null}
        </Section>

        <Section title={`Mapped controls (${detail.controls.length})`} icon="map">
          {detail.controls.length === 0 ? (
            <div className="text-muted" style={{ padding: 16, textAlign: 'center', fontSize: 'var(--fs-body-sm)' }}>No controls mapped.</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {detail.controls.map((c) => (
                <div key={c.controlId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '9px 12px', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)' }}>
                  <FrameworkTag framework={c.framework} control={c.code} />
                  <span className="text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>{c.title}</span>
                  <span className="text-faint" style={{ fontSize: 'var(--fs-caption)' }}>{FW_FULL[c.framework]}</span>
                </div>
              ))}
            </div>
          )}
        </Section>

        <Section title="Review decision" icon="clipboard-check">
          {isOwnFinding ? (
            <div className="text-muted" style={{ display: 'flex', alignItems: 'center', gap: 9, fontSize: 'var(--fs-body-sm)' }}>
              <Icon name="alert-triangle" size={15} color="var(--text-faint)" />
              You submitted this finding. Separation of duties means another reviewer must sign it off.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <p className="text-muted" style={{ margin: 0, fontSize: 'var(--fs-body-sm)', lineHeight: 1.5 }}>
                Approve to advance the finding, or return it for changes — a comment is required when returning.
              </p>
              <div style={{ display: 'flex', gap: 10 }}>
                <Button variant="primary" iconLeft="check" disabled={busy} onClick={onApprove}>Approve</Button>
                <Button variant="secondary" iconLeft="arrow-up-right" disabled={busy} onClick={onReturn}>Return for changes</Button>
              </div>
            </div>
          )}
        </Section>
      </div>
    </div>
  )
}

export function ReviewQueue() {
  const { user } = useAuth()
  const { toast } = useToast()

  const [queue, setQueue] = useState<FindingSummary[]>([])
  const [load, setLoad] = useState<'loading' | 'ready' | 'error'>('loading')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<FindingDetail | null>(null)
  const [detailLoad, setDetailLoad] = useState<'loading' | 'ready' | 'error'>('loading')
  const [busy, setBusy] = useState(false)
  const [returnOpen, setReturnOpen] = useState(false)
  const [reviewed, setReviewed] = useState(0)

  function loadQueue() {
    setLoad('loading')
    getReviewQueue()
      .then((items) => {
        setQueue(items)
        setSelectedId((prev) => (prev && items.some((f) => f.id === prev) ? prev : items[0]?.id ?? null))
        setLoad('ready')
      })
      .catch(() => setLoad('error'))
  }

  useEffect(loadQueue, [])

  // Fetch the full finding for the panel whenever the selection changes; guard against out-of-order resolves.
  useEffect(() => {
    if (selectedId == null) {
      setDetail(null)
      return
    }
    let active = true
    setDetailLoad('loading')
    getFinding(selectedId)
      .then((f) => {
        if (!active) return
        setDetail(f)
        setDetailLoad('ready')
      })
      .catch(() => {
        if (active) setDetailLoad('error')
      })
    return () => {
      active = false
    }
  }, [selectedId])

  const isOwnFinding = !!user && !!detail && user.name === detail.owner

  // After a decision: drop the finding from the queue and move to the neighbouring one.
  function afterDecision(id: number) {
    setQueue((q) => {
      const idx = q.findIndex((f) => f.id === id)
      const next = q[idx + 1] ?? q[idx - 1] ?? null
      setSelectedId(next ? next.id : null)
      return q.filter((f) => f.id !== id)
    })
    setReviewed((n) => n + 1)
  }

  async function decide(action: 'approve' | 'return', comment?: string) {
    if (!detail) return
    const id = detail.id
    setBusy(true)
    try {
      await transitionFinding(id, action, comment)
      toast({ tone: 'success', title: action === 'approve' ? 'Finding approved' : 'Returned for changes' })
      afterDecision(id)
    } catch (err) {
      toast({ tone: 'error', title: 'Action failed', message: err instanceof ApiError ? err.message : 'The decision could not be recorded.' })
      // The finding may have moved on the server (someone else acted) — re-sync the queue.
      loadQueue()
    } finally {
      setBusy(false)
      setReturnOpen(false)
    }
  }

  const selectedExists = selectedId != null

  return (
    <div className="cm-review" style={{ display: 'flex', height: '100%', minHeight: 0 }}>
      {/* Master list */}
      <aside
        className="cm-review-list"
        style={{ width: 360, flex: 'none', borderRight: '1px solid var(--border-subtle)', background: 'var(--surface-card)', display: 'flex', flexDirection: 'column', minHeight: 0 }}
      >
        <div style={{ padding: '16px 16px 12px', borderBottom: '1px solid var(--border-subtle)' }}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h2)', fontWeight: 600, margin: 0, letterSpacing: '-0.01em', whiteSpace: 'nowrap' }}>Review queue</h1>
            <span className="font-mono text-muted" style={{ fontSize: 13, fontWeight: 600, background: 'var(--surface-inset)', padding: '1px 8px', borderRadius: 'var(--radius-pill)' }}>{queue.length}</span>
          </div>
          <p className="text-muted" style={{ margin: '4px 0 0', fontSize: 'var(--fs-caption)' }}>Findings submitted for sign-off, oldest first.</p>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
          {load === 'loading' ? (
            <div className="text-muted" style={{ padding: '28px 16px', textAlign: 'center', fontSize: 'var(--fs-body-sm)' }}>Loading…</div>
          ) : load === 'error' ? (
            <div style={{ padding: '20px 16px' }}>
              <div role="alert" style={{ padding: '12px 14px', background: 'var(--critical-100)', border: '1px solid var(--critical-500)', borderRadius: 'var(--radius-md)', color: 'var(--critical-600)', fontSize: 'var(--fs-body-sm)', display: 'flex', flexDirection: 'column', gap: 10 }}>
                <span>Couldn't load the review queue.</span>
                <Button size="sm" variant="secondary" onClick={loadQueue}>Retry</Button>
              </div>
            </div>
          ) : (
            <>
              {queue.map((f) => (
                <QueueItem key={f.id} f={f} selected={f.id === selectedId} onSelect={setSelectedId} />
              ))}
              {queue.length === 0 ? (
                <div className="text-muted" style={{ padding: '28px 16px', textAlign: 'center', fontSize: 'var(--fs-body-sm)' }}>No findings awaiting review.</div>
              ) : null}
              {reviewed > 0 ? (
                <div className="text-muted" style={{ padding: '12px 16px', borderTop: '1px solid var(--border-subtle)', display: 'flex', alignItems: 'center', gap: 6, fontSize: 'var(--fs-caption)' }}>
                  <Icon name="check" size={13} color="var(--status-approved)" />{reviewed} reviewed this session
                </div>
              ) : null}
            </>
          )}
        </div>
      </aside>

      {/* Detail / sign-off panel */}
      {load === 'ready' && !selectedExists ? (
        <PanelMessage icon="clipboard-check" title="Queue clear" body="All submitted findings have been reviewed. Nothing is waiting on you." />
      ) : selectedExists ? (
        <ReviewPanel
          detail={detail}
          load={detailLoad}
          isOwnFinding={isOwnFinding}
          busy={busy}
          onApprove={() => decide('approve')}
          onReturn={() => setReturnOpen(true)}
        />
      ) : (
        <div style={{ flex: 1 }} />
      )}

      <ReturnDialog open={returnOpen} busy={busy} onConfirm={(comment) => decide('return', comment)} onCancel={() => setReturnOpen(false)} />
    </div>
  )
}
