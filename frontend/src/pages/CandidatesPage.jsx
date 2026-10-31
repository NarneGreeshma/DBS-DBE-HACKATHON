import { useEffect, useState } from 'react'
import { apiGet } from '../lib/api.js'
import { Badge, EmptyState, ErrorBanner, Modal, PageHeader, Spinner } from '../components/ui.jsx'
import { formatDate } from '../lib/format.js'

export default function CandidatesPage() {
  const [candidates, setCandidates] = useState([])
  const [q, setQ] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selected, setSelected] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    apiGet('/api/candidates')
      .then((list) => !cancelled && setCandidates(list))
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  const filtered = candidates.filter((candidate) => {
    if (!q) return true
    const needle = q.toLowerCase()
    return (
      candidate.fullName.toLowerCase().includes(needle) ||
      candidate.email.toLowerCase().includes(needle) ||
      (candidate.headline || '').toLowerCase().includes(needle)
    )
  })

  if (loading) return <Spinner label="Loading candidates…" />
  if (error) return <ErrorBanner error={error} />

  return (
    <div>
      <PageHeader
        title="Candidates"
        subtitle={`${candidates.length} profile${candidates.length === 1 ? '' : 's'} on file`}
        actions={
          <input
            className="input max-w-xs"
            placeholder="Search name, email or headline…"
            value={q}
            onChange={(event) => setQ(event.target.value)}
          />
        }
      />

      {filtered.length === 0 ? (
        <EmptyState title="No candidates match" description="Try a different search term." />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((candidate) => (
            <button
              key={candidate.userId}
              type="button"
              onClick={() => setSelected(candidate)}
              className="card p-5 text-left transition hover:border-brand-500/50"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-semibold text-white">{candidate.fullName}</p>
                  <p className="truncate text-xs text-slate-500">{candidate.email}</p>
                </div>
                <Badge tone={candidate.hasResume ? 'green' : 'slate'}>
                  {candidate.hasResume ? 'Resume' : 'No resume'}
                </Badge>
              </div>
              <p className="mt-3 line-clamp-2 text-sm text-slate-400">
                {candidate.headline || 'No headline yet'}
              </p>
              <div className="mt-3 flex flex-wrap gap-1.5">
                <Badge>{candidate.experienceYears} yr{candidate.experienceYears === 1 ? '' : 's'}</Badge>
                {candidate.location && <Badge>{candidate.location}</Badge>}
              </div>
            </button>
          ))}
        </div>
      )}

      <Modal
        open={Boolean(selected)}
        title={selected?.fullName || ''}
        onClose={() => setSelected(null)}
      >
        {selected && (
          <div className="space-y-4 text-sm">
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <p className="label">Email</p>
                <p className="text-slate-300">{selected.email}</p>
              </div>
              <div>
                <p className="label">Phone</p>
                <p className="text-slate-300">{selected.phone || '—'}</p>
              </div>
              <div>
                <p className="label">Experience</p>
                <p className="text-slate-300">{selected.experienceYears} year(s)</p>
              </div>
              <div>
                <p className="label">Location</p>
                <p className="text-slate-300">{selected.location || '—'}</p>
              </div>
            </div>
            {selected.summary && (
              <div>
                <p className="label">Summary</p>
                <p className="whitespace-pre-line text-slate-300">{selected.summary}</p>
              </div>
            )}
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <p className="label">LinkedIn</p>
                <p className="break-all text-slate-300">{selected.linkedinUrl || '—'}</p>
              </div>
              <div>
                <p className="label">Portfolio</p>
                <p className="break-all text-slate-300">{selected.portfolioUrl || '—'}</p>
              </div>
            </div>
            <div>
              <p className="label">Resume</p>
              {selected.hasResume ? (
                <Badge tone="green">{selected.resumeFileName}</Badge>
              ) : (
                <p className="text-slate-500">No resume uploaded.</p>
              )}
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
