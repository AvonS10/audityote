import { useNavigate, useParams } from 'react-router-dom'
import { Button } from '../components/ui/Button'

/**
 * Temporary stand-in for the finding detail screen (build increment #12). Gives the Edit form a
 * reachable entry point now; replaced by the real detail (mappings, workflow, activity) in #12.
 */
export function FindingDetailStub() {
  const { id } = useParams()
  const navigate = useNavigate()
  return (
    <div style={{ maxWidth: 760 }}>
      <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', fontWeight: 600, margin: '0 0 var(--space-3)' }}>
        Finding detail
      </h1>
      <div className="bg-surface-card border border-subtle rounded-md shadow-xs" style={{ padding: 'var(--space-7)' }}>
        <p className="cm-eyebrow" style={{ marginBottom: 'var(--space-2)' }}>Coming soon</p>
        <p className="text-body" style={{ margin: '0 0 var(--space-6)' }}>
          The full finding detail screen — mapped controls, workflow tracker, and activity trail — arrives in build increment #12.
        </p>
        <div style={{ display: 'flex', gap: 10 }}>
          <Button variant="secondary" iconLeft="file-text" onClick={() => navigate(`/findings/${id}/edit`)}>
            Edit finding
          </Button>
          <Button variant="ghost" onClick={() => navigate('/')}>
            Back to dashboard
          </Button>
        </div>
      </div>
    </div>
  )
}
