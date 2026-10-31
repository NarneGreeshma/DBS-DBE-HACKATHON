import { useCallback, useEffect, useState } from 'react'
import { apiGet, apiPatch, apiPost } from '../lib/api.js'
import {
  Badge,
  EmptyState,
  ErrorBanner,
  Field,
  Modal,
  PageHeader,
  Spinner,
} from '../components/ui.jsx'
import { formatDate } from '../lib/format.js'

const STATUS_TONE = { ACTIVE: 'green', INVITED: 'amber', SUSPENDED: 'rose' }

export default function AdminUsersPage() {
  const [users, setUsers] = useState([])
  const [roles, setRoles] = useState([])
  const [filters, setFilters] = useState({ q: '', role: '', status: '' })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'RECRUITER', phone: '' })
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    const params = new URLSearchParams({ page: '0', size: '100' })
    if (filters.q) params.set('q', filters.q)
    if (filters.role) params.set('role', filters.role)
    if (filters.status) params.set('status', filters.status)

    Promise.all([apiGet(`/api/admin/users?${params}`), apiGet('/api/admin/users/roles')])
      .then(([page, roleList]) => {
        setUsers(page.content)
        setRoles(roleList)
      })
      .catch(setError)
      .finally(() => setLoading(false))
  }, [filters])

  useEffect(load, [load])

  async function createUser(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await apiPost('/api/admin/users', {
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
        phone: form.phone || undefined,
      })
      setCreating(false)
      setForm({ fullName: '', email: '', password: '', role: 'RECRUITER', phone: '' })
      setNotice(`Account created for ${form.email}`)
      load()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function changeStatus(user, status) {
    setError(null)
    try {
      await apiPatch(`/api/admin/users/${user.id}/status`, { status })
      setNotice(`${user.email} → ${status}`)
      load()
    } catch (err) {
      setError(err)
    }
  }

  if (loading) return <Spinner label="Loading accounts…" />

  return (
    <div>
      <PageHeader
        title="User administration"
        subtitle="Provision recruiters and admins, suspend accounts."
        actions={
          <button type="button" className="btn-primary" onClick={() => setCreating(true)}>
            + New account
          </button>
        }
      />

      {notice && (
        <div className="mb-4 rounded-lg border border-emerald-900/50 bg-emerald-950/40 px-4 py-3 text-sm text-emerald-200">
          {notice}
          <button type="button" className="ml-3 text-emerald-400" onClick={() => setNotice(null)}>✕</button>
        </div>
      )}
      {error && <div className="mb-4"><ErrorBanner error={error} onDismiss={() => setError(null)} /></div>}

      <div className="card mb-5 grid gap-3 p-4 sm:grid-cols-3">
        <div>
          <label className="label" htmlFor="user-q">Search</label>
          <input id="user-q" className="input" placeholder="Name or email…"
                 value={filters.q} onChange={(e) => setFilters({ ...filters, q: e.target.value })} />
        </div>
        <div>
          <label className="label" htmlFor="user-role">Role</label>
          <select id="user-role" className="input" value={filters.role}
                  onChange={(e) => setFilters({ ...filters, role: e.target.value })}>
            <option value="">All roles</option>
            {roles.map((role) => <option key={role} value={role}>{role}</option>)}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="user-status">Status</label>
          <select id="user-status" className="input" value={filters.status}
                  onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
            <option value="">Any</option>
            <option value="ACTIVE">Active</option>
            <option value="INVITED">Invited</option>
            <option value="SUSPENDED">Suspended</option>
          </select>
        </div>
      </div>

      {users.length === 0 ? (
        <EmptyState title="No accounts match" description="Adjust the filters." />
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="table-base">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Last login</th>
                  <th>Created</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((account) => (
                  <tr key={account.id}>
                    <td>
                      <p className="font-medium text-slate-200">{account.fullName}</p>
                      <p className="text-xs text-slate-500">{account.email}</p>
                    </td>
                    <td>
                      <Badge tone={account.role === 'ADMIN' ? 'violet' : account.role === 'RECRUITER' ? 'brand' : 'slate'}>
                        {account.role}
                      </Badge>
                    </td>
                    <td><Badge tone={STATUS_TONE[account.status]}>{account.status}</Badge></td>
                    <td className="text-slate-500">{account.lastLoginAt ? formatDate(account.lastLoginAt) : 'never'}</td>
                    <td className="text-slate-500">{formatDate(account.createdAt)}</td>
                    <td className="text-right">
                      {account.status === 'ACTIVE' ? (
                        <button type="button" className="btn-danger !px-2.5 !py-1 text-xs"
                                onClick={() => changeStatus(account, 'SUSPENDED')}>
                          Suspend
                        </button>
                      ) : (
                        <button type="button" className="btn-primary !px-2.5 !py-1 text-xs"
                                onClick={() => changeStatus(account, 'ACTIVE')}>
                          Activate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal open={creating} title="Create an account" onClose={() => setCreating(false)}>
        <form onSubmit={createUser} className="space-y-4">
          <Field label="Full name">
            <input className="input" value={form.fullName} required
                   onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
          </Field>
          <Field label="Email">
            <input className="input" type="email" value={form.email} required
                   onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Password" hint="At least 8 characters.">
              <input className="input" type="password" minLength={8} value={form.password} required
                     onChange={(e) => setForm({ ...form, password: e.target.value })} />
            </Field>
            <Field label="Role">
              <select className="input" value={form.role}
                      onChange={(e) => setForm({ ...form, role: e.target.value })}>
                {roles.filter((role) => role !== 'CANDIDATE').map((role) => (
                  <option key={role} value={role}>{role}</option>
                ))}
              </select>
            </Field>
          </div>
          <Field label="Phone (optional)">
            <input className="input" value={form.phone}
                   onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setCreating(false)}>Cancel</button>
            <button className="btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
