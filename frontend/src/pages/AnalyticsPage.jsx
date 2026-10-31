import { useEffect, useState } from 'react'
import { apiGet } from '../lib/api.js'
import { EmptyState, ErrorBanner, PageHeader, ProgressBar, Spinner, StatCard } from '../components/ui.jsx'
import { titleCase } from '../lib/format.js'

export default function AnalyticsPage() {
  const [data, setData] = useState(null)
  const [sources, setSources] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([apiGet('/api/analytics/dashboard'), apiGet('/api/analytics/sources')])
      .then(([dashboard, sourceMap]) => {
        if (cancelled) return
        setData(dashboard)
        setSources(sourceMap)
      })
      .catch((err) => !cancelled && setError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  if (loading) return <Spinner label="Crunching numbers…" />
  if (error) return <ErrorBanner error={error} />
  if (!data) return <EmptyState title="No analytics yet" />

  const summary = data.summary
  const maxFunnel = Math.max(1, ...data.funnel.map((point) => point.count))
  const maxSkill = Math.max(1, ...data.topSkills.map((skill) => skill.jobCount))
  const maxSource = Math.max(1, ...Object.values(sources))

  return (
    <div>
      <PageHeader title="Analytics" subtitle="Where the pipeline is healthy — and where it stalls." />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Total applications" value={summary.totalApplications} accent="brand" />
        <StatCard label="Active pipeline" value={summary.activePipeline} hint="excluding hired/rejected" accent="sky" />
        <StatCard label="Offers out" value={summary.offers} hint={`${summary.hired} hired`} accent="green" />
        <StatCard
          label="Avg AI score"
          value={summary.averageScore ? Number(summary.averageScore).toFixed(1) : '—'}
          accent="amber"
        />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <section className="card p-5">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-400">
            Pipeline funnel
          </h2>
          <div className="space-y-3">
            {data.funnel.map((point) => (
              <ProgressBar key={point.stage} label={titleCase(point.stage)}
                           value={point.count} max={maxFunnel} right={String(point.count)} />
            ))}
          </div>
        </section>

        <section className="card p-5">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-400">
            Skill demand on open roles
          </h2>
          {data.topSkills.length === 0 ? (
            <EmptyState title="No open roles" />
          ) : (
            <div className="space-y-3">
              {data.topSkills.map((skill) => (
                <ProgressBar
                  key={skill.skillId}
                  label={
                    <span>
                      {skill.name}
                      {skill.requiredCount > 0 && (
                        <span className="ml-2 text-[10px] uppercase text-amber-400">
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

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <section className="card p-5">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-400">
            Applications by source
          </h2>
          {Object.keys(sources).length === 0 ? (
            <EmptyState title="No data yet" />
          ) : (
            <div className="space-y-3">
              {Object.entries(sources).map(([source, count]) => (
                <ProgressBar key={source} label={titleCase(source)}
                             value={count} max={maxSource} right={String(count)} />
              ))}
            </div>
          )}
        </section>

        <section className="card p-5">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-400">
            Workforce &amp; postings
          </h2>
          <div className="grid grid-cols-2 gap-4 text-sm">
            {[
              ['Open roles', summary.openJobs],
              ['Draft roles', summary.draftJobs],
              ['Closed roles', summary.closedJobs],
              ['Candidates', summary.totalCandidates],
              ['Recruiter team', summary.totalRecruiters],
              ['Scheduled interviews', summary.scheduledInterviews],
              ['Completed interviews', summary.completedInterviews],
              ['Rejected / withdrawn', summary.rejected],
            ].map(([label, value]) => (
              <div key={label} className="rounded-lg border border-surface-line bg-surface px-3 py-2.5">
                <p className="text-xs text-slate-500">{label}</p>
                <p className="text-xl font-semibold tabular-nums text-white">{value}</p>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}
