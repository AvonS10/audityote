import { Icon, type IconName } from '../Icon'

/**
 * WorkflowTracker — the Finding lifecycle spine (open → in-progress → submitted → approved), the
 * Returned-for-changes loop, and the two terminal outcomes (Remediated / Accepted). Ported from the
 * design system's Finding Detail screen; purely presentational, driven by the current status.
 */

const SPINE = [
  { key: 'open', label: 'Open' },
  { key: 'in-progress', label: 'In Progress' },
  { key: 'submitted', label: 'Submitted', note: 'review' },
  { key: 'approved', label: 'Approved', note: 'decision' },
] as const

const SPINE_POS: Record<string, number> = { open: 0, 'in-progress': 1, submitted: 2, approved: 3, returned: 1, remediated: -1, accepted: -1 }
const REACHED: Record<string, number> = { open: 0, 'in-progress': 1, submitted: 2, approved: 3, returned: 2, remediated: 4, accepted: 4 }

type NodeState = 'done' | 'current' | 'future' | 'returned'

function SpineNode({ state, label, note, last }: { state: NodeState; label: string; note?: string; last?: boolean }) {
  const ring = { done: 'var(--primary)', current: 'var(--primary)', future: 'var(--border-default)', returned: 'var(--status-returned)' }[state]
  const fill = state === 'done' ? 'var(--primary)' : state === 'current' ? 'var(--primary-soft)' : state === 'returned' ? 'var(--status-returned-bg)' : 'var(--surface-inset)'
  const lineColor = state === 'done' ? 'var(--primary)' : 'var(--border-subtle)'
  const emphasized = state === 'current' || state === 'returned'
  return (
    <div style={{ display: 'flex', gap: 11 }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flex: 'none' }}>
        <span style={{ width: 18, height: 18, borderRadius: '50%', boxSizing: 'border-box', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', background: fill, border: `${emphasized ? '2px' : '1px'} solid ${ring}` }}>
          {state === 'done' ? <Icon name="check" size={11} color="#fff" />
            : state === 'current' ? <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--primary)' }} />
            : state === 'returned' ? <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--status-returned)' }} /> : null}
        </span>
        {!last ? <span style={{ flex: 1, width: 2, background: lineColor, marginTop: 2, minHeight: 16 }} /> : null}
      </div>
      <div style={{ paddingBottom: last ? 0 : 12, marginTop: -1, display: 'flex', alignItems: 'baseline', gap: 7 }}>
        <span style={{ fontSize: 'var(--fs-body-sm)', fontWeight: emphasized ? 600 : 400, color: state === 'future' ? 'var(--text-faint)' : 'var(--text-strong)' }}>{label}</span>
        {note ? <span style={{ fontSize: 10.5, letterSpacing: '0.03em', textTransform: 'uppercase', color: 'var(--text-faint)', fontWeight: 600 }}>{note}</span> : null}
      </div>
    </div>
  )
}

type TerminalState = 'current' | 'alt' | 'future'

function Terminal({ state, label, icon, tone, desc }: { state: TerminalState; label: string; icon: IconName; tone: 'positive' | 'accepted'; desc: string }) {
  const active = state === 'current'
  const color = tone === 'accepted' ? 'var(--status-accepted)' : 'var(--positive-600)'
  const bg = tone === 'accepted' ? 'var(--status-accepted-bg)' : 'var(--positive-100)'
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '7px 10px', borderRadius: 'var(--radius-sm)',
      background: active ? bg : 'transparent',
      border: `1px solid ${active ? `color-mix(in srgb, ${color} 32%, transparent)` : 'var(--border-subtle)'}`,
      opacity: state === 'alt' ? 0.5 : 1 }}>
      <span style={{ width: 18, height: 18, borderRadius: '50%', flex: 'none', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        background: active ? color : 'var(--surface-inset)', border: active ? '0' : '1px solid var(--border-default)' }}>
        <Icon name={icon} size={11} color={active ? '#fff' : 'var(--text-faint)'} />
      </span>
      <span style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.25 }}>
        <span style={{ fontSize: 'var(--fs-body-sm)', fontWeight: active ? 600 : 500, color: active ? 'var(--text-strong)' : 'var(--text-muted)' }}>{label}</span>
        <span style={{ fontSize: 10.5, color: active ? color : 'var(--text-faint)' }}>{desc}</span>
      </span>
      {active ? <span style={{ marginLeft: 'auto', fontSize: 9.5, fontWeight: 700, letterSpacing: '0.05em', textTransform: 'uppercase', color }}>Current</span> : null}
    </div>
  )
}

export function WorkflowTracker({ status }: { status: string }) {
  const pos = SPINE_POS[status] ?? 0
  const reached = REACHED[status] ?? 0
  const returned = status === 'returned'
  const terminal = status === 'remediated' ? 'remediated' : status === 'accepted' ? 'accepted' : null
  const nodeState = (i: number): NodeState => {
    if (returned && i === 2) return 'returned'
    if (terminal) return 'done'
    if (i === pos) return 'current'
    if (i < reached) return 'done'
    return 'future'
  }
  const remState: TerminalState = terminal === 'remediated' ? 'current' : terminal === 'accepted' ? 'alt' : 'future'
  const accState: TerminalState = terminal === 'accepted' ? 'current' : terminal === 'remediated' ? 'alt' : 'future'

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {SPINE.map((s, i) => <SpineNode key={s.key} state={nodeState(i)} label={s.label} note={'note' in s ? s.note : undefined} />)}

      {returned ? (
        <div style={{ display: 'flex', gap: 11, marginTop: -6, marginBottom: 10 }}>
          <span style={{ width: 18, flex: 'none', display: 'flex', justifyContent: 'center' }}>
            <span style={{ width: 2, height: 18, background: 'var(--status-returned)', borderRadius: 2 }} />
          </span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '4px 9px', borderRadius: 'var(--radius-pill)', background: 'var(--status-returned-bg)', border: '1px solid color-mix(in srgb, var(--status-returned) 32%, transparent)', color: 'var(--status-returned)', fontSize: 11, fontWeight: 600 }}>
            <Icon name="arrow-up-right" size={11} style={{ transform: 'rotate(135deg)' }} />
            Returned for changes → In Progress
          </span>
        </div>
      ) : null}

      <div style={{ display: 'flex', gap: 11 }}>
        <span style={{ width: 18, flex: 'none', display: 'flex', justifyContent: 'center' }}>
          <span style={{ width: 2, height: 10, background: 'var(--border-subtle)', borderRadius: 2 }} />
        </span>
        <span style={{ fontSize: 10, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--text-faint)', fontWeight: 600 }}>Ends in one of</span>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 7, paddingLeft: 29, paddingTop: 6 }}>
        <Terminal state={remState} label="Remediated" icon="shield-check" tone="positive" desc="Fixed & verified" />
        <Terminal state={accState} label="Accepted" icon="check" tone="accepted" desc="Risk formally accepted" />
      </div>
    </div>
  )
}
