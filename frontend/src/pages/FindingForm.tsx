import { useEffect, useState, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../lib/api'
import { createFinding, deleteFinding, getFinding, updateFinding, type FindingRequest } from '../lib/findings'
import { useAuth } from '../auth/AuthContext'
import { Icon } from '../components/Icon'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { Textarea } from '../components/ui/Textarea'
import { SeverityBadge } from '../components/data/SeverityBadge'
import { StatusBadge } from '../components/data/StatusBadge'
import { CvssScore } from '../components/data/CvssScore'
import { ConfirmDialog } from '../components/feedback/ConfirmDialog'

const SEVERITIES = ['critical', 'high', 'medium', 'low']

function bandFromCvss(v: number): string {
  if (v >= 9) return 'critical'
  if (v >= 7) return 'high'
  if (v >= 4) return 'medium'
  return 'low'
}

function Field({ label, required, optional, hint, error, children }: { label: string; required?: boolean; optional?: boolean; hint?: string; error?: string | null; children: ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <label style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600, color: 'var(--text-strong)', display: 'flex', alignItems: 'center' }}>
        {label}
        {required ? <span style={{ color: 'var(--critical-600)', marginLeft: 3 }}>*</span> : null}
        {optional ? <span style={{ color: 'var(--text-faint)', fontWeight: 400, marginLeft: 7, fontSize: 'var(--fs-caption)' }}>Optional</span> : null}
      </label>
      {children}
      {error ? (
        <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--critical-600)', display: 'flex', alignItems: 'center', gap: 5 }}>
          <Icon name="alert-triangle" size={12} />
          {error}
        </span>
      ) : hint ? (
        <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-muted)' }}>{hint}</span>
      ) : null}
    </div>
  )
}

function SeverityPicker({ value, onChange, disabled }: { value: string; onChange: (v: string) => void; disabled?: boolean }) {
  return (
    <div style={{ display: 'flex', gap: 8 }}>
      {SEVERITIES.map((sev) => {
        const selected = value === sev
        return (
          <button
            key={sev}
            type="button"
            disabled={disabled}
            onClick={() => onChange(sev)}
            style={{
              flex: 1,
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'center',
              height: 40,
              cursor: disabled ? 'not-allowed' : 'pointer',
              background: selected ? 'var(--primary-soft)' : 'var(--surface-card)',
              border: `1px solid ${selected ? 'var(--primary)' : 'var(--border-default)'}`,
              boxShadow: selected ? 'var(--focus-ring)' : 'none',
              borderRadius: 'var(--radius-sm)',
              opacity: disabled && !selected ? 0.5 : 1,
              transition: 'border-color 120ms ease',
            }}
          >
            <SeverityBadge level={sev} />
          </button>
        )
      })}
    </div>
  )
}

export function FindingForm() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { user } = useAuth()

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [severity, setSeverity] = useState('high')
  const [cvss, setCvss] = useState('')
  const [assetName, setAssetName] = useState('')
  const [status, setStatus] = useState('open')
  const [ownerName, setOwnerName] = useState('')

  const [load, setLoad] = useState<'loading' | 'ready' | 'error'>(editing ? 'loading' : 'ready')
  const [touched, setTouched] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    if (!editing) {
      setOwnerName(user?.name ?? '')
      return
    }
    getFinding(id)
      .then((f) => {
        setTitle(f.title)
        setDescription(f.description ?? '')
        setSeverity(f.severity)
        setCvss(f.cvss != null ? String(f.cvss) : '')
        setAssetName(f.asset?.name ?? '')
        setStatus(f.status)
        setOwnerName(f.owner)
        setLoad('ready')
      })
      .catch(() => setLoad('error'))
  }, [editing, id, user])

  const cvssNum = cvss.trim() === '' ? null : Number(cvss)
  const cvssValid = cvssNum !== null && !Number.isNaN(cvssNum) && cvssNum >= 0 && cvssNum <= 10
  const effectiveSeverity = cvssValid ? bandFromCvss(cvssNum) : severity

  const errors = {
    title: !title.trim() ? 'Title is required.' : null,
    cvss: cvss.trim() !== '' && (cvssNum === null || Number.isNaN(cvssNum) || cvssNum < 0 || cvssNum > 10) ? 'Enter a score between 0.0 and 10.0.' : null,
    severity: !cvssValid && !severity ? 'Select a severity, or enter a CVSS score.' : null,
    asset: !assetName.trim() ? 'Affected asset is required.' : null,
  }
  const hasErrors = Object.values(errors).some(Boolean)
  const showErr = (k: keyof typeof errors) => (touched ? errors[k] : null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setTouched(true)
    setServerError(null)
    if (hasErrors) return
    setSubmitting(true)
    const body: FindingRequest = {
      title: title.trim(),
      description: description.trim() || null,
      severity: cvssValid ? null : severity,
      cvss: cvssValid ? cvssNum : null,
      asset: { name: assetName.trim() },
    }
    try {
      if (editing) {
        await updateFinding(id, body)
        navigate(`/findings/${id}`)
      } else {
        await createFinding(body)
        navigate('/')
      }
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
      setSubmitting(false)
    }
  }

  async function confirmDelete() {
    if (!editing) return
    setDeleting(true)
    setServerError(null)
    try {
      await deleteFinding(id)
      navigate('/')
    } catch (err) {
      setServerError(err instanceof ApiError ? err.message : 'Could not delete the finding.')
      setDeleting(false)
      setConfirmOpen(false)
    }
  }

  if (load === 'error') {
    return (
      <div style={{ maxWidth: 760, margin: '0 auto' }}>
        <div role="alert" style={{ padding: '14px 16px', background: 'var(--critical-100)', border: '1px solid var(--critical-500)', borderRadius: 'var(--radius-md)', color: 'var(--critical-600)', fontSize: 'var(--fs-body-sm)' }}>
          Couldn't load this finding.
        </div>
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 760, margin: '0 auto', width: '100%' }}>
      <div style={{ marginBottom: 18 }}>
        <h1 className="font-display text-strong" style={{ fontSize: 'var(--fs-h1)', fontWeight: 600, margin: 0, letterSpacing: '-0.01em' }}>
          {editing ? 'Edit finding' : 'New finding'}
        </h1>
        <p className="text-muted" style={{ fontSize: 'var(--fs-body-sm)', margin: '4px 0 0' }}>
          {editing ? 'Update the finding details.' : 'Log a security finding and route it through review.'}
        </p>
      </div>

      <form onSubmit={submit} className="bg-surface-card border border-subtle rounded-md shadow-sm" style={{ overflow: 'hidden' }}>
        <div style={{ padding: '22px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
          {serverError ? (
            <div role="alert" style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '10px 12px', background: 'var(--critical-100)', border: '1px solid var(--critical-500)', borderRadius: 'var(--radius-sm)', fontSize: 'var(--fs-body-sm)', color: 'var(--critical-600)' }}>
              <Icon name="alert-triangle" size={15} />
              {serverError}
            </div>
          ) : null}

          <Field label="Title" required error={showErr('title')} hint="A specific, vulnerability-first summary.">
            <Input placeholder="e.g. Stored XSS in customer statements export" value={title} invalid={!!showErr('title')} onChange={(e) => setTitle(e.target.value)} />
          </Field>

          <Field label="Description" hint="What is the issue, where, and what is the impact?">
            <Textarea rows={5} placeholder="Describe the finding, affected flow, and impact…" value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>

          <Field label="Severity" required error={showErr('severity')} hint={cvssValid ? 'Derived from the CVSS score.' : undefined}>
            <SeverityPicker value={effectiveSeverity} onChange={setSeverity} disabled={cvssValid} />
          </Field>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 18 }}>
            <Field label="CVSS score" optional error={showErr('cvss')} hint="Base score, 0.0–10.0.">
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <Input type="text" inputMode="decimal" placeholder="0.0" value={cvss} invalid={!!showErr('cvss')} onChange={(e) => setCvss(e.target.value)} style={{ maxWidth: 110 }} />
                {cvssValid ? <CvssScore score={cvssNum} /> : null}
              </div>
            </Field>
            <Field label="Affected asset" required error={showErr('asset')} hint="System or component impacted.">
              <Input placeholder="e.g. statements-portal" value={assetName} invalid={!!showErr('asset')} onChange={(e) => setAssetName(e.target.value)} />
            </Field>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 18 }}>
            <Field label="Owner" hint="Findings are owned by their author.">
              <div style={{ display: 'flex', alignItems: 'center', height: 'var(--control-h-md)', padding: '0 12px', background: 'var(--surface-inset)', border: '1px solid var(--border-default)', borderRadius: 'var(--radius-sm)', fontSize: 'var(--fs-body-sm)', color: 'var(--text-body)' }}>
                {ownerName}
              </div>
            </Field>
            <Field label="Status" hint={editing ? 'Advances only through workflow actions (separation of duties).' : 'New findings always start as Open.'}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, height: 'var(--control-h-md)', padding: '0 12px', background: 'var(--surface-inset)', border: '1px solid var(--border-default)', borderRadius: 'var(--radius-sm)' }}>
                <StatusBadge status={editing ? status : 'open'} />
                <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 'var(--fs-caption)', color: 'var(--text-faint)' }}>
                  <Icon name="shield-check" size={13} />
                  Read-only
                </span>
              </div>
            </Field>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '14px 24px', background: 'var(--surface-inset)', borderTop: '1px solid var(--border-subtle)' }}>
          {editing ? (
            <Button type="button" variant="danger" iconLeft="trash" onClick={() => setConfirmOpen(true)}>
              Delete
            </Button>
          ) : null}
          {touched && hasErrors ? (
            <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--critical-600)', display: 'flex', alignItems: 'center', gap: 5 }}>
              <Icon name="alert-triangle" size={13} />
              Resolve the highlighted fields.
            </span>
          ) : null}
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 10 }}>
            <Button type="button" variant="ghost" onClick={() => navigate(editing ? `/findings/${id}` : '/')}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" iconLeft="check" disabled={submitting}>
              {submitting ? 'Saving…' : editing ? 'Save changes' : 'Save finding'}
            </Button>
          </div>
        </div>
      </form>

      <ConfirmDialog
        open={confirmOpen}
        title="Delete finding?"
        body="This removes the finding from the active list. It is kept read-only for audit — viewable any time under “Show deleted” — and its control mappings and full history are preserved."
        confirmLabel="Delete finding"
        icon="trash"
        busy={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  )
}
