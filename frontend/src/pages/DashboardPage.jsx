import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiGet } from '../lib/api.js'
import { ErrorBanner, EmptyState, PageHeader, ProgressBar, ScorePill, Spinner, StageBadge, StatCard } from '../components/ui.jsx'
import { timeAgo, titleCase } from '../lib/format.js'

export default function DashboardPage() {
  const [data, setData] = useState(null)
  const [recent, setRecent] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    Promise.all([
      apiGet('/api/analytics/dashboard'),
      apiGet('/api/analytics/recent-applications?limit=8'),
    ])
      .then(([dashboard, applications]) => {
        if (cancelled) return
        setData(dashboard)
        setRecent(applications)
      })
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  if (loading) return <Spinner label="Loading dashboard…" />
  if (error) return <ErrorBanner error={error} />

  const summary = data.summary
  const funnel = data.funnel
  const maxFunnel = Math.max(1, ...funnel.map((point) => point.count))
  const maxSkill = Math.max(1, ...data.topSkills.map((skill) => skill.jobCount))

  return (
    <div>
      <PageHeader
        title="Dashboard"
        subtitle="Hiring health across every open role."
        actions={
          <>
            <Link to="/jobs/manage" className="btn-secondary">Manage jobs</Link>
            <Link to="/pipeline" className="btn-primary">Open pipeline</Link>
          </>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Open roles" value={summary.openJobs} hint={`${summary.totalJobs} total postings`} accent="brand" />
        <StatCard label="Applications" value={summary.totalApplications} hint={`${summary.activePipeline} in flight`} accent="sky" />
        <StatCard label="In interview" value={funnel.find((p) => p.stage === 'INTERVIEW')?.count ?? 0} hint={`${summary.scheduledInterviews} interviews scheduled`} accent="amber" />
        <StatCard label="Hired" value={summary.hired} hint={`${summary.offers} offer(s) out`} accent="green" />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <section className="card p-5">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
              Pipeline funnel
            </h2>
            <Link to="/analytics" className="text-xs text-brand-400 hover:text-brand-300">
              Full analytics →
            </Link>
          </div>
          <div className="space-y-3">
            {funnel.map((point) => (
              <ProgressBar
                key={point.stage}
                label={titleCase(point.stage)}
                value={point.count}
                max={maxFunnel}
                right={`${point.count}`}
              />
            ))}
          </div>
        </section>

        <section className="card p-5">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
              Skill demand (open roles)
            </h2>
            <Link to="/analytics" className="text-xs text-brand-400 hover:text-brand-300">
              Details →
            </Link>
          </div>
          {data.topSkills.length === 0 ? (
            <EmptyState title="No skills published yet" />
          ) : (
            <div className="space-y-3">
              {data.topSkills.slice(0, 8).map((skill) => (
                <ProgressBar
                  key={skill.skillId}
                  label={
                    <span>
                      {skill.name}{' '}
                      {skill.requiredCount > 0 && (
                        <span className="ml-1 text-[10px] uppercase text-amber-400">
                          {skill.requiredCount} required
                        </span>
                      )}
                    </span>
                  }
                  value={skill.jobCount}
                  max={maxSkill}
                  right={`${skill.jobCount} job${skill.jobCount === 1 ? '' : 's'}`}
                />
              ))}
            </div>
          )}
        </section>
      </div>

      <section className="card mt-6 overflow-hidden">
        <div className="flex items-center justify-between border-b border-surface-line px-5 py-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
            Latest applications
          </h2>
          <Link to="/pipeline" className="text-xs text-brand-400 hover:text-brand-300">
            View all →
          </Link>
        </div>
        {recent.length === 0 ? (
          <EmptyState title="No applications yet" description="Post a role and share it to get started." />
        ) : (
          <div className="overflow-x-auto">
            <table className="table-base">
              <thead>
                <tr>
                  <th>Candidate</th>
                  <th>Role</th>
                  <th>Stage</th>
                  <th>AI score</th>
                  <th>Applied</th>
                </tr>
              </thead>
              <tbody>
                {recent.map((application) => (
                  <tr key={application.id}>
                    <td>
                      <p className="font-medium text-slate-200">{application.candidate.fullName}</p>
                      <p className="text-xs text-slate-500">{application.candidate.email}</p>
                    </td>
                    <td>{application.job.title}</td>
                    <td><StageBadge stage={application.stage} /></td>
                    <td><ScorePill value={application.overallScore} /></td>
                    <td className="text-slate-500">{timeAgo(application.appliedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="mt-6 grid gap-4 sm:grid-cols-3">
        <StatCard label="Total candidates" value={summary.totalCandidates} accent="violet" />
        <StatCard label="Team" value={summary.totalRecruiters} hint="recruiters + admins" accent="sky" />
        <StatCard
          label="Avg. AI score"
          value={summary.averageScore ? Number(summary.averageScore).toFixed(1) : '—'}
          hint="across scored applications"
          accent="amber"
        />
      </section>
    </div>
  )
}
