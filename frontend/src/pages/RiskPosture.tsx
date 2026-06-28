import { Fragment, useEffect, useMemo, useState, type ReactNode } from 'react'
import { bandFor, getPosture, POSTURE_BANDS, type HeatRow, type PostureResponse, type SeverityCount, type StatusCount } from '../lib/posture'
import { Icon, type IconName } from '../components/Icon'
import { Button } from '../components/ui/Button'
import { RiskMeter } from '../components/data/RiskMeter'
import { StatCard } from '../components/data/StatCard'

const SEV_COLOR: Record<string, string> = {
  critical: 'var(--critical-500)',
  high: 'var(--high-500)',
  medium: 'var(--medium-500)',
  low: 'var(--low-500)',
}
const STATUS_COLOR: Record<string, string> = {
  open: 'var(--status-open)',
  'in-progress': 'var(--status-progress)',
  submitted: 'var(--status-submitted)',
  returned: 'var(--status-returned)',
  approved: 'var(--status-approved)',
  remediated: 'var(--status-remediated)',
  accepted: 'var(--status-accepted)',
}
const STATUS_SHORT: Record<string, string> = {
  open: 'Open',
  'in-progress': 'In prog.',
  submitted: 'Subm.',
  returned: 'Ret.',
  approved: 'Appr.',
  remediated: 'Remed.',
  accepted: 'Acc.',
}
// Heatmap cell tint = inherent risk (severity × workflow state), independent of count. Display-only.
const SEV_WEIGHT: Record<string, number> = { critical: 4, high: 3, medium: 2, low: 1 }
const STATUS_FACTOR: Record<string, number> = {
  open: 1, 'in-progress': 0.92, submitted: 0.85, returned: 0.9, approved: 0.45, remediated: 0.12, accepted: 0.28,
}

function ChartCard({ title, icon, hint, children, style }: { title: string; icon?: IconName; hint?: ReactNode; children: ReactNode; style?: React.CSSProperties }) {
  return (
    <section className="bg-surface-card border border-subtle rounded-md shadow-xs" style={{ overflow: 'hidden', display: 'flex', flexDirection: 'column', ...style }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '13px 18px', borderBottom: '1px solid var(--border-subtle)' }}>
        {icon ? <Icon name={icon} size={16} color="var(--text-muted)" /> : null}
        <h2 className="font-display text-strong" style={{ fontSize: 'var(--fs-h3)', fontWeight: 600, margin: 0, whiteSpace: 'nowrap' }}>{title}</h2>
        {hint ? <span className="text-faint" style={{ marginLeft: 'auto', fontSize: 'var(--fs-caption)' }}>{hint}</span> : null}
      </header>
      <div style={{ padding: 18, flex: 1 }}>{children}</div>
    </section>
  )
}

function PostureCard({ score, delta, hasHistory }: { score: number; delta: number; hasHistory: boolean }) {
  const band = bandFor(score)
  const improving = delta < 0
  return (
    <ChartCard title="Overall risk posture" icon="shield-alert">
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14 }}>
        <RiskMeter score={score} size={210} label="Program-wide" />
        {hasHistory ? (
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 'var(--fs-body-sm)', color: improving ? 'var(--positive-600)' : 'var(--critical-600)', fontWeight: 600 }}>
            <Icon name={improving ? 'trending-down' : 'trending-up'} size={15} />
            {Math.abs(delta)} pts {improving ? 'lower' : 'higher'} vs 90 days ago
          </div>
        ) : (
          <div className="text-faint" style={{ fontSize: 'var(--fs-caption)' }}>No prior history — trend begins recording now</div>
        )}
        <div style={{ display: 'flex', gap: 5, width: '100%', marginTop: 2 }}>
          {POSTURE_BANDS.map((b) => {
            const cur = b.label === band.label
            return (
              <div key={b.label} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 5 }}>
                <span style={{ width: '100%', height: 5, borderRadius: 'var(--radius-pill)', background: cur ? b.color : `color-mix(in srgb, ${b.color} 22%, var(--surface-inset))` }} />
                <span style={{ fontSize: 10, fontWeight: cur ? 700 : 500, color: cur ? b.color : 'var(--text-faint)' }}>{b.label}</span>
              </div>
            )
          })}
        </div>
      </div>
    </ChartCard>
  )
}

function SeverityBars({ data }: { data: SeverityCount[] }) {
  const max = Math.max(1, ...data.map((d) => d.count))
  const total = Math.max(1, data.reduce((s, d) => s + d.count, 0))
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {data.map((d) => (
        <div key={d.key} style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span className="text-strong" style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>
              <span style={{ width: 9, height: 9, borderRadius: 2, background: SEV_COLOR[d.key] }} />{d.label}
            </span>
            <span className="text-muted" style={{ fontSize: 'var(--fs-body-sm)' }}>
              <b className="text-strong font-mono" style={{ fontWeight: 600 }}>{d.count}</b>
              <span style={{ fontSize: 'var(--fs-caption)', marginLeft: 6 }}>{Math.round((d.count / total) * 100)}%</span>
            </span>
          </div>
          <span style={{ height: 10, borderRadius: 'var(--radius-xs)', background: 'var(--surface-inset)', overflow: 'hidden' }}>
            <span style={{ display: 'block', height: '100%', width: `${(d.count / max) * 100}%`, background: SEV_COLOR[d.key], borderRadius: 'var(--radius-xs)' }} />
          </span>
        </div>
      ))}
    </div>
  )
}

function StatusDonut({ data }: { data: StatusCount[] }) {
  const total = Math.max(1, data.reduce((s, d) => s + d.count, 0))
  const r = 52
  const stroke = 18
  const C = 2 * Math.PI * r
  let acc = 0
  const segs = data.map((d) => {
    const frac = d.count / total
    const seg = { ...d, dash: frac * C, offset: acc * C }
    acc += frac
    return seg
  })
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 22 }}>
      <svg width={148} height={148} viewBox="0 0 148 148" style={{ flex: 'none' }}>
        <g transform="rotate(-90 74 74)">
          {segs.map((s) => (
            <circle key={s.key} cx="74" cy="74" r={r} fill="none" stroke={STATUS_COLOR[s.key]} strokeWidth={stroke} strokeDasharray={`${s.dash} ${C - s.dash}`} strokeDashoffset={-s.offset} />
          ))}
        </g>
        <text x="74" y="69" textAnchor="middle" style={{ fontFamily: 'var(--font-display)', fontSize: 28, fontWeight: 600, fill: 'var(--text-strong)' }}>{data.reduce((s, d) => s + d.count, 0)}</text>
        <text x="74" y="86" textAnchor="middle" style={{ fontFamily: 'var(--font-body)', fontSize: 11, fill: 'var(--text-muted)' }}>findings</text>
      </svg>
      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 16px' }}>
        {data.map((d) => (
          <div key={d.key} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ width: 9, height: 9, borderRadius: '50%', background: STATUS_COLOR[d.key], flex: 'none' }} />
            <span className="text-body" style={{ fontSize: 'var(--fs-caption)', flex: 1, whiteSpace: 'nowrap' }}>{d.label}</span>
            <span className="text-strong font-mono" style={{ fontSize: 'var(--fs-caption)', fontWeight: 600 }}>{d.count}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function Heatmap({ statuses, rows }: { statuses: string[]; rows: HeatRow[] }) {
  return (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: `96px repeat(${statuses.length}, 1fr)`, gap: 4 }}>
        <span />
        {statuses.map((s) => (
          <span key={s} className="text-muted" style={{ fontSize: 10.5, fontWeight: 600, textAlign: 'center', letterSpacing: '0.01em', paddingBottom: 2 }}>{STATUS_SHORT[s]}</span>
        ))}
        {rows.map((row) => (
          <Fragment key={row.key}>
            <span className="text-strong" style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>
              <span style={{ width: 9, height: 9, borderRadius: 2, background: SEV_COLOR[row.key] }} />{row.label}
            </span>
            {row.cells.map((count, i) => {
              const intensity = (SEV_WEIGHT[row.key] * STATUS_FACTOR[statuses[i]]) / 4
              const pct = Math.round(intensity * 100)
              const hot = intensity > 0.5
              const empty = count === 0
              return (
                <div
                  key={i}
                  style={{
                    height: 42,
                    borderRadius: 'var(--radius-xs)',
                    background: empty ? 'var(--surface-inset)' : `color-mix(in srgb, var(--critical-500) ${pct}%, var(--surface-card))`,
                    border: `1px solid ${empty ? 'var(--border-subtle)' : `color-mix(in srgb, var(--critical-500) ${Math.min(100, pct + 14)}%, var(--border-subtle))`}`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontFamily: 'var(--font-data)',
                    fontSize: 14,
                    fontWeight: 600,
                    color: empty ? 'var(--text-faint)' : hot ? '#fff' : 'var(--text-strong)',
                  }}
                >
                  {empty ? '·' : count}
                </div>
              )
            })}
          </Fragment>
        ))}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 14 }}>
        <span className="text-muted" style={{ fontSize: 'var(--fs-caption)' }}>Cell colour = inherent risk (severity × workflow state)</span>
        <span className="text-faint" style={{ marginLeft: 'auto', fontSize: 10.5 }}>Lower</span>
        <span style={{ width: 120, height: 8, borderRadius: 'var(--radius-pill)', background: 'linear-gradient(90deg, color-mix(in srgb, var(--critical-500) 6%, var(--surface-card)), var(--critical-500))', border: '1px solid var(--border-subtle)' }} />
        <span className="text-faint" style={{ fontSize: 10.5 }}>Higher</span>
      </div>
    </div>
  )
}

interface TrendPoint {
  w: string
  score: number
}

function TrendChart({ data }: { data: TrendPoint[] }) {
  const W = 640
  const H = 188
  const padL = 34
  const padR = 14
  const padT = 16
  const padB = 26
  const scores = data.map((d) => d.score)
  const lo = Math.min(...scores) - 4
  const hi = Math.max(...scores) + 4
  const x = (i: number) => padL + (i / (data.length - 1)) * (W - padL - padR)
  const y = (v: number) => padT + (1 - (v - lo) / (hi - lo || 1)) * (H - padT - padB)
  const line = data.map((d, i) => `${i === 0 ? 'M' : 'L'} ${x(i).toFixed(1)} ${y(d.score).toFixed(1)}`).join(' ')
  const area = `${line} L ${x(data.length - 1).toFixed(1)} ${H - padB} L ${x(0).toFixed(1)} ${H - padB} Z`
  const gridVals = [lo, Math.round((lo + hi) / 2), hi]
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" style={{ display: 'block' }}>
      <defs>
        <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.16" />
          <stop offset="100%" stopColor="var(--primary)" stopOpacity="0" />
        </linearGradient>
      </defs>
      {gridVals.map((v, i) => (
        <g key={i}>
          <line x1={padL} y1={y(v)} x2={W - padR} y2={y(v)} stroke="var(--border-subtle)" strokeWidth="1" strokeDasharray={i === 0 ? '0' : '3 3'} />
          <text x={padL - 8} y={y(v) + 3} textAnchor="end" style={{ fontFamily: 'var(--font-data)', fontSize: 10, fill: 'var(--text-faint)' }}>{Math.round(v)}</text>
        </g>
      ))}
      <path d={area} fill="url(#trendFill)" />
      <path d={line} fill="none" stroke="var(--primary)" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />
      {data.map((d, i) => (
        <g key={i}>
          {i === data.length - 1 ? <circle cx={x(i)} cy={y(d.score)} r="4.5" fill="var(--primary)" stroke="var(--surface-card)" strokeWidth="2" /> : null}
          <text x={x(i)} y={H - 9} textAnchor="middle" style={{ fontFamily: 'var(--font-body)', fontSize: 10, fill: 'var(--text-faint)' }}>{i % 2 === 0 ? d.w : ''}</text>
        </g>
      ))}
    </svg>
  )
}

/** Deterministic illustrative trend ending exactly at the live score (demo only — never real history). */
function syntheticTrend(score: number): TrendPoint[] {
  const labels = ['12w', '10w', '8w', '6w', '4w', '2w', '1w', 'now']
  const start = Math.min(100, score + 18)
  const n = labels.length
  return labels.map((w, i) => {
    const t = i / (n - 1)
    const base = start + (score - start) * t
    const wobble = Math.sin(i * 1.7) * 2.5 * (1 - t)
    return { w, score: Math.round(Math.max(0, Math.min(100, base + wobble))) }
  })
}

function TrendCard({ score, demo }: { score: number; demo: boolean }) {
  const data = useMemo(() => syntheticTrend(score), [score])
  return (
    <ChartCard title="Risk score trend" icon="trending-down" hint="Lower is better">
      {demo ? (
        <div>
          <TrendChart data={data} />
        </div>
      ) : (
        <div style={{ height: 188, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 10, textAlign: 'center' }}>
          <Icon name="trending-down" size={30} color="var(--text-faint)" />
          <span className="text-strong" style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600 }}>History not yet recorded</span>
          <span className="text-muted" style={{ fontSize: 'var(--fs-caption)', maxWidth: 380 }}>
            Daily snapshots begin now; the 90-day trend fills in over time. Toggle “Illustrative demo” to preview the populated chart.
          </span>
        </div>
      )}
    </ChartCard>
  )
}

export function RiskPosture() {
  const [posture, setPosture] = useState<PostureResponse | null>(null)
  const [load, setLoad] = useState<'loading' | 'ready' | 'error'>('loading')
  const [demo, setDemo] = useState(false)

  function fetchPosture() {
    setLoad('loading')
    getPosture()
      .then((p) => {
        setPosture(p)
        setLoad('ready')
      })
      .catch(() => setLoad('error'))
  }

  useEffect(fetchPosture, [])

  if (load === 'loading') {
    return <div className="text-muted" style={{ padding: 'var(--space-9)', textAlign: 'center', fontSize: 'var(--fs-body-sm)' }}>Loading…</div>
  }
  if (load === 'error' || !posture) {
    return (
      <div role="alert" style={{ maxWidth: 900, display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', background: 'var(--critical-100)', border: '1px solid var(--critical-500)', borderRadius: 'var(--radius-md)', color: 'var(--critical-600)', fontSize: 'var(--fs-body-sm)' }}>
        <Icon name="alert-triangle" size={16} />
        <span style={{ flex: 1 }}>Couldn't load the risk posture.</span>
        <Button size="sm" variant="secondary" onClick={fetchPosture}>Retry</Button>
      </div>
    )
  }

  const p = posture
  const band = bandFor(p.score)
  const demoDelta = demo ? p.score - syntheticTrend(p.score)[0].score : 0
  const hasHistory = demo // real history (deltaPts != 0) would also count, once snapshots exist

  if (p.total === 0) {
    return (
      <div style={{ maxWidth: 1180 }}>
        <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', fontWeight: 600, margin: 0, letterSpacing: '-0.01em' }}>Risk posture</h1>
        <div style={{ marginTop: 24, padding: 48, textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }} className="bg-surface-card border border-subtle rounded-md">
          <Icon name="shield-check" size={32} color="var(--text-faint)" />
          <span className="font-display text-strong" style={{ fontSize: 'var(--fs-h2)', fontWeight: 600 }}>No findings yet</span>
          <span className="text-muted" style={{ fontSize: 'var(--fs-body-sm)' }}>Once findings are recorded, the program-wide risk posture appears here.</span>
        </div>
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 1180, display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', fontWeight: 600, margin: 0, letterSpacing: '-0.01em' }}>Risk posture</h1>
          <p className="text-muted" style={{ fontSize: 'var(--fs-body-sm)', margin: '4px 0 0' }}>Program-wide risk across {p.total} {p.total === 1 ? 'finding' : 'findings'}.</p>
        </div>
        <Button variant={demo ? 'primary' : 'secondary'} iconLeft="trending-down" onClick={() => setDemo((d) => !d)}>
          {demo ? 'Illustrative demo: on' : 'Illustrative demo: off'}
        </Button>
      </div>

      {demo ? (
        <div role="status" style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '9px 14px', background: 'var(--medium-100)', border: '1px solid var(--medium-500)', borderRadius: 'var(--radius-md)', color: 'var(--medium-700)', fontSize: 'var(--fs-caption)' }}>
          <Icon name="info" size={14} />
          <span>Illustrative mode — the 90-day delta and trend below are sample data, not real history. The gauge score and all counts remain real.</span>
        </div>
      ) : null}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14 }} className="cm-posture-stats">
        <StatCard label="Risk score" value={p.score} sub={band.label} delta={demo ? `${Math.abs(demoDelta)} pts` : undefined} deltaDir={demoDelta < 0 ? 'down' : 'up'} accent={band.color} icon="shield-alert" />
        <StatCard label="Active findings" value={p.active} sub="Open · in progress · in review" icon="alert-triangle" accent="var(--medium-500)" />
        <StatCard label="Critical · active" value={p.criticalActive} sub="Need immediate attention" accent="var(--critical-500)" icon="alert-octagon" />
        <StatCard label="Remediated (90d)" value={p.remediated90d} sub="Closed by fix" accent="var(--positive-600)" icon="shield-check" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '0.95fr 1.05fr', gap: 14, alignItems: 'stretch' }} className="cm-posture-row">
        <PostureCard score={p.score} delta={demoDelta} hasHistory={hasHistory} />
        <TrendCard score={p.score} demo={demo} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, alignItems: 'stretch' }} className="cm-posture-row">
        <ChartCard title="Findings by severity" icon="alert-octagon">
          <SeverityBars data={p.bySeverity} />
        </ChartCard>
        <ChartCard title="Findings by status" icon="clipboard-check">
          <StatusDonut data={p.byStatus} />
        </ChartCard>
      </div>

      <ChartCard title="Risk concentration — severity × status" icon="dashboard" hint="Count of findings per cell">
        <Heatmap statuses={p.heatStatuses} rows={p.heatRows} />
      </ChartCard>
    </div>
  )
}
