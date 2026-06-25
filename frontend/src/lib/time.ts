/** Absolute date (YYYY-MM-DD) for detail/audit views. */
export function formatDate(iso: string): string {
  return new Date(iso).toISOString().slice(0, 10)
}

/** Relative time for dense views ("2h ago", "yesterday", "1 week ago"); ISO date when older. */
export function relativeTime(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime()
  const sec = Math.round(ms / 1000)
  if (sec < 60) return 'just now'
  const min = Math.round(sec / 60)
  if (min < 60) return `${min}m ago`
  const hr = Math.round(min / 60)
  if (hr < 24) return `${hr}h ago`
  const day = Math.round(hr / 24)
  if (day === 1) return 'yesterday'
  if (day < 7) return `${day} days ago`
  const wk = Math.round(day / 7)
  if (wk < 5) return `${wk} week${wk > 1 ? 's' : ''} ago`
  const mo = Math.round(day / 30)
  if (mo < 12) return `${mo} month${mo > 1 ? 's' : ''} ago`
  return new Date(iso).toISOString().slice(0, 10)
}
