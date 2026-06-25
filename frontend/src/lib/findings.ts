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
  size?: number
}

export function getFindings(filters: FindingFilters) {
  const p = new URLSearchParams()
  if (filters.status) p.set('status', filters.status)
  if (filters.severity) p.set('severity', filters.severity)
  if (filters.framework) p.set('framework', filters.framework)
  if (filters.q) p.set('q', filters.q)
  p.set('size', String(filters.size ?? 100))
  return api.get<PagedResponse<FindingSummary>>(`/findings?${p.toString()}`)
}
