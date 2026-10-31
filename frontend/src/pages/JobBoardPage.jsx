import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiGet } from '../lib/api.js'
import { Badge, EmptyState, ErrorBanner, JobStatusBadge, PageHeader, Spinner } from '../components/ui.jsx'
import { formatDate, salaryRange, titleCase } from '../lib/format.js'

const PAGE_SIZE = 9

export default function JobBoardPage() {
  const [jobs, setJobs] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [q, setQ] = useState('')
  const [location, setLocation] = useState('')
  const [workplace, setWorkplace] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) })
    if (q) params.set('q', q)
    if (location) params.set('location', location)
    if (workplace) params.set('workplace', workplace)

    apiGet(`/api/jobs?${params}`)
      .then((data) => {
        if (cancelled) return
        setJobs(data.content)
        setTotal(data.totalElements)
      })
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [page, q, location, workplace])

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const filtersActive = useMemo(() => q || location || workplace, [q, location, workplace])

  return (
    <div>
      <PageHeader
        title="Open roles"
        subtitle="Every published position at SmartHire."
        actions={<Link to="/jobs/manage" className="btn-secondary">Manage postings</Link>}
      />

      <div className="card mb-5 grid gap-3 p-4 sm:grid-cols-4">
        <div className="sm:col-span-2">
          <label className="label" htmlFor="search">Search</label>
          <input
            id="search"
            className="input"
            placeholder="Title or keyword…"
            value={q}
            onChange={(e) => {
              setPage(0)
              setQ(e.target.value)
            }}
          />
        </div>
        <div>
          <label className="label" htmlFor="loc">Location</label>
          <input
            id="loc"
            className="input"
            placeholder="City…"
            value={location}
            onChange={(e) => {
              setPage(0)
              setLocation(e.target.value)
            }}
          />
        </div>
        <div>
          <label className="label" htmlFor="wp">Workplace</label>
          <select
            id="wp"
            className="input"
            value={workplace}
            onChange={(e) => {
              setPage(0)
              setWorkplace(e.target.value)
            }}
          >
            <option value="">Any</option>
            <option value="ONSITE">Onsite</option>
            <option value="HYBRID">Hybrid</option>
            <option value="REMOTE">Remote</option>
          </select>
        </div>
      </div>

      {error && <ErrorBanner error={error} onDismiss={() => setError(null)} />}
      {loading ? (
        <Spinner label="Loading roles…" />
      ) : jobs.length === 0 ? (
        <EmptyState
          title={filtersActive ? 'No roles match those filters' : 'No open roles right now'}
          description={filtersActive ? 'Try widening your search.' : 'Check back soon — new roles are published weekly.'}
          action={
            filtersActive ? (
              <button
                type="button"
                className="btn-secondary"
                onClick={() => {
                  setQ('')
                  setLocation('')
                  setWorkplace('')
                  setPage(0)
                }}
              >
                Clear filters
              </button>
            ) : null
          }
        />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {jobs.map((job) => (
              <Link
                key={job.id}
                to={`/jobs/${job.id}`}
                className="card group flex flex-col p-5 transition hover:border-brand-500/50"
              >
                <div className="flex items-start justify-between gap-3">
                  <h2 className="text-base font-semibold text-white group-hover:text-brand-300">
                    {job.title}
                  </h2>
                  <JobStatusBadge status={job.status} />
                </div>
                <p className="mt-2 line-clamp-3 flex-1 text-sm leading-relaxed text-slate-400">
                  {job.description}
                </p>
                <div className="mt-4 flex flex-wrap gap-1.5">
                  {job.location && <Badge>{job.location}</Badge>}
                  <Badge>{titleCase(job.workplaceType)}</Badge>
                  <Badge>{titleCase(job.employmentType)}</Badge>
                  <Badge>{titleCase(job.experienceLevel)}</Badge>
                </div>
                <div className="mt-4 flex items-center justify-between border-t border-surface-line pt-3 text-xs text-slate-500">
                  <span className="font-medium text-slate-300">{salaryRange(job) || 'Competitive'}</span>
                  <span>{job.applicationCount ?? 0} applicant{(job.applicationCount ?? 0) === 1 ? '' : 's'}</span>
                </div>
                <p className="mt-2 text-[11px] text-slate-600">
                  Published {formatDate(job.publishedAt)}
                </p>
              </Link>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="mt-6 flex items-center justify-between">
              <p className="text-sm text-slate-500">
                {total} role{total === 1 ? '' : 's'}
              </p>
              <div className="flex gap-2">
                <button className="btn-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                  ← Previous
                </button>
                <span className="grid place-items-center px-3 text-sm text-slate-500">
                  {page + 1} / {totalPages}
                </span>
                <button
                  className="btn-secondary"
                  disabled={page + 1 >= totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  Next →
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
