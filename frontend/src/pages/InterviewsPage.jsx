import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext.jsx'
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
import { formatDateTime } from '../lib/format.js'

const STATUS_TONE = {
  SCHEDULED: 'amber',
  COMPLETED: 'green',
  CANCELLED: 'rose',
  NO_SHOW: 'rose',
}

export default function InterviewsPage() {
  const { isStaff } = useAuth()
  const [interviews, setInterviews] = useState([])
  const [candidates, setCandidates] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)

  const [scheduleOpen, setScheduleOpen] = useState(false)
  const [completing, setCompleting] = useState(null)
  const [busy, setBusy] = useState(false)

  const [form, setForm] = useState({
    applicationId: '',
    round: 1,
    mode: 'ONLINE',
    date: '',
    time: '',
    durationMinutes: 45,
    meetingLink: '',
  })
  const [feedback, setFeedback] = useState({ status: 'COMPLETED', feedback: '', rating: 5 })

  const load = useCallback(() => {
    setLoading(true)
    Promise.all([
      apiGet('/api/interviews'),
      isStaff ? apiGet('/api/applications?size=100') : Promise.resolve([]),
    ])
      .then(([list, applications]) => {
        setInterviews(list)
        setCandidates(applications.content || [])
      })
      .catch(setError)
      .finally(() => setLoading(false))
  }, [isStaff])

  useEffect(load, [load])

  async function schedule(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await apiPost('/api/interviews', {
        applicationId: Number(form.applicationId),
        round: Number(form.round),
        mode: form.mode,
        scheduledAt: new Date(`${form.date}T${form.time}:00`).toISOString(),
        durationMinutes: Number(form.durationMinutes),
        meetingLink: form.meetingLink || null,
      })
      setScheduleOpen(false)
      setNotice('Interview scheduled')
      load()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  async function saveFeedback(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await apiPatch(`/api/interviews/${completing.id}`, {
        status: feedback.status,
        feedback: feedback.feedback || null,
        rating: Number(feedback.rating),
      })
      setCompleting(null)
      setNotice('Interview updated')
      load()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Spinner label="Loading interviews…" />

  return (
    <div>
      <PageHeader
        title="Interviews"
        subtitle={isStaff ? 'Schedule rounds and record feedback.' : 'Your upcoming and past interviews.'}
        actions={
          isStaff && (
            <button type="button" className="btn-primary" onClick={() => setScheduleOpen(true)}>
              + Schedule interview
            </button>
          )
        }
      />

      {notice && (
        <div className="mb-4 rounded-lg border border-emerald-900/50 bg-emerald-950/40 px-4 py-3 text-sm text-emerald-200">
          {notice}
          <button type="button" className="ml-3 text-emerald-400" onClick={() => setNotice(null)}>✕</button>
        </div>
      )}
      {error && <div className="mb-4"><ErrorBanner error={error} onDismiss={() => setError(null)} /></div>}

      {interviews.length === 0 ? (
        <EmptyState
          title="No interviews scheduled"
          description={isStaff
            ? 'Move a candidate to the interview stage, then book a round here.'
            : 'You will see scheduled rounds here once a recruiter books them.'}
        />
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="table-base">
              <thead>
                <tr>
                  <th>Candidate</th>
                  <th>Role</th>
                  <th>Round</th>
                  <th>When</th>
                  <th>Mode</th>
                  <th>Status</th>
                  <th>Rating</th>
                  {isStaff && <th className="text-right"></th>}
                </tr>
              </thead>
              <tbody>
                {interviews.map((interview) => (
                  <tr key={interview.id}>
                    <td>
                      <p className="font-medium text-slate-200">{interview.candidateName}</p>
                      <p className="text-xs text-slate-500">{interview.candidateEmail}</p>
                    </td>
                    <td>{interview.jobTitle}</td>
                    <td className="tabular-nums">#{interview.round}</td>
                    <td className="text-slate-400">{formatDateTime(interview.scheduledAt)}</td>
                    <td><Badge>{interview.mode}</Badge></td>
                    <td><Badge tone={STATUS_TONE[interview.status]}>{interview.status}</Badge></td>
                    <td>{interview.rating ? `${interview.rating}/5` : '—'}</td>
                    {isStaff && (
                      <td className="text-right">
                        <button
                          type="button"
                          className="btn-secondary !px-2.5 !py-1 text-xs"
                          onClick={() => {
                            setCompleting(interview)
                            setFeedback({
                              status: 'COMPLETED',
                              feedback: interview.feedback || '',
                              rating: interview.rating || 5,
                            })
                          }}
                        >
                          {interview.status === 'SCHEDULED' ? 'Record result' : 'View'}
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* schedule */}
      <Modal open={scheduleOpen} title="Schedule an interview" onClose={() => setScheduleOpen(false)}>
        <form onSubmit={schedule} className="space-y-4">
          <Field label="Application">
            <select className="input" value={form.applicationId} required
                    onChange={(e) => setForm({ ...form, applicationId: e.target.value })}>
              <option value="">Select a candidate…</option>
              {candidates.map((application) => (
                <option key={application.id} value={application.id}>
                  {application.candidate.fullName} — {application.job.title}
                </option>
              ))}
            </select>
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Date">
              <input className="input" type="date" required value={form.date}
                     onChange={(e) => setForm({ ...form, date: e.target.value })} />
            </Field>
            <Field label="Time">
              <input className="input" type="time" required value={form.time}
                     onChange={(e) => setForm({ ...form, time: e.target.value })} />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Round">
              <input className="input" type="number" min="1" max="10" value={form.round}
                     onChange={(e) => setForm({ ...form, round: e.target.value })} />
            </Field>
            <Field label="Mode">
              <select className="input" value={form.mode}
                      onChange={(e) => setForm({ ...form, mode: e.target.value })}>
                <option value="ONLINE">Online</option>
                <option value="PHONE">Phone</option>
                <option value="ONSITE">Onsite</option>
              </select>
            </Field>
            <Field label="Minutes">
              <input className="input" type="number" min="5" max="600" step="5"
                     value={form.durationMinutes}
                     onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })} />
            </Field>
          </div>
          <Field label="Meeting link (optional)">
            <input className="input" placeholder="https://meet…"
                   value={form.meetingLink}
                   onChange={(e) => setForm({ ...form, meetingLink: e.target.value })} />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setScheduleOpen(false)}>Cancel</button>
            <button className="btn-primary" disabled={busy}>{busy ? 'Scheduling…' : 'Schedule'}</button>
          </div>
        </form>
      </Modal>

      {/* result */}
      <Modal
        open={Boolean(completing)}
        title={completing ? `Round ${completing.round} · ${completing.candidateName}` : ''}
        onClose={() => setCompleting(null)}
      >
        <form onSubmit={saveFeedback} className="space-y-4">
          <div>
            <p className="label">Scheduled</p>
            <p className="text-sm text-slate-300">{formatDateTime(completing?.scheduledAt)}</p>
          </div>
          <Field label="Status">
            <select className="input" value={feedback.status}
                    onChange={(e) => setFeedback({ ...feedback, status: e.target.value })}>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
              <option value="NO_SHOW">No show</option>
            </select>
          </Field>
          <Field label="Feedback">
            <textarea className="input min-h-24" placeholder="What did the interviewer observe?"
                      value={feedback.feedback}
                      onChange={(e) => setFeedback({ ...feedback, feedback: e.target.value })} />
          </Field>
          <Field label={`Rating: ${feedback.rating}/5`}>
            <input className="w-full accent-brand-500" type="range" min="1" max="5"
                   value={feedback.rating}
                   onChange={(e) => setFeedback({ ...feedback, rating: e.target.value })} />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setCompleting(null)}>Cancel</button>
            <button className="btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save result'}</button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
