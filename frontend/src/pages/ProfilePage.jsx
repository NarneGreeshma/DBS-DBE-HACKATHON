import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext.jsx'
import { apiGet, apiPatch, apiPost, apiPut } from '../lib/api.js'
import { Badge, ErrorBanner, Field, PageHeader, Spinner } from '../components/ui.jsx'

export default function ProfilePage() {
  const { user, isCandidate, reloadUser } = useAuth()
  const [profile, setProfile] = useState(null)
  const [account, setAccount] = useState({ fullName: '', phone: '' })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [saved, setSaved] = useState(null)
  const [busy, setBusy] = useState(false)
  const [file, setFile] = useState(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([
      apiGet('/api/users/me'),
      isCandidate ? apiGet('/api/candidates/me') : Promise.resolve(null),
    ])
      .then(([me, candidateProfile]) => {
        if (cancelled) return
        setAccount({ fullName: me.fullName || '', phone: me.phone || '' })
        setProfile(candidateProfile)
      })
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [isCandidate])

  async function saveAccount(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setSaved(null)
    try {
      await apiPut('/api/users/me', account)
      if (isCandidate && profile) {
        await apiPut('/api/candidates/me', {
          headline: profile.headline || '',
          summary: profile.summary || '',
          experienceYears: Number(profile.experienceYears) || 0,
          location: profile.location || '',
          linkedinUrl: profile.linkedinUrl || '',
          portfolioUrl: profile.portfolioUrl || '',
        })
      }
      await reloadUser()
      setSaved('Profile saved')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function uploadResume(event) {
    event.preventDefault()
    if (!file) return
    setBusy(true)
    setError(null)
    setSaved(null)
    try {
      const form = new FormData()
      form.append('file', file)
      const updated = await apiPost('/api/candidates/me/resume', undefined, { formData: form })
      setProfile(updated)
      setFile(null)
      setSaved('Resume uploaded and text extracted')
      event.target.reset()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Spinner label="Loading profile…" />

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={isCandidate ? 'My profile' : 'Account settings'}
        subtitle={user?.email}
        actions={isCandidate && profile?.hasResume ? <Badge tone="green">Resume on file</Badge> : null}
      />

      {error && <div className="mb-4"><ErrorBanner error={error} onDismiss={() => setError(null)} /></div>}
      {saved && (
        <div className="mb-4 rounded-lg border border-emerald-900/50 bg-emerald-950/40 px-4 py-3 text-sm text-emerald-200">
          {saved}
          <button type="button" className="ml-3 text-emerald-400" onClick={() => setSaved(null)}>✕</button>
        </div>
      )}

      <form onSubmit={saveAccount} className="card space-y-4 p-5">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">Account</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Full name">
            <input className="input" value={account.fullName}
                   onChange={(e) => setAccount({ ...account, fullName: e.target.value })} required />
          </Field>
          <Field label="Phone">
            <input className="input" value={account.phone}
                   onChange={(e) => setAccount({ ...account, phone: e.target.value })} />
          </Field>
        </div>
        <div className="flex justify-end">
          <button className="btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save changes'}</button>
        </div>
      </form>

      {isCandidate && profile && (
        <>
          <form onSubmit={saveAccount} className="card mt-6 space-y-4 p-5">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">Career details</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Headline">
                <input className="input" value={profile.headline || ''}
                       onChange={(e) => setProfile({ ...profile, headline: e.target.value })} />
              </Field>
              <Field label="Location">
                <input className="input" value={profile.location || ''}
                       onChange={(e) => setProfile({ ...profile, location: e.target.value })} />
              </Field>
            </div>
            <Field label="Summary">
              <textarea className="input min-h-24" value={profile.summary || ''}
                        onChange={(e) => setProfile({ ...profile, summary: e.target.value })} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field label="Experience (years)">
                <input className="input" type="number" min="0" max="50"
                       value={profile.experienceYears ?? 0}
                       onChange={(e) => setProfile({ ...profile, experienceYears: e.target.value })} />
              </Field>
              <Field label="LinkedIn">
                <input className="input" value={profile.linkedinUrl || ''}
                       onChange={(e) => setProfile({ ...profile, linkedinUrl: e.target.value })} />
              </Field>
              <Field label="Portfolio">
                <input className="input" value={profile.portfolioUrl || ''}
                       onChange={(e) => setProfile({ ...profile, portfolioUrl: e.target.value })} />
              </Field>
            </div>
            <div className="flex justify-end">
              <button className="btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save details'}</button>
            </div>
          </form>

          <form onSubmit={uploadResume} className="card mt-6 space-y-4 p-5">
            <div>
              <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">Resume</h2>
              <p className="mt-1 text-xs text-slate-500">
                PDF, DOCX or TXT. Text is extracted by the Java backend and scored by the
                Python resume-intelligence service.
              </p>
            </div>

            {profile.hasResume && (
              <div className="flex items-center gap-3 rounded-lg border border-surface-line bg-surface px-3 py-2.5 text-sm">
                <span className="text-emerald-400">✓</span>
                <span className="flex-1 text-slate-300">{profile.resumeFileName}</span>
                <Badge tone="green">uploaded</Badge>
              </div>
            )}

            <Field label="Upload a new resume">
              <input className="input" type="file" accept=".pdf,.doc,.docx,.txt,.rtf"
                     onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            </Field>
            <div className="flex justify-end">
              <button className="btn-primary" disabled={busy || !file}>
                {busy ? 'Uploading…' : 'Upload resume'}
              </button>
            </div>
          </form>
        </>
      )}
    </div>
  )
}
