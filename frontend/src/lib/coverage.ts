import { api } from './api'

/** Coverage API types + call (mirror the backend CoverageRow DTO). One row per control of a framework. */
export interface CoverageRow {
  control: { id: number; code: string; title: string }
  findingCount: number
  /** Worst severity among mapped findings, lowercase wire value — null when the control has none. */
  highestSeverity: string | null
  /** True when an active high/critical finding still maps to the control. */
  atRisk: boolean
}

export const getCoverage = (slug: string) =>
  api.get<CoverageRow[]>(`/coverage?framework=${encodeURIComponent(slug)}`)
