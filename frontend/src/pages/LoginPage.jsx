import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { ErrorBanner, Field } from '../components/ui.jsx'

const DEMO = [
  { role: 'Admin', email: 'admin@smarthire.io' },
  { role: 'Recruiter', email: 'recruiter@smarthire.io' },
  { role: 'Candidate', email: 'candidate@smarthire.io' },
]

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('recruiter@smarthire.io')
  const [password, setPassword] = useState('Password123!')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const user = await login(email.trim(), password)
      navigate(user.role === 'CANDIDATE' ? '/jobs' : '/dashboard', { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* brand panel */}
      <div className="relative hidden overflow-hidden border-r border-surface-line bg-surface-raised lg:block">
        <div className="absolute -left-32 -top-32 h-96 w-96 rounded-full bg-brand-600/20 blur-3xl" />
        <div className="absolute bottom-0 right-0 h-96 w-96 rounded-full bg-violet-600/10 blur-3xl" />
        <div className="relative flex h-full flex-col justify-between p-12">
          <div className="flex items-center gap-2.5">
            <div className="grid h-9 w-9 place-items-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 font-bold text-white">
              S
            </div>
            <span className="text-lg font-semibold text-white">SmartHire</span>
          </div>
          <div>
            <h1 className="max-w-md text-3xl font-semibold leading-tight text-white">
              Hire with evidence, not guesswork.
            </h1>
            <p className="mt-4 max-w-md text-sm leading-relaxed text-slate-400">
              Post roles, run a structured pipeline, and let resume intelligence surface the
              candidates who actually match — with every score explained.
            </p>
            <div className="mt-8 grid max-w-md grid-cols-3 gap-4">
              {[
                ['4', 'open roles'],
                ['AI', 'screening'],
                ['100%', 'role-based'],
              ].map(([value, label]) => (
                <div key={label} className="rounded-lg border border-surface-line bg-surface/60 p-3">
                  <p className="text-xl font-semibold text-white">{value}</p>
                  <p className="text-[11px] uppercase tracking-wide text-slate-500">{label}</p>
                </div>
              ))}
            </div>
          </div>
          <p className="text-xs text-slate-600">Spring Boot · PostgreSQL · FastAPI · React</p>
        </div>
      </div>

      {/* form */}
      <div className="flex items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm">
          <h2 className="text-xl font-semibold text-white">Sign in</h2>
          <p className="mt-1 text-sm text-slate-500">Access the SmartHire console.</p>

          <div className="mt-5">
            <ErrorBanner error={error} onDismiss={() => setError(null)} />
          </div>

          <form onSubmit={submit} className="mt-4 space-y-4">
            <Field label="Email">
              <input
                className="input"
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </Field>
            <Field label="Password">
              <input
                className="input"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </Field>
            <button className="btn-primary w-full" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <div className="mt-5 rounded-lg border border-surface-line bg-surface-raised p-3">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
              Demo accounts (password: Password123!)
            </p>
            <div className="mt-2 space-y-1">
              {DEMO.map((account) => (
                <button
                  key={account.email}
                  type="button"
                  onClick={() => {
                    setEmail(account.email)
                    setPassword('Password123!')
                  }}
                  className="flex w-full items-center justify-between rounded-md px-2 py-1.5 text-left text-xs text-slate-400 hover:bg-white/5 hover:text-slate-200"
                >
                  <span className="font-mono">{account.email}</span>
                  <span className="text-brand-300">{account.role} →</span>
                </button>
              ))}
            </div>
          </div>

          <p className="mt-5 text-sm text-slate-500">
            No account?{' '}
            <Link to="/register" className="text-brand-400 hover:text-brand-300">
              Create one
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
