import { useEffect, useRef, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { Avatar } from '../Avatar'
import { CoyoteBadge, Habitat } from '../Coyote'
import { Icon, type IconName } from '../Icon'
import { RoleChip } from './RoleChip'

interface NavEntry {
  to: string
  icon: IconName
  label: string
  end?: boolean
  reviewerOnly?: boolean
}

const NAV: NavEntry[] = [
  { to: '/', icon: 'dashboard', label: 'Dashboard', end: true },
  { to: '/catalog', icon: 'list-checks', label: 'Control Catalog' },
  { to: '/coverage', icon: 'map', label: 'Control Coverage' },
  { to: '/posture', icon: 'trending-up', label: 'Risk Posture' },
  { to: '/reviews', icon: 'clipboard-check', label: 'Review Queue', reviewerOnly: true },
]

function isActive(pathname: string, entry: NavEntry): boolean {
  return entry.end ? pathname === entry.to : pathname === entry.to || pathname.startsWith(`${entry.to}/`)
}

function Brand() {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 9 }}>
      <CoyoteBadge size={28} radius={7} />
      <span
        className="cm-brand-word"
        style={{ fontFamily: 'var(--font-display)', fontSize: 16, fontWeight: 700, letterSpacing: '-0.01em', color: 'var(--text-on-dark)' }}
      >
        Audit<span style={{ opacity: 0.62 }}>Yote</span>
      </span>
    </span>
  )
}

function NavItem({ entry, active }: { entry: NavEntry; active: boolean }) {
  const [hover, setHover] = useState(false)
  return (
    <Link
      to={entry.to}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        height: 36,
        padding: '0 10px',
        borderRadius: 'var(--radius-sm)',
        textDecoration: 'none',
        color: active ? '#fff' : 'color-mix(in srgb, var(--text-on-dark) 78%, transparent)',
        background: active
          ? 'color-mix(in srgb, #fff 13%, transparent)'
          : hover
            ? 'color-mix(in srgb, #fff 7%, transparent)'
            : 'transparent',
        fontSize: 'var(--fs-body-sm)',
        fontWeight: active ? 600 : 500,
        transition: 'background 120ms ease, color 120ms ease',
      }}
    >
      <Icon name={entry.icon} size={17} />
      <span className="cm-nav-label" style={{ flex: 1 }}>
        {entry.label}
      </span>
    </Link>
  )
}

function Sidebar() {
  const { user } = useAuth()
  const location = useLocation()
  const isReviewer = user?.role.toUpperCase() === 'REVIEWER'
  const items = NAV.filter((n) => !n.reviewerOnly || isReviewer)

  return (
    <aside
      className="cm-sidebar"
      style={{
        width: 'var(--sidebar-w)',
        flex: 'none',
        height: '100%',
        background: 'var(--surface-header)',
        display: 'flex',
        flexDirection: 'column',
        padding: '14px 12px',
        gap: 4,
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0, opacity: 0.6, pointerEvents: 'none', zIndex: 0 }} aria-hidden="true">
        <Habitat height={132} onDark opacity={0.9} />
      </div>
      <div style={{ padding: '4px 6px 14px', position: 'relative', zIndex: 1 }}>
        <Brand />
      </div>
      <nav style={{ display: 'flex', flexDirection: 'column', gap: 2, position: 'relative', zIndex: 1 }}>
        {items.map((entry) => (
          <NavItem key={entry.to} entry={entry} active={isActive(location.pathname, entry)} />
        ))}
      </nav>
      <div style={{ marginTop: 'auto', position: 'relative', zIndex: 1 }}>
        <div
          className="cm-user-block"
          style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '10px 8px', borderTop: '1px solid color-mix(in srgb, #fff 12%, transparent)' }}
        >
          <Avatar name={user?.name ?? ''} size={28} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 3, minWidth: 0 }}>
            <span style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--text-on-dark)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', lineHeight: 1.2 }}>
              {user?.name}
            </span>
            <RoleChip role={user?.role ?? ''} onDark />
          </div>
        </div>
      </div>
    </aside>
  )
}

function AccountMenuItem({ icon, label, danger, onClick }: { icon: IconName; label: string; danger?: boolean; onClick: () => void }) {
  const [hover, setHover] = useState(false)
  return (
    <button
      type="button"
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 9,
        width: '100%',
        height: 34,
        padding: '0 9px',
        border: 'none',
        borderRadius: 'var(--radius-sm)',
        background: hover ? (danger ? 'var(--critical-100)' : 'var(--surface-inset)') : 'transparent',
        cursor: 'pointer',
        textAlign: 'left',
        fontFamily: 'var(--font-body)',
        fontSize: 'var(--fs-body-sm)',
        fontWeight: 500,
        color: danger ? 'var(--critical-600)' : 'var(--text-body)',
      }}
    >
      <Icon name={icon} size={16} color={danger ? 'var(--critical-600)' : 'var(--text-muted)'} />
      {label}
    </button>
  )
}

function AccountMenu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  async function signOut() {
    setOpen(false)
    await logout()
    navigate('/login?reason=signed-out', { replace: true })
  }

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        title="Account"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          height: 34,
          padding: '0 6px 0 4px',
          borderRadius: 'var(--radius-pill)',
          border: `1px solid ${open ? 'var(--border-default)' : 'transparent'}`,
          background: open ? 'var(--surface-inset)' : 'transparent',
          cursor: 'pointer',
        }}
      >
        <Avatar name={user?.name ?? ''} size={26} />
        <Icon name="chevron-down" size={14} color="var(--text-muted)" />
      </button>
      {open ? (
        <div
          role="menu"
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            right: 0,
            zIndex: 60,
            width: 250,
            background: 'var(--surface-card)',
            border: '1px solid var(--border-default)',
            borderRadius: 'var(--radius-md)',
            boxShadow: 'var(--shadow-pop)',
            overflow: 'hidden',
          }}
        >
          <div style={{ display: 'flex', gap: 11, padding: '14px 14px 12px', borderBottom: '1px solid var(--border-subtle)' }}>
            <Avatar name={user?.name ?? ''} size={38} />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 3, minWidth: 0 }}>
              <span style={{ fontSize: 'var(--fs-body-sm)', fontWeight: 600, color: 'var(--text-strong)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {user?.name}
              </span>
              <span style={{ fontFamily: 'var(--font-data)', fontSize: 11.5, color: 'var(--text-muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {user?.email}
              </span>
              <span style={{ marginTop: 3 }}>
                <RoleChip role={user?.role ?? ''} />
              </span>
            </div>
          </div>
          <div style={{ padding: 4 }}>
            <AccountMenuItem icon="settings" label="Account settings" onClick={() => setOpen(false)} />
            <AccountMenuItem icon="log-out" label="Sign out" danger onClick={signOut} />
          </div>
        </div>
      ) : null}
    </div>
  )
}

function TopBar({ title, theme, onToggleTheme }: { title: string; theme: 'sovereign' | 'carbon'; onToggleTheme: () => void }) {
  return (
    <header
      style={{
        height: 'var(--topbar-h)',
        flex: 'none',
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        padding: '0 24px',
        background: 'var(--surface-card)',
        borderBottom: '1px solid var(--border-subtle)',
      }}
    >
      <nav style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 'var(--fs-body-sm)', color: 'var(--text-muted)' }}>
        <span>Security</span>
        <Icon name="chevron-right" size={14} color="var(--text-faint)" />
        <span style={{ color: 'var(--text-strong)', fontWeight: 600 }}>{title}</span>
      </nav>
      <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 10 }}>
        <button
          type="button"
          onClick={onToggleTheme}
          title="Switch visual direction"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 7,
            height: 28,
            padding: '0 11px',
            borderRadius: 'var(--radius-pill)',
            border: '1px solid var(--border-default)',
            background: 'var(--surface-card)',
            cursor: 'pointer',
            fontSize: 12,
            fontWeight: 600,
            color: 'var(--text-body)',
          }}
        >
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--primary)' }} />
          {theme === 'carbon' ? 'Carbon' : 'Sovereign'}
        </button>
        <button
          type="button"
          title="Alerts"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 32,
            height: 32,
            borderRadius: 'var(--radius-sm)',
            border: '1px solid var(--border-default)',
            background: 'var(--surface-card)',
            cursor: 'pointer',
            color: 'var(--text-muted)',
          }}
        >
          <Icon name="bell" size={16} />
        </button>
        <span style={{ width: 1, height: 22, background: 'var(--border-subtle)', margin: '0 2px' }} />
        <AccountMenu />
      </div>
    </header>
  )
}

/** Authenticated app chrome: forest sidebar nav + top bar (account menu, theme toggle) + page outlet. */
export function AppShell() {
  const location = useLocation()
  const [theme, setTheme] = useState<'sovereign' | 'carbon'>('sovereign')
  const current = NAV.find((n) => isActive(location.pathname, n))

  return (
    <div
      className={`cm-root${theme === 'carbon' ? ' theme-carbon' : ''}`}
      style={{ display: 'flex', height: '100vh', overflow: 'hidden', background: 'var(--bg-app)' }}
    >
      <Sidebar />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <TopBar
          title={current?.label ?? 'Dashboard'}
          theme={theme}
          onToggleTheme={() => setTheme((t) => (t === 'carbon' ? 'sovereign' : 'carbon'))}
        />
        <main style={{ flex: 1, overflow: 'auto', padding: '24px 28px' }}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
