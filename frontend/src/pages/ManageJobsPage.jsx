import { useCallback, useEffect, useState } from 'react'
import { apiGet, apiPatch, apiPost, apiPut } from '../lib/api.js'
import {
  Badge,
  ErrorBanner,
  JobStatusBadge,
  Modal,
  PageHeader,
  Spinner,
  EmptyState,
  Field,
} from '../components/ui.jsx'
import { formatDate } from '../lib/format.js'

const EMPTY = {
  title: '',
  description: '',
  requirements: '',
  responsibilities: '',
  location: '',
  workplaceType: 'ONSITE',
  employmentType: 'FULL_TIME',
  experienceLevel: 'MID',
  minYears: '',
  maxYears: '',
  openings: 1,
  skills: [],
}

export default function ManageJobsPage() {
  const [jobs, setJobs] = useState([])
  const [skills, setSkills] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [editing, setEditing] = useState(null) // null | job object
  const [form, setForm] = useState(EMPTY)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState(null)

  const load = useCallback(() => {
    setLoading(true)
    Promise.all([apiGet('/api/jobs?size=100'), apiGet('/api/skills')])
      .then(([jobPage, skillList]) => {
        setJobs(jobPage.content)
        setSkills(skillList)
      })
      .catch(setError)
      .finally(() => setLoading(false))
  }, [])

  useEffect(load, [load])

  function openCreate() {
    setForm({ ...EMPTY, skills: [] })
    setEditing('new')
  }

  function openEdit(job) {
    setForm({
      title: job.title,
      description: job.description,
      requirements: job.requirements || '',
      responsibilities: job.responsibilities || '',
      location: job.location || '',
      workplaceType: job.workplaceType,
      employmentType: job.employmentType,
      experienceLevel: job.experienceLevel,
      minYears: job.minYears ?? '',
      maxYears: job.maxYears ?? '',
      openings: job.openings,
      skills: job.skills.map((skill) => ({
        skillId: skill.id,
        name: skill.name,
        weight: skill.weight,
        required: skill.required,
      })),
    })
    setEditing(job)
  }

  async function save(event) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    const payload = {
      ...form,
      minYears: form.minYears === '' ? null : Number(form.minYears),
      maxYears: form.maxYears === '' ? null : Number(form.maxYears),
      openings: Number(form.openings) || 1,
      skills: form.skills,
    }
    try {
      if (editing === 'new') {
        const created = await apiPost('/api/jobs', payload)
        setNotice(`Draft "${created.title}" created`)
      } else {
        await apiPut(`/api/jobs/${editing.id}`, payload)
        setNotice(`Updated "${payload.title}"`)
      }
      setEditing(null)
      load()
    } catch (err) {
      setError(err)
    } finally {
      setSaving(false)
    }
  }

  async function changeStatus(job, status) {
    setError(null)
    try {
      await apiPatch(`/api/jobs/${job.id}/status`, { status })
      setNotice(`${job.title} → ${status}`)
      load()
    } catch (err) {
      setError(err)
    }
  }

  function toggleSkill(skillId, name) {
    setForm((current) => {
      const exists = current.skills.some((skill) => skill.skillId === skillId)
      return {
        ...current,
        skills: exists
          ? current.skills.filter((skill) => skill.skillId !== skillId)
          : [...current.skills, { skillId, name, weight: 1, required: false }],
      }
    })
  }

  if (loading) return <Spinner label="Loading postings…" />

  return (
    <div>
      <PageHeader
        title="Job postings"
        subtitle="Draft, publish and close roles."
        actions={
          <button type="button" className="btn-primary" onClick={openCreate}>
            + New job
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

      {jobs.length === 0 ? (
        <EmptyState
          title="No job postings yet"
          description="Create your first draft to start hiring."
          action={<button type="button" className="btn-primary" onClick={openCreate}>+ New job</button>}
        />
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="table-base">
              <thead>
                <tr>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Skills</th>
                  <th>Applicants</th>
                  <th>Created</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.id}>
                    <td>
                      <p className="font-medium text-slate-200">{job.title}</p>
                      <p className="text-xs text-slate-500">{job.location || 'Unspecified'} · {job.openings} opening(s)</p>
                    </td>
                    <td><JobStatusBadge status={job.status} /></td>
                    <td>
                      <div className="flex flex-wrap gap-1">
                        {job.skills.slice(0, 3).map((skill) => (
                          <Badge key={skill.id} tone={skill.required ? 'brand' : 'slate'}>{skill.name}</Badge>
                        ))}
                        {job.skills.length > 3 && (
                          <span className="text-xs text-slate-500">+{job.skills.length - 3}</span>
                        )}
                      </div>
                    </td>
                    <td className="tabular-nums">{job.applicationCount ?? 0}</td>
                    <td className="text-slate-500">{formatDate(job.createdAt)}</td>
                    <td>
                      <div className="flex justify-end gap-2">
                        <button type="button" className="btn-secondary !px-2.5 !py-1 text-xs" onClick={() => openEdit(job)}>
                          Edit
                        </button>
                        {job.status === 'DRAFT' && (
                          <button type="button" className="btn-primary !px-2.5 !py-1 text-xs" onClick={() => changeStatus(job, 'OPEN')}>
                            Publish
                          </button>
                        )}
                        {job.status === 'OPEN' && (
                          <>
                            <button type="button" className="btn-secondary !px-2.5 !py-1 text-xs" onClick={() => changeStatus(job, 'ON_HOLD')}>
                              Hold
                            </button>
                            <button type="button" className="btn-danger !px-2.5 !py-1 text-xs" onClick={() => changeStatus(job, 'CLOSED')}>
                              Close
                            </button>
                          </>
                        )}
                        {job.status === 'ON_HOLD' && (
                          <button type="button" className="btn-primary !px-2.5 !py-1 text-xs" onClick={() => changeStatus(job, 'OPEN')}>
                            Resume
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal
        open={editing !== null}
        wide
        title={editing === 'new' ? 'Create job draft' : 'Edit job'}
        onClose={() => setEditing(null)}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setEditing(null)}>
              Cancel
            </button>
            <button type="submit" form="job-form" className="btn-primary" disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          </>
        }
      >
        <form id="job-form" onSubmit={save} className="space-y-4">
          <Field label="Title">
            <input className="input" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required />
          </Field>
          <Field label="Description">
            <textarea className="input min-h-24" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} required />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Requirements">
              <textarea className="input min-h-20" value={form.requirements} onChange={(e) => setForm({ ...form, requirements: e.target.value })} />
            </Field>
            <Field label="Responsibilities">
              <textarea className="input min-h-20" value={form.responsibilities} onChange={(e) => setForm({ ...form, responsibilities: e.target.value })} />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Location">
              <input className="input" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} />
            </Field>
            <Field label="Workplace">
              <select className="input" value={form.workplaceType} onChange={(e) => setForm({ ...form, workplaceType: e.target.value })}>
                <option value="ONSITE">Onsite</option>
                <option value="HYBRID">Hybrid</option>
                <option value="REMOTE">Remote</option>
              </select>
            </Field>
            <Field label="Level">
              <select className="input" value={form.experienceLevel} onChange={(e) => setForm({ ...form, experienceLevel: e.target.value })}>
                {['JUNIOR', 'MID', 'SENIOR', 'LEAD', 'EXEC'].map((level) => (
                  <option key={level} value={level}>{level}</option>
                ))}
              </select>
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-4">
            <Field label="Min years">
              <input className="input" type="number" min="0" value={form.minYears} onChange={(e) => setForm({ ...form, minYears: e.target.value })} />
            </Field>
            <Field label="Max years">
              <input className="input" type="number" min="0" value={form.maxYears} onChange={(e) => setForm({ ...form, maxYears: e.target.value })} />
            </Field>
            <Field label="Openings">
              <input className="input" type="number" min="1" value={form.openings} onChange={(e) => setForm({ ...form, openings: e.target.value })} />
            </Field>
            <Field label="Employment">
              <select className="input" value={form.employmentType} onChange={(e) => setForm({ ...form, employmentType: e.target.value })}>
                {['FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP'].map((type) => (
                  <option key={type} value={type}>{type.replace('_', ' ')}</option>
                ))}
              </select>
            </Field>
          </div>

          <div>
            <p className="label">Skills ({form.skills.length} selected)</p>
            <div className="max-h-44 space-y-1 overflow-y-auto rounded-lg border border-surface-line bg-surface p-2">
              {skills.map((skill) => {
                const selected = form.skills.find((item) => item.skillId === skill.id)
                return (
                  <label key={skill.id} className="flex cursor-pointer items-center gap-3 rounded px-2 py-1.5 hover:bg-white/5">
                    <input
                      type="checkbox"
                      checked={Boolean(selected)}
                      onChange={() => toggleSkill(skill.id, skill.name)}
                      className="h-4 w-4 accent-brand-500"
                    />
                    <span className="flex-1 text-sm text-slate-300">{skill.name}</span>
                    <span className="text-[10px] uppercase text-slate-600">{skill.category}</span>
                    {selected && (
                      <span className="flex items-center gap-1">
                        <select
                          className="rounded border border-surface-line bg-surface-overlay px-1 py-0.5 text-xs"
                          value={selected.weight}
                          onChange={(e) => setForm({
                            ...form,
                            skills: form.skills.map((item) =>
                              item.skillId === skill.id ? { ...item, weight: Number(e.target.value) } : item),
                          })}
                        >
                          <option value={1}>nice to have</option>
                          <option value={2}>important</option>
                          <option value={3}>key</option>
                        </select>
                        <input
                          type="checkbox"
                          title="required"
                          checked={selected.required}
                          onChange={(e) => setForm({
                            ...form,
                            skills: form.skills.map((item) =>
                              item.skillId === skill.id ? { ...item, required: e.target.checked } : item),
                          })}
                          className="h-4 w-4 accent-amber-500"
                        />
                      </span>
                    )}
                  </label>
                )
              })}
            </div>
            <p className="mt-1 text-xs text-slate-500">Tick the amber box to mark a skill as required.</p>
          </div>
        </form>
      </Modal>
    </div>
  )
}
