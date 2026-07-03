import { api } from './api'

export interface ControlRef {
  framework: string
  code: string
}

export interface FindingSummary {
  id: number
  reference: string
  title: string
  asset: string | null
  severity: string
  cvss: number | null
  /** Effective risk score 0–10 (CVSS when present, else severity-derived); never null. */
  riskScore: number
  /** Which strategy produced the score: 'cvss' or 'severity' (derived). */
  riskSource: string
  status: string
  controls: ControlRef[]
  owner: string
  updatedAt: string
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface FindingFilters {
  status?: string
  severity?: string
  framework?: string
  q?: string
  /** When true, list soft-deleted findings (the "Show deleted" view) instead of active ones. */
  deleted?: boolean
  /** When true, restrict to the signed-in user's own findings (#4 "Returned to me"). */
  mine?: boolean
  size?: number
}

export function getFindings(filters: FindingFilters) {
  const p = new URLSearchParams()
  if (filters.status) p.set('status', filters.status)
  if (filters.severity) p.set('severity', filters.severity)
  if (filters.framework) p.set('framework', filters.framework)
  if (filters.q) p.set('q', filters.q)
  if (filters.deleted) p.set('deleted', 'true')
  if (filters.mine) p.set('mine', 'true')
  p.set('size', String(filters.size ?? 100))
  return api.get<PagedResponse<FindingSummary>>(`/findings?${p.toString()}`)
}

export interface AssetDetail {
  name: string
  env: string | null
  component: string | null
  url: string | null
}

export interface MappedControl {
  controlId: number
  framework: string
  code: string
  title: string
}

export interface AuditEntry {
  actor: string
  action: string
  fromStatus: string | null
  toStatus: string | null
  comment: string | null
  timestamp: string
}

export interface FindingDetail {
  id: number
  reference: string
  title: string
  description: string | null
  severity: string
  cvss: number | null
  /** Effective risk score 0–10 (CVSS when present, else severity-derived); never null. */
  riskScore: number
  /** Which strategy produced the score: 'cvss' or 'severity' (derived). */
  riskSource: string
  status: string
  asset: AssetDetail | null
  owner: string
  createdAt: string
  updatedAt: string
  deleted: boolean
  controls: MappedControl[]
  audit: AuditEntry[]
}

export interface FindingRequest {
  title: string
  description?: string | null
  severity?: string | null
  cvss?: number | null
  asset: { name: string; env?: string | null; component?: string | null; url?: string | null }
}

export const getFinding = (id: string | number) => api.get<FindingDetail>(`/findings/${id}`)
export const createFinding = (body: FindingRequest) => api.post<FindingDetail>('/findings', body)
export const updateFinding = (id: string | number, body: FindingRequest) => api.put<FindingDetail>(`/findings/${id}`, body)
export const deleteFinding = (id: string | number) => api.del<void>(`/findings/${id}`)

export const addControlMapping = (findingId: string | number, controlId: number) =>
  api.post<FindingDetail>(`/findings/${findingId}/controls`, { controlId })
export const removeControlMapping = (findingId: string | number, controlId: number) =>
  api.del<FindingDetail>(`/findings/${findingId}/controls/${controlId}`)

/** A control the model suggests for a finding (id/framework/code/title mirror a catalog control). */
export interface SuggestedControl {
  id: number
  framework: string
  code: string
  title: string
  description: string | null
  category: string | null
}

/** One AI control suggestion (stretch, PLAN §4/§7.12): a grounded control + confidence + one-line rationale. */
export interface ControlSuggestion {
  control: SuggestedControl
  confidence: number
  rationale: string
}

/** Request AI control suggestions for a finding. 503 when disabled/failed, 429 when rate-limited. */
export const suggestControls = (findingId: string | number) =>
  api.post<ControlSuggestion[]>(`/findings/${findingId}/suggest-controls`)

/** Accept an AI suggestion — creates the mapping with server-stamped provenance. Returns the updated finding. */
export const acceptSuggestion = (findingId: string | number, controlId: number) =>
  api.post<FindingDetail>(`/findings/${findingId}/accept-suggestion`, { controlId })

/** Perform a role-gated workflow transition (PLAN §8). Returns the updated finding. */
export const transitionFinding = (findingId: string | number, action: string, comment?: string) =>
  api.post<FindingDetail>(`/findings/${findingId}/transition`, { action, comment })

/** Reviewer-only sign-off queue (#17): SUBMITTED findings awaiting a decision, oldest first. */
export const getReviewQueue = () => api.get<FindingSummary[]>('/reviews/queue')
