import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiGet, apiPost } from '../lib/api.js'
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
import { formatDateTime, timeAgo } from '../lib/format.js'

export default function MyApplicationsPage() {
  const [applications, setApplications] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selected, setSelected] = useState(null)
  const [analysis, setAnalysis] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    apiGet('/api/applications/mine')
      .then(setApplications)
      .catch(setError)
      .finally(() => setLoading(false))
  }, [])

  useEffect(load, [load])

  async function open(application) {
    setSelected(application)
    setAnalysis(null)
    try {
      const history = await apiGet(`/api/applications/${application.id}/analysis`)
      setAnalysis(history[0] || null)
    } catch (err) {
      setError(err)
    }
  }

  async function withdraw() {
    if (!selected) return
    setBusy(true)
    try {
      await apiPost(`/api/applications/${selected.id}/withdraw`)
      setSelected(null)
      load()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const active = ['APPLIED', 'SCREENING', 'SHORTLISTED', 'INTERVIEW', 'OFFER']
  const openCount = applications.filter((app) => active.includes(app.stage)).length

  if (loading) return <Spinner label="Loading your applications…" />

  return (
    <div>
      <PageHeader
        title="My applications"
        subtitle={
          applications.length === 0
            ? 'You have not applied to any roles yet.'
            : `${openCount} active · ${applications.length} total`
        }
        actions={<Link to="/jobs" className="btn-primary">Browse open roles</Link>}
      />

      {error && <div className="mb-4"><ErrorBanner error={error} onDismiss={() => setError(null)} /></div>}

      {applications.length === 0 ? (
        <EmptyState
          title="No applications yet"
          description="Find a role you like and apply — your resume gets AI-screened automatically."
          action={<Link to="/jobs" className="btn-primary">Find jobs</Link>}
        />
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="table-base">
              <thead>
                <tr>
                  <th>Role</th>
                  <th>Stage</th>
                  <th>AI score</th>
                  <th>Applied</th>
                  <th className="text-right"></th>
                </tr>
              </thead>
              <tbody>
                {applications.map((application) => (
                  <tr key={application.id}>
                    <td>
                      <p className="font-medium text-slate-200">{application.job.title}</p>
                      <p className="text-xs text-slate-500">{application.job.location || '—'}</p>
                    </td>
                    <td><StageBadge stage={application.stage} /></td>
                    <td><ScorePill value={application.overallScore} /></td>
                    <td className="text-slate-500">{timeAgo(application.appliedAt)}</td>
                    <td className="text-right">
                      <button type="button" className="btn-secondary !px-2.5 !py-1 text-xs"
                              onClick={() => open(application)}>
                        View
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
        title={selected ? selected.job.title : ''}
        onClose={() => setSelected(null)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setSelected(null)}>Close</button>
            {selected && active.includes(selected.stage) && (
              <button type="button" className="btn-danger" onClick={withdraw} disabled={busy}>
                {busy ? 'Withdrawing…' : 'Withdraw application'}
              </button>
            )}
          </>
        }
      >
        {selected && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-4 text-sm">
              <StageBadge stage={selected.stage} />
              <span className="text-slate-400">
                Applied {formatDateTime(selected.appliedAt)}
              </span>
            </div>

            {selected.coverLetter && (
              <div>
                <p className="label">Your cover letter</p>
                <p className="rounded-lg border border-surface-line bg-surface p-3 text-sm whitespace-pre-line text-slate-300">
                  {selected.coverLetter}
                </p>
              </div>
            )}

            <div>
              <p className="label">AI screening result</p>
              {!analysis ? (
                <p className="text-sm text-slate-500">No screening on record.</p>
              ) : analysis.status === 'COMPLETED' ? (
                <div className="space-y-3 rounded-lg border border-surface-line bg-surface p-4 text-sm">
                  <div className="flex gap-5">
                    <p><span className="text-slate-500">Match:</span>{' '}
                      <strong className="text-white">{Number(analysis.matchScore).toFixed(1)}</strong></p>
                    <p><span className="text-slate-500">ATS:</span>{' '}
                      <strong className="text-white">{Number(analysis.atsScore).toFixed(1)}</strong></p>
                  </div>
                  <p className="text-slate-300">{analysis.summary}</p>
                  <div>
                    <p className="text-xs uppercase text-slate-500">Missing skills</p>
                    <div className="mt-1 flex flex-wrap gap-1">
                      {analysis.missingSkills.length === 0
                        ? <span className="text-slate-500">None — full coverage</span>
                        : analysis.missingSkills.map((skill) => <Badge key={skill} tone="amber">{skill}</Badge>)}
                    </div>
                  </div>
                  <div>
                    <p className="text-xs uppercase text-slate-500">Recommendations</p>
                    <ul className="mt-1 list-disc space-y-1 pl-5 text-slate-300">
                      {analysis.recommendations.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                </div>
              ) : (
                <div className="rounded-lg border border-amber-900/50 bg-amber-950/30 p-3 text-sm text-amber-200">
                  Screening {analysis.status.toLowerCase()}: {analysis.error}
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
