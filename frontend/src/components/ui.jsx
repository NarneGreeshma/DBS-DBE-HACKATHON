/** Shared dark-theme UI primitives used across every screen. */

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="flex items-center justify-center gap-3 py-14 text-slate-400">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-600 border-t-brand-400" />
      <span className="text-sm">{label}</span>
    </div>
  )
}

export function ErrorBanner({ error, onDismiss }) {
  if (!error) return null
  const fields = error.fieldErrors && Object.keys(error.fieldErrors)
  return (
    <div className="rounded-lg border border-rose-900/60 bg-rose-950/40 px-4 py-3 text-sm text-rose-200">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="font-medium">Something went wrong</p>
          <p className="mt-0.5 text-rose-300/90">{error.message}</p>
          {fields?.length > 0 && (
            <ul className="mt-2 list-disc space-y-0.5 pl-5 text-rose-300/80">
              {fields.map((key) => (
                <li key={key}>
                  <span className="font-mono text-xs">{key}</span>: {error.fieldErrors[key]}
                </li>
              ))}
            </ul>
          )}
        </div>
        {onDismiss && (
          <button type="button" onClick={onDismiss} className="text-rose-300/70 hover:text-rose-200">
            ✕
          </button>
        )}
      </div>
    </div>
  )
}

export function EmptyState({ title, description, action }) {
  return (
    <div className="rounded-xl border border-dashed border-surface-line px-6 py-14 text-center">
      <p className="text-sm font-medium text-slate-300">{title}</p>
      {description && <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

const TONES = {
  slate: 'bg-slate-800 text-slate-300 ring-slate-700',
  brand: 'bg-brand-500/15 text-brand-300 ring-brand-500/30',
  green: 'bg-emerald-500/15 text-emerald-300 ring-emerald-500/30',
  amber: 'bg-amber-500/15 text-amber-300 ring-amber-500/30',
  rose: 'bg-rose-500/15 text-rose-300 ring-rose-500/30',
  sky: 'bg-sky-500/15 text-sky-300 ring-sky-500/30',
  violet: 'bg-violet-500/15 text-violet-300 ring-violet-500/30',
}

export function Badge({ tone = 'slate', children, className = '' }) {
  return (
    <span className={`badge ring-1 ring-inset ${TONES[tone] || TONES.slate} ${className}`}>
      {children}
    </span>
  )
}

/** Colour-codes a pipeline stage so the table reads at a glance. */
export function StageBadge({ stage }) {
  const tone = {
    APPLIED: 'sky',
    SCREENING: 'brand',
    SHORTLISTED: 'violet',
    INTERVIEW: 'amber',
    OFFER: 'green',
    HIRED: 'green',
    REJECTED: 'rose',
    WITHDRAWN: 'slate',
  }[stage]
  return <Badge tone={tone}>{String(stage || '').replace('_', ' ')}</Badge>
}

export function JobStatusBadge({ status }) {
  const tone = { OPEN: 'green', DRAFT: 'slate', ON_HOLD: 'amber', CLOSED: 'rose' }[status]
  return <Badge tone={tone}>{String(status || '').replace('_', ' ')}</Badge>
}

export function ScorePill({ value }) {
  if (value === null || value === undefined) {
    return <span className="text-xs text-slate-500">not scored</span>
  }
  const score = Number(value)
  const tone =
    score >= 75
      ? 'text-emerald-300 bg-emerald-500/10 ring-emerald-500/30'
      : score >= 55
        ? 'text-amber-300 bg-amber-500/10 ring-amber-500/30'
        : 'text-rose-300 bg-rose-500/10 ring-rose-500/30'
  return (
    <span className={`badge ring-1 ring-inset tabular-nums ${tone}`}>{score.toFixed(1)}</span>
  )
}

export function StatCard({ label, value, hint, accent = 'brand' }) {
  const accents = {
    brand: 'from-brand-500/20 to-brand-500/0 text-brand-300',
    green: 'from-emerald-500/20 to-emerald-500/0 text-emerald-300',
    amber: 'from-amber-500/20 to-amber-500/0 text-amber-300',
    violet: 'from-violet-500/20 to-violet-500/0 text-violet-300',
    sky: 'from-sky-500/20 to-sky-500/0 text-sky-300',
    rose: 'from-rose-500/20 to-rose-500/0 text-rose-300',
  }
  return (
    <div className="card relative overflow-hidden p-4">
      <div className={`pointer-events-none absolute inset-0 bg-gradient-to-br ${accents[accent]}`} />
      <div className="relative">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</p>
        <p className="mt-2 text-3xl font-semibold tabular-nums text-white">{value}</p>
        {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      </div>
    </div>
  )
}

export function ProgressBar({ value, max = 100, label, right }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0
  return (
    <div className="space-y-1.5">
      {(label || right) && (
        <div className="flex items-baseline justify-between text-xs">
          <span className="truncate text-slate-300">{label}</span>
          <span className="tabular-nums text-slate-500">{right ?? `${pct}%`}</span>
        </div>
      )}
      <div className="bar-track">
        <div className="bar-fill" style={{ width: `${pct}%` }} />
      </div>
    </div>
  )
}

export function Modal({ open, title, onClose, children, footer, wide = false }) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/70 p-4 backdrop-blur-sm sm:p-8">
      <div
        className={`card w-full ${wide ? 'max-w-3xl' : 'max-w-lg'} p-5`}
        role="dialog"
        aria-modal="true"
      >
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-base font-semibold text-white">{title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-1 text-slate-500 hover:bg-white/5 hover:text-slate-200"
            aria-label="Close"
          >
            ✕
          </button>
        </div>
        {children}
        {footer && <div className="mt-5 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  )
}

export function Field({ label, error, hint, children }) {
  return (
    <div>
      <label className="label">{label}</label>
      {children}
      {hint && !error && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      {error && <p className="mt-1 text-xs text-rose-400">{error}</p>}
    </div>
  )
}

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 className="text-xl font-semibold tracking-tight text-white">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

export function Tabs({ tabs, active, onChange }) {
  return (
    <div className="flex gap-1 overflow-x-auto rounded-lg border border-surface-line bg-surface-raised p-1">
      {tabs.map((tab) => (
        <button
          key={tab.value}
          type="button"
          onClick={() => onChange(tab.value)}
          className={`whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium transition ${
            active === tab.value
              ? 'bg-surface-overlay text-white shadow'
              : 'text-slate-400 hover:text-slate-200'
          }`}
        >
          {tab.label}
          {tab.count !== undefined && (
            <span className="ml-1.5 text-xs text-slate-500">({tab.count})</span>
          )}
        </button>
      ))}
    </div>
  )
}
