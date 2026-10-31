/** Small formatting helpers shared by every screen. */

export function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

export function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function timeAgo(value) {
  if (!value) return '—'
  const seconds = Math.round((Date.now() - new Date(value).getTime()) / 1000)
  if (Number.isNaN(seconds)) return '—'
  const units = [
    [60, 'second'],
    [60, 'minute'],
    [24, 'hour'],
    [7, 'day'],
    [4.345, 'week'],
    [12, 'month'],
    [Number.POSITIVE_INFINITY, 'year'],
  ]
  let amount = seconds
  for (const [step, name] of units) {
    if (Math.abs(amount) < step) {
      const rounded = Math.round(amount)
      return `${rounded} ${name}${Math.abs(rounded) === 1 ? '' : 's'} ago`
    }
    amount /= step
  }
  return 'just now'
}

export function salaryRange(job) {
  if (!job.minSalary && !job.maxSalary) return null
  const fmt = (n) => Number(n).toLocaleString()
  const currency = job.currency || 'USD'
  if (job.minSalary && job.maxSalary) {
    return `${currency} ${fmt(job.minSalary)} – ${fmt(job.maxSalary)}`
  }
  return `${currency} ${fmt(job.minSalary || job.maxSalary)}+`
}

export function titleCase(value) {
  if (!value) return ''
  return String(value)
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function initials(name) {
  if (!name) return '?'
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('')
}
