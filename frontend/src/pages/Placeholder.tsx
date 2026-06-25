/**
 * Neutral placeholder for screens not yet built — renders inside the AppShell so the chrome,
 * navigation, and account menu are reviewable now. Each is replaced by its real screen in a later
 * build increment. (Neutral by design: the coyote motif is reserved for empty/error states.)
 */
export function Placeholder({ title, note }: { title: string; note: string }) {
  return (
    <div>
      <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', margin: '0 0 var(--space-3)' }}>
        {title}
      </h1>
      <div
        className="bg-surface-card border border-subtle rounded-md shadow-xs"
        style={{ padding: 'var(--space-7)', maxWidth: 560 }}
      >
        <p className="cm-eyebrow" style={{ marginBottom: 'var(--space-2)' }}>
          Coming soon
        </p>
        <p className="text-body" style={{ margin: 0 }}>
          {note}
        </p>
      </div>
    </div>
  )
}
