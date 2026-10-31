import { useEffect, useState } from 'react'
import { NavLink, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { apiGet } from '../lib/api.js'
import { initials } from '../lib/format.js'

const STAFF_NAV = [
  { to: '/dashboard', label: 'Dashboard', icon: '◈' },
  { to: '/jobs/manage', label: 'Job Postings', icon: '▤' },
  { to: '/pipeline', label: 'Pipeline', icon: '⇄' },
  { to: '/interviews', label: 'Interviews', icon: '◷' },
  { to: '/analytics', label: 'Analytics', icon: '◔' },
  { to: '/candidates', label: 'Candidates', icon: '◉' },
]

const CANDIDATE_NAV = [
  { to: '/jobs', label: 'Find Jobs', icon: '▤' },
  { to: '/applications', label: 'My Applications', icon: '⇄' },
  { to: '/interviews', label: 'Interviews', icon: '◷' },
  { to: '/profile', label: 'My Profile', icon: '◉' },
]

const ADMIN_NAV = [{ to: '/admin/users', label: 'Users', icon: '⚙' }]

function Brand() {
  return (
    <div className="flex items-center gap-2.5 px-5 py-5">
      <div className="grid h-8 w-8 place-items-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 text-sm font-bold text-white">
        S
      </div>
      <div className="leading-tight">
        <p className="text-sm font-semibold text-white">SmartHire</p>
        <p className="text-[11px] text-slate-500">Recruitment OS</p>
      </div>
    </div>
  )
}

function NavItem({ item, onNavigate }) {
  return (
    <NavLink
      to={item.to}
      onClick={onNavigate}
      className={({ isActive }) =>
        `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition ${
          isActive
            ? 'bg-brand-500/15 text-brand-300 ring-1 ring-inset ring-brand-500/30'
            : 'text-slate-400 hover:bg-white/5 hover:text-slate-200'
        }`
      }
    >
      <span className="w-4 text-center text-xs opacity-70">{item.icon}</span>
      <span className="flex-1">{item.label}</span>
      {item.count > 0 && (
        <span className="grid h-4 min-w-4 place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
          {item.count > 9 ? '9+' : item.count}
        </span>
      )}
    </NavLink>
  )
}

export default function Layout() {
  const { user, role, isStaff, isAdmin, isCandidate, logout, booting } = useAuth()
  const location = useLocation()
  const [open, setOpen] = useState(false)
  const [unread, setUnread] = useState(0)

  useEffect(() => {
    if (!user) return
    let cancelled = false
    const load = async () => {
      try {
        const data = await apiGet('/api/notifications/unread-count')
        if (!cancelled) setUnread(data.unread || 0)
      } catch {
        /* notifications are non-critical */
      }
    }
    load()
    const id = setInterval(load, 30000)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [user, location.pathname])

  if (booting) {
    return (
      <div className="grid min-h-screen place-items-center text-slate-500">Restoring session…</div>
    )
  }
  if (!user) return <Navigate to="/login" replace />

  const items = isStaff ? STAFF_NAV : CANDIDATE_NAV
  const nav = (
    <>
      <Brand />
      <nav className="flex-1 space-y-1 px-3">
        {items.map((item) => (
          <NavItem key={item.to} item={item} onNavigate={() => setOpen(false)} />
        ))}
        {isAdmin && ADMIN_NAV.map((item) => (
          <NavItem key={item.to} item={item} onNavigate={() => setOpen(false)} />
        ))}
        <div className="my-3 border-t border-surface-line" />
        <NavItem
          item={{ to: '/notifications', label: 'Notifications', icon: '◔', count: unread }}
          onNavigate={() => setOpen(false)}
        />
        <NavItem
          item={{ to: isStaff ? '/settings' : '/profile', label: 'Settings', icon: '⚙' }}
          onNavigate={() => setOpen(false)}
        />
      </nav>

      <div className="border-t border-surface-line p-3">
        <div className="flex items-center gap-3 px-2 py-1.5">
          <div className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-surface-overlay text-xs font-semibold text-brand-300">
            {initials(user.fullName)}
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm text-slate-200">{user.fullName}</p>
            <p className="truncate text-[11px] uppercase tracking-wide text-slate-500">{role}</p>
          </div>
        </div>
        <button type="button" onClick={logout} className="btn-secondary mt-2 w-full">
          Sign out
        </button>
      </div>
    </>
  )

  return (
    <div className="flex min-h-screen">
      {/* desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-surface-line bg-surface-raised lg:flex">
        {nav}
      </aside>

      {/* mobile drawer */}
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/60" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 flex w-64 flex-col border-r border-surface-line bg-surface-raised">
            {nav}
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col lg:pl-60">
        <header className="sticky top-0 z-20 flex items-center gap-3 border-b border-surface-line bg-surface/80 px-4 py-3 backdrop-blur">
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="btn-secondary lg:hidden"
            aria-label="Open menu"
          >
            ☰
          </button>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm text-slate-400">
              {isStaff ? 'Recruiter console' : 'Candidate portal'}
            </p>
          </div>
          <NavLink
            to="/jobs"
            className="btn-secondary hidden sm:inline-flex"
          >
            Browse jobs
          </NavLink>
          <NavLink
            to="/notifications"
            className="btn-secondary relative"
            aria-label="Notifications"
          >
            ◔
            {unread > 0 && (
              <span className="absolute -right-1.5 -top-1.5 grid h-4 min-w-4 place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
                {unread > 9 ? '9+' : unread}
              </span>
            )}
          </NavLink>
        </header>

        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
