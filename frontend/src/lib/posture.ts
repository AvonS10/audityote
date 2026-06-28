import { api } from './api'

export interface PostureBand {
  max: number
  label: string
  color: string
}

/** Posture gauge bands (PLAN §9) — score < max picks the band. Shared by the gauge + band scale. */
export const POSTURE_BANDS: PostureBand[] = [
  { max: 25, label: 'Low', color: 'var(--positive-600)' },
  { max: 50, label: 'Guarded', color: 'var(--low-600)' },
  { max: 70, label: 'Elevated', color: 'var(--medium-600)' },
  { max: 85, label: 'High', color: 'var(--high-600)' },
  { max: 101, label: 'Severe', color: 'var(--critical-600)' },
]

export function bandFor(score: number): PostureBand {
  return POSTURE_BANDS.find((b) => score < b.max) ?? POSTURE_BANDS[POSTURE_BANDS.length - 1]
}

export interface SeverityCount {
  key: string
  label: string
  count: number
}

export interface StatusCount {
  key: string
  label: string
  count: number
}

export interface HeatRow {
  key: string
  label: string
  cells: number[]
}

/** Program-wide risk posture rollup (PLAN §9) — backs the Risk Posture screen. */
export interface PostureResponse {
  score: number
  /** Change vs 90 days ago; 0 until daily snapshots exist (stretch). */
  deltaPts: number
  total: number
  active: number
  criticalActive: number
  remediated90d: number
  bySeverity: SeverityCount[]
  byStatus: StatusCount[]
  heatStatuses: string[]
  heatRows: HeatRow[]
}

export const getPosture = () => api.get<PostureResponse>('/posture')
