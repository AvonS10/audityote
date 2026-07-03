import { ApiError, type ApiErrorBody } from './api'

/**
 * Report download helpers. Reports stream as file attachments, so they bypass the JSON `api` client:
 * we fetch same-origin (session cookie via `credentials: 'include'`), then save the blob through a
 * temporary object URL. GET needs no CSRF token. Non-2xx throws {@link ApiError} like the rest.
 */
const BASE = '/api'

export type ReportFormat = 'csv' | 'pdf'

export const findingsReportPath = (format: ReportFormat = 'csv') => `/reports/findings?format=${format}`

export const coverageReportPath = (framework: string, format: ReportFormat = 'csv') =>
  `/reports/coverage?framework=${encodeURIComponent(framework)}&format=${format}`

export const auditReportPath = (format: ReportFormat = 'csv') => `/reports/audit?format=${format}`

/** The admin user-management trail — the endpoint is ADMIN-only (403 otherwise). */
export const userAuditReportPath = (format: ReportFormat = 'csv') => `/reports/user-audit?format=${format}`

/** The comprehensive posture/auditor report — PDF is the primary artifact, CSV the raw-numbers companion. */
export const postureReportPath = (format: ReportFormat = 'pdf') => `/reports/posture?format=${format}`

export async function downloadReport(path: string): Promise<void> {
  const res = await fetch(`${BASE}${path}`, { credentials: 'include' })
  if (!res.ok) {
    let body: ApiErrorBody | null = null
    try {
      body = (await res.json()) as ApiErrorBody
    } catch {
      // non-JSON error body — leave null
    }
    throw new ApiError(res.status, body)
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filenameFromDisposition(res.headers.get('Content-Disposition')) ?? 'report.csv'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

/** Pulls `filename="…"` out of a Content-Disposition header, if present. */
function filenameFromDisposition(header: string | null): string | null {
  if (!header) return null
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header)
  return match ? decodeURIComponent(match[1]) : null
}
