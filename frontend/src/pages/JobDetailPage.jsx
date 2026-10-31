import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import { apiGet, apiPost } from '../lib/api.js'
import { Badge, ErrorBanner, JobStatusBadge, PageHeader, Spinner } from '../components/ui.jsx'
import { formatDate, salaryRange, titleCase } from '../lib/format.js'

export default function JobDetailPage() {
  const { id } = useParams()
  const { user, isCandidate, isAuthenticated } = useAuth()
  const [job, setJob] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const [coverLetter, setCoverLetter] = useState('')
  const [resume, setResume] = useState(null)
  const [applying, setApplying] = useState(false)
  const [applied, setApplied] = useState(false)
  const [applyError, setApplyError] = useState(null)

  const load = useCallback(() => {
    setLoading(true)
    apiGet(`/api/jobs/${id}`)
      .then(setJob)
      .catch(setError)
      .finally(() => setLoading(false))
  }, [id])

  useEffect(load, [load])

  useEffect(() => {
    if (!isCandidate || !user) return
    apiGet('/api/applications/mine')
      .then((list) => {
        if (list.some((application) => application.job.id === Number(id))) setApplied(true)
      })
      .catch(() => {})
  }, [id, isCandidate, user])

  async function submit(event) {
    event.preventDefault()
    setApplying(true)
    setApplyError(null)
    try {
      const form = new FormData()
      form.append('jobId', id)
      if (coverLetter) form.append('coverLetter', coverLetter)
      if (resume) form.append('resume', resume)
      await apiPost('/api/applications', undefined, { formData: form })
      setApplied(true)
    } catch (err) {
      setApplyError(err)
    } finally {
      setApplying(false)
    }
  }

  if (loading) return <Spinner label="Loading role…" />
  if (error) return <ErrorBanner error={error} />
  if (!job) return null

  const closed = job.status !== 'OPEN'

  return (
    <div>
      <Link to="/jobs" className="text-sm text-slate-500 hover:text-slate-300">
        ← All roles
      </Link>

      <PageHeader
        title={job.title}
        subtitle={`${job.location || 'Unspecified'} · ${titleCase(job.workplaceType)} · ${titleCase(job.employmentType)} · ${titleCase(job.experienceLevel)}`}
        actions={
          <>
            <JobStatusBadge status={job.status} />
            <Badge>{job.openings} opening{job.openings === 1 ? '' : 's'}</Badge>
            <Badge tone="brand">{salaryRange(job) || 'Competitive'}</Badge>
          </>
        }
      />

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          <section className="card p-5">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">About the role</h2>
            <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-slate-300">
              {job.description}
            </p>
          </section>

          {job.responsibilities && (
            <section className="card p-5">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                Responsibilities
              </h2>
              <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-slate-300">
                {job.responsibilities}
              </p>
            </section>
          )}

          {job.requirements && (
            <section className="card p-5">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                Requirements
              </h2>
              <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-slate-300">
                {job.requirements}
              </p>
            </section>
          )}

          <section className="card p-5">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
              Skills
            </h2>
            <div className="mt-3 flex flex-wrap gap-2">
              {job.skills.length === 0 && <span className="text-sm text-slate-500">No skills listed.</span>}
              {job.skills.map((skill) => (
                <Badge key={skill.id} tone={skill.required ? 'brand' : 'slate'}>
                  {skill.name}
                  {skill.required ? ' *' : ''}
                </Badge>
              ))}
            </div>
            {job.skills.some((skill) => skill.required) && (
              <p className="mt-2 text-xs text-slate-500">* required</p>
            )}
          </section>

          <section className="card p-5 text-sm text-slate-400">
            <div className="grid gap-3 sm:grid-cols-2">
              <p>Published: <span className="text-slate-200">{formatDate(job.publishedAt)}</span></p>
              <p>Closes: <span className="text-slate-200">{job.closesAt ? formatDate(job.closesAt) : 'Open until filled'}</span></p>
              <p>Experience: <span className="text-slate-200">
                {job.minYears ?? '—'}{job.maxYears ? `–${job.maxYears}` : '+'} years
              </span></p>
              <p>Applicants: <span className="text-slate-200">{job.applicationCount ?? 0}</span></p>
            </div>
          </section>
        </div>

        <div className="lg:col-span-1">
          <div className="card sticky top-24 p-5">
            {!isAuthenticated ? (
              <>
                <p className="text-sm text-slate-400">Sign in to apply for this role.</p>
                <Link to="/login" className="btn-primary mt-4 w-full">Sign in</Link>
              </>
            ) : !isCandidate ? (
              <p className="text-sm text-slate-400">
                You are signed in as {user.role} — switch to a candidate account to apply.
              </p>
            ) : applied ? (
              <div className="text-center">
                <p className="text-sm font-medium text-emerald-300">Application submitted</p>
                <p className="mt-1 text-xs text-slate-500">
                  Track its progress in My Applications.
                </p>
                <Link to="/applications" className="btn-secondary mt-4 w-full">
                  View my applications
                </Link>
              </div>
            ) : closed ? (
              <p className="text-sm text-slate-400">
                This role is no longer accepting applications.
              </p>
            ) : (
              <form onSubmit={submit} className="space-y-4">
                <div>
                  <label className="label" htmlFor="cl">Cover letter</label>
                  <textarea
                    id="cl"
                    className="input min-h-28"
                    placeholder="Why are you a good fit?"
                    value={coverLetter}
                    onChange={(event) => setCoverLetter(event.target.value)}
                  />
                </div>
                <div>
                  <label className="label" htmlFor="resume">Resume (PDF, DOCX or TXT)</label>
                  <input
                    id="resume"
                    type="file"
                    accept=".pdf,.doc,.docx,.txt,.rtf"
                    className="input"
                    onChange={(event) => setResume(event.target.files?.[0] ?? null)}
                  />
                  <p className="mt-1 text-xs text-slate-500">
                    Optional — uses the resume already on your profile if omitted.
                  </p>
                </div>
                {applyError && <ErrorBanner error={applyError} onDismiss={() => setApplyError(null)} />}
                <button className="btn-primary w-full" disabled={applying}>
                  {applying ? 'Submitting…' : 'Apply now'}
                </button>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
