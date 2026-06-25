/**
 * Placeholder for sub-step 8a — proves the ControlMap design tokens resolve through both
 * mechanisms: Tailwind token utilities (bg-surface-card, text-strong, bg-primary, font-display, …)
 * and raw CSS variables. Replaced by the real Login / AppShell in sub-steps 8b–8c.
 */
function App() {
  return (
    <div
      className="cm-root"
      style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 'var(--space-9)' }}
    >
      <div className="bg-surface-card border border-subtle rounded-md shadow-sm" style={{ maxWidth: 520, padding: 'var(--space-7)' }}>
        <p className="cm-eyebrow">Design tokens</p>
        <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', margin: 'var(--space-2) 0 var(--space-3)' }}>
          Control<span style={{ color: 'var(--primary)' }}>Map</span>
        </h1>
        <p className="text-body" style={{ marginBottom: 'var(--space-6)' }}>
          Frontend scaffold with the Sovereign theme wired in — Spectral display, Libre Franklin
          body, IBM Plex Mono for data.
        </p>
        <div style={{ display: 'flex', gap: 'var(--space-4)', alignItems: 'center' }}>
          <button
            type="button"
            className="bg-primary text-on-primary rounded-sm font-body"
            style={{ height: 'var(--control-h-md)', padding: '0 var(--space-5)', fontWeight: 600, border: 'none', cursor: 'pointer' }}
          >
            Primary action
          </button>
          <span className="font-mono text-strong">CM-2026-0001 · CVSS 9.8</span>
        </div>
      </div>
    </div>
  )
}

export default App
