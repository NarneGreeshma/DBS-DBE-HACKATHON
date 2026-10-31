import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { ErrorBanner, Field } from '../components/ui.jsx'

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({
    fullName: '',
    email: '',
    password: '',
    headline: '',
    location: '',
  })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const update = (key) => (event) => setForm({ ...form, [key]: event.target.value })

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        headline: form.headline || undefined,
        location: form.location || undefined,
      })
      navigate('/jobs', { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid min-h-screen place-items-center px-4 py-10">
      <div className="w-full max-w-md">
        <Link to="/login" className="text-sm text-slate-500 hover:text-slate-300">
          ← Back to sign in
        </Link>
        <h1 className="mt-4 text-xl font-semibold text-white">Create your candidate account</h1>
        <p className="mt-1 text-sm text-slate-500">
          Self-registration always creates a <strong className="text-slate-400">CANDIDATE</strong>{' '}
          account. Recruiters and admins are provisioned by an administrator.
        </p>

        <div className="mt-5">
          <ErrorBanner error={error} onDismiss={() => setError(null)} />
        </div>

        <form onSubmit={submit} className="mt-4 space-y-4">
          <Field label="Full name">
            <input className="input" value={form.fullName} onChange={update('fullName')} required />
          </Field>
          <Field label="Email">
            <input
              className="input"
              type="email"
              autoComplete="email"
              value={form.email}
              onChange={update('email')}
              required
            />
          </Field>
          <Field label="Password" hint="At least 8 characters, must contain a letter.">
            <input
              className="input"
              type="password"
              autoComplete="new-password"
              value={form.password}
              onChange={update('password')}
              minLength={8}
              required
            />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Headline">
              <input
                className="input"
                placeholder="Senior Java Engineer"
                value={form.headline}
                onChange={update('headline')}
              />
            </Field>
            <Field label="Location">
              <input
                className="input"
                placeholder="Bengaluru, IN"
                value={form.location}
                onChange={update('location')}
              />
            </Field>
          </div>
          <button className="btn-primary w-full" disabled={busy}>
            {busy ? 'Creating…' : 'Create account'}
          </button>
        </form>

        <p className="mt-5 text-sm text-slate-500">
          Already registered?{' '}
          <Link to="/login" className="text-brand-400 hover:text-brand-300">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
