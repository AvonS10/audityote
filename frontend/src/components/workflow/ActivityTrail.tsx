import { Icon, type IconName } from '../Icon'
import { relativeTime } from '../../lib/time'
import type { AuditEntry } from '../../lib/findings'

/**
 * ActivityTrail — the finding's audit history as a vertical timeline (ported from the design system's
 * Finding Detail screen). Fed by the FindingDetail.audit[] the Observer writes (#16): who did what,
 * when, with any workflow comment rendered as a quote. Renders inside a SectionCard on the detail page.
 */

const PHRASE: Record<string, string> = {
  created: 'created this finding',
  submit: 'submitted for review',
  resubmit: 'resubmitted for review',
  approve: 'approved this finding',
  return: 'returned for changes',
  remediate: 'marked remediated',
  accept: 'accepted the risk',
  reopen: 'reopened this finding',
}

const ICON: Record<string, IconName> = {
  created: 'plus',
  submit: 'arrow-up-right',
  resubmit: 'arrow-up-right',
  approve: 'check',
  return: 'arrow-up-right',
  remediate: 'shield-check',
  accept: 'check',
  reopen: 'arrow-up-right',
}

const TINT: Record<string, string> = {
  created: 'var(--primary)',
  approve: 'var(--positive-600)',
  remediate: 'var(--positive-600)',
  accept: 'var(--positive-600)',
  return: 'var(--status-returned)',
  submit: 'var(--status-submitted)',
  resubmit: 'var(--status-submitted)',
  reopen: 'var(--status-submitted)',
}

export function ActivityTrail({ audit }: { audit: AuditEntry[] }) {
  if (audit.length === 0) {
    return (
      <div className="text-muted" style={{ padding: 20, textAlign: 'center', fontSize: 'var(--fs-body-sm)', border: '1px dashed var(--border-default)', borderRadius: 'var(--radius-sm)' }}>
        No activity recorded yet.
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {audit.map((a, i) => {
        const last = i === audit.length - 1
        const tint = TINT[a.action] ?? 'var(--text-muted)'
        return (
          <div key={i} style={{ display: 'flex', gap: 12, paddingBottom: last ? 0 : 18 }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flex: 'none' }}>
              <span style={{ width: 26, height: 26, borderRadius: '50%', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', background: 'var(--surface-inset)', border: '1px solid var(--border-subtle)', color: tint }}>
                <Icon name={ICON[a.action] ?? 'arrow-up-right'} size={13} color={tint} />
              </span>
              {!last ? <span style={{ flex: 1, width: 1, background: 'var(--border-subtle)', marginTop: 2 }} /> : null}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2, paddingTop: 2, minWidth: 0 }}>
              <span style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-body)' }}>
                <b className="text-strong" style={{ fontWeight: 600 }}>{a.actor}</b> {PHRASE[a.action] ?? a.action}
                {a.fromStatus && a.toStatus ? (
                  <span className="font-mono" style={{ marginLeft: 7, fontSize: 'var(--fs-caption)', color: 'var(--text-muted)' }}>
                    {a.fromStatus} → {a.toStatus}
                  </span>
                ) : null}
              </span>
              {a.comment ? (
                <span style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-muted)', fontStyle: 'italic' }}>“{a.comment}”</span>
              ) : null}
              <span className="font-mono text-faint" style={{ fontSize: 11 }} title={a.timestamp}>{relativeTime(a.timestamp)}</span>
            </div>
          </div>
        )
      })}
    </div>
  )
}
