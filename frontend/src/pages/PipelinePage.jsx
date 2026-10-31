import { useCallback, useEffect, useState } from 'react'
import { apiGet, apiPatch, apiPost } from '../lib/api.js'
import {
  Badge,
  EmptyState,
  ErrorBanner,
  Modal,
  PageHeader,
  ScorePill,
  Spinner,
  StageBadge,
} from '../components/ui.jsx'
import { formatDateTime, timeAgo, titleCase } from '../lib/format.js'

const STAGES = [
  'APPLIED', 'SCREENING', 'SHORTLISTED', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED',
]

export default function PipelinePage() {
  const [jobs, setJobs] = useState([])
  const [applications, setApplications] = useState([])
  const [counts, setCounts] = useState({})
  const [filters, setFilters] = useState({ jobId: '', stage: '', q: '', sortByScore: false })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [selected, setSelected] = useState(null)
  const [detail, setDetail] = useState({ analysis: null, timeline: [], interviews: [] })
  const [nextStage, setNextStage] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    const params = new URLSearchParams({ page: '0', size: '50' })
    if (filters.jobId) params.set('jobId', filters.jobId)
    if (filters.stage) params.set('stage', filters.stage)
    if (filters.q) params.set('q', filters.q)
    if (filters.sortByScore) params.set('sortByScore', 'true')

    Promise.all([
      apiGet(`/api/applications?${params}`),
      apiGet('/api/jobs?size=100'),
      apiGet('/api/applications/stage-counts'),
    ])
      .then(([page, jobPage, stageCounts]) => {
        setApplications(page.content)
        setJobs(jobPage.content)
        setCounts(stageCounts)
      })
      .catch(setError)
      .finally(() => setLoading(false))
  }, [filters])

  useEffect(load, [load])

  const openDetail = useCallback(async (application) => {
    setSelected(application)
    setNextStage('')
    setNote('')
    try {
      const [analysis, timeline, interviews] = await Promise.all([
        apiGet(`/api/applications/${application.id}/analysis`),
        apiGet(`/api/applications/${application.id}/timeline`),
        apiGet(`/api/interviews/application/${application.id}`),
      ])
      setDetail({ analysis: analysis[0] || null, timeline, interviews })
    } catch (err) {
      setError(err)
    }
  }, [])

  async function moveStage() {
    if (!nextStage || !selected) return
    setBusy(true)
    setError(null)
    try {
      await apiPatch(`/api/applications/${selected.id}/stage`, {
        stage: nextStage,
        note: note || null,
      })
      setSelected(null)
      load()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function reanalyze() {
    if (!selected) return
    setBusy(true)
    try {
      await apiPost(`/api/applications/${selected.id}/analyze`)
      await openDetail(selected)
      load()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <PageHeader
        title="Hiring pipeline"
        subtitle="Review applicants, run AI screening and move candidates forward."
      />

      <div className="card mb-5 grid gap-3 p-4 sm:grid-cols-4">
        <div>
          <label className="label" htmlFor="job-filter">Job</label>
          <select id="job-filter" className="input" value={filters.jobId}
                  onChange={(e) => setFilters({ ...filters, jobId: e.target.value })}>
            <option value="">All jobs</option>
            {jobs.map((job) => <option key={job.id} value={job.id}>{job.title}</option>)}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="stage-filter">Stage</label>
          <select id="stage-filter" className="input" value={filters.stage}
                  onChange={(e) => setFilters({ ...filters, stage: e.target.value })}>
            <option value="">All stages</option>
            {STAGES.map((stage) => <option key={stage} value={stage}>{titleCase(stage)}</option>)}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="q-filter">Candidate</label>
          <input id="q-filter" className="input" placeholder="Name or email…"
                 value={filters.q} onChange={(e) => setFilters({ ...filters, q: e.target.value })} />
        </div>
        <div className="flex items-end">
          <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-400">
            <input type="checkbox" className="h-4 w-4 accent-brand-500"
                   checked={filters.sortByScore}
                   onChange={(e) => setFilters({ ...filters, sortByScore: e.target.checked })} />
            Sort by AI score
          </label>
        </div>
      </div>

      <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-8">
        {STAGES.concat('WITHDRAWN').map((stage) => (
          <button
            key={stage}
            type="button"
            onClick={() => setFilters({ ...filters, stage: filters.stage === stage ? '' : stage })}
            className={`card px-3 py-2 text-left transition ${
              filters.stage === stage ? 'border-brand-500/60' : 'hover:border-slate-600'
            }`}
          >
            <p className="text-[11px] uppercase tracking-wide text-slate-500">{titleCase(stage)}</p>
            <p className="text-lg font-semibold tabular-nums text-white">{counts[stage] ?? 0}</p>
          </button>
        ))}
      </div>

      {error && <div className="mb-4"><ErrorBanner error={error} onDismiss={() => setError(null)} /></div>}

      {loading ? (
        <Spinner label="Loading pipeline…" />
      ) : applications.length === 0 ? (
        <EmptyState
          title="No applications match"
          description="Adjust the filters or wait for candidates to apply."
          action={
            <button type="button" className="btn-secondary"
                    onClick={() => setFilters({ jobId: '', stage: '', q: '', sortByScore: false })}>
              Clear filters
            </button>
          }
        />
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="table-base">
              <thead>
                <tr>
                  <th>Candidate</th>
                  <th>Role</th>
                  <th>Stage</th>
                  <th>AI score</th>
                  <th>Missing skills</th>
                  <th>Applied</th>
                  <th className="text-right"></th>
                </tr>
              </thead>
              <tbody>
                {applications.map((application) => (
                  <tr key={application.id}>
                    <td>
                      <p className="font-medium text-slate-200">{application.candidate.fullName}</p>
                      <p className="text-xs text-slate-500">{application.candidate.email}</p>
                    </td>
                    <td>
                      <p className="text-slate-300">{application.job.title}</p>
                      <p className="text-xs text-slate-500">{application.job.location || '—'}</p>
                    </td>
                    <td><StageBadge stage={application.stage} /></td>
                    <td><ScorePill value={application.overallScore} /></td>
                    <td>
                      <div className="flex max-w-48 flex-wrap gap-1">
                        {(application.analysis?.missingSkills || []).slice(0, 3).map((skill) => (
                          <Badge key={skill} tone="amber">{skill}</Badge>
                        ))}
                        {!application.analysis?.missingSkills?.length && (
                          <span className="text-xs text-slate-600">—</span>
                        )}
                      </div>
                    </td>
                    <td className="text-slate-500">{timeAgo(application.appliedAt)}</td>
                    <td className="text-right">
                      <button type="button" className="btn-secondary !px-2.5 !py-1 text-xs"
                              onClick={() => openDetail(application)}>
                        Review
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal
        open={Boolean(selected)}
        wide
        title={selected ? `${selected.candidate.fullName} · ${selected.job.title}` : ''}
        onClose={() => setSelected(null)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setSelected(null)}>Close</button>
            <button type="button" className="btn-secondary" onClick={reanalyze} disabled={busy}>
              {busy ? 'Working…' : 'Re-run AI screening'}
            </button>
            <button type="button" className="btn-primary" onClick={moveStage} disabled={busy || !nextStage}>
              {busy ? 'Saving…' : 'Move stage'}
            </button>
          </>
        }
      >
        {selected && (
          <div className="space-y-5">
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg border border-surface-line bg-surface p-3">
                <p className="text-[11px] uppercase text-slate-500">Current stage</p>
                <div className="mt-1"><StageBadge stage={selected.stage} /></div>
              </div>
              <div className="rounded-lg border border-surface-line bg-surface p-3">
                <p className="text-[11px] uppercase text-slate-500">AI match</p>
                <div className="mt-1"><ScorePill value={selected.overallScore} /></div>
              </div>
              <div className="rounded-lg border border-surface-line bg-surface p-3">
                <p className="text-[11px] uppercase text-slate-500">Applied</p>
                <p className="mt-1 text-sm text-slate-300">{formatDateTime(selected.appliedAt)}</p>
              </div>
            </div>

            {selected.coverLetter && (
              <div>
                <p className="label">Cover letter</p>
                <p className="rounded-lg border border-surface-line bg-surface p-3 text-sm whitespace-pre-line text-slate-300">
                  {selected.coverLetter}
                </p>
              </div>
            )}

            {/* AI screening */}
            <div>
              <p className="label">AI resume screening</p>
              {!detail.analysis ? (
                <p className="text-sm text-slate-500">No analysis yet — run AI screening.</p>
              ) : detail.analysis.status === 'COMPLETED' ? (
                <div className="space-y-3 rounded-lg border border-surface-line bg-surface p-4">
                  <div className="flex flex-wrap gap-4 text-sm">
                    <p><span className="text-slate-500">Match:</span>{' '}
                      <strong className="text-white">{Number(detail.analysis.matchScore).toFixed(1)}</strong></p>
                    <p><span className="text-slate-500">ATS:</span>{' '}
                      <strong className="text-white">{Number(detail.analysis.atsScore).toFixed(1)}</strong></p>
                    <p><span className="text-slate-500">Model:</span>{' '}
                      <span className="text-slate-300">{detail.analysis.model}</span></p>
                  </div>
                  <p className="text-sm leading-relaxed text-slate-300">{detail.analysis.summary}</p>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div>
                      <p className="text-xs uppercase text-emerald-400">Strengths</p>
                      <ul className="mt-1 space-y-1 text-sm text-slate-300">
                        {detail.analysis.strengths.map((item) => (
                          <li key={item} className="flex gap-2"><span className="text-emerald-500">+</span>{item}</li>
                        ))}
                      </ul>
                    </div>
                    <div>
                      <p className="text-xs uppercase text-amber-400">Gaps</p>
                      <ul className="mt-1 space-y-1 text-sm text-slate-300">
                        {detail.analysis.gaps.map((item) => (
                          <li key={item} className="flex gap-2"><span className="text-amber-500">!</span>{item}</li>
                        ))}
                      </ul>
                    </div>
                  </div>
                  <div>
                    <p className="text-xs uppercase text-slate-500">Extracted skills</p>
                    <div className="mt-1 flex flex-wrap gap-1">
                      {detail.analysis.extractedSkills.map((skill) => (
                        <Badge key={skill} tone="green">{skill}</Badge>
                      ))}
                    </div>
                  </div>
                  <div>
                    <p className="text-xs uppercase text-slate-500">Missing</p>
                    <div className="mt-1 flex flex-wrap gap-1">
                      {detail.analysis.missingSkills.length === 0
                        ? <span className="text-sm text-slate-500">None</span>
                        : detail.analysis.missingSkills.map((skill) => (
                            <Badge key={skill} tone="amber">{skill}</Badge>
                          ))}
                    </div>
                  </div>
                  <div>
                    <p className="text-xs uppercase text-slate-500">Recommendations</p>
                    <ul className="mt-1 list-disc space-y-1 pl-5 text-sm text-slate-300">
                      {detail.analysis.recommendations.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                </div>
              ) : (
                <div className="rounded-lg border border-amber-900/50 bg-amber-950/30 p-3 text-sm text-amber-200">
                  <p className="font-medium">Screening {detail.analysis.status.toLowerCase()}</p>
                  <p className="mt-1 text-amber-300/80">{detail.analysis.error}</p>
                </div>
              )}
            </div>

            {/* interviews */}
            {detail.interviews.length > 0 && (
              <div>
                <p className="label">Interviews</p>
                <div className="space-y-2">
                  {detail.interviews.map((interview) => (
                    <div key={interview.id}
                         className="flex items-center justify-between rounded-lg border border-surface-line bg-surface px-3 py-2 text-sm">
                      <span className="text-slate-300">
                        Round {interview.round} · {interview.mode} · {formatDateTime(interview.scheduledAt)}
                      </span>
                      <Badge tone={interview.status === 'COMPLETED' ? 'green' : 'amber'}>{interview.status}</Badge>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* timeline */}
            <div>
              <p className="label">Timeline</p>
              <ol className="space-y-2">
                {detail.timeline.map((event) => (
                  <li key={event.id} className="flex items-start gap-3 text-sm">
                    <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-brand-500" />
                    <div>
                      <p className="text-slate-300">
                        {event.fromStage ? `${titleCase(event.fromStage)} → ` : ''}
                        <strong className="text-white">{titleCase(event.toStage)}</strong>
                        {event.changedBy && <span className="text-slate-500"> by {event.changedBy}</span>}
                      </p>
                      {event.note && <p className="text-xs text-slate-500">{event.note}</p>}
                      <p className="text-xs text-slate-600">{formatDateTime(event.createdAt)}</p>
                    </div>
                  </li>
                ))}
              </ol>
            </div>

            {/* stage move */}
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="next-stage">Move to</label>
                <select id="next-stage" className="input" value={nextStage}
                        onChange={(e) => setNextStage(e.target.value)}>
                  <option value="">Select stage…</option>
                  {STAGES.filter((stage) => stage !== selected.stage)
                    .map((stage) => <option key={stage} value={stage}>{titleCase(stage)}</option>)}
                </select>
              </div>
              <div>
                <label className="label" htmlFor="stage-note">Note</label>
                <input id="stage-note" className="input" placeholder="Why this change?"
                       value={note} onChange={(e) => setNote(e.target.value)} />
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
