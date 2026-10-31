import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiGet, apiPost, apiPatch } from '../lib/api.js'
import { Badge, EmptyState, ErrorBanner, PageHeader, Spinner } from '../components/ui.jsx'
import { timeAgo, titleCase } from '../lib/format.js'

const TYPE_TONE = {
  APPLICATION: 'sky',
  STAGE: 'brand',
  INTERVIEW: 'amber',
  SYSTEM: 'slate',
}

export default function NotificationsPage() {
  const [items, setItems] = useState([])
  const [unread, setUnread] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    setLoading(true)
    Promise.all([apiGet('/api/notifications'), apiGet('/api/notifications/unread-count')])
      .then(([list, counts]) => {
        setItems(list)
        setUnread(counts.unread || 0)
      })
      .catch(setError)
      .finally(() => setLoading(false))
  }, [])

  useEffect(load, [load])

  async function markRead(id) {
    try {
      await apiPatch(`/api/notifications/${id}/read`)
      load()
    } catch (err) {
      setError(err)
    }
  }

  async function markAll() {
    try {
      await apiPost('/api/notifications/read-all')
      load()
    } catch (err) {
      setError(err)
    }
  }

  if (loading) return <Spinner label="Loading notifications…" />

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Notifications"
        subtitle={unread > 0 ? `${unread} unread` : 'You are all caught up'}
        actions={
          unread > 0 ? (
            <button type="button" className="btn-secondary" onClick={markAll}>
              Mark all read
            </button>
          ) : null
        }
      />

      {error && <ErrorBanner error={error} onDismiss={() => setError(null)} />}

      {items.length === 0 ? (
        <EmptyState title="No notifications" description="Pipeline activity will show up here." />
      ) : (
        <ul className="space-y-2">
          {items.map((notification) => (
            <li
              key={notification.id}
              className={`card flex items-start gap-3 p-4 ${notification.read ? 'opacity-60' : ''}`}
            >
              <span
                className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${notification.read ? 'bg-slate-700' : 'bg-brand-500'}`}
              />
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium text-slate-200">{notification.title}</p>
                  <Badge tone={TYPE_TONE[notification.type] || 'slate'}>{titleCase(notification.type)}</Badge>
                </div>
                {notification.body && (
                  <p className="mt-0.5 text-sm text-slate-400">{notification.body}</p>
                )}
                <div className="mt-1 flex items-center gap-3 text-xs text-slate-600">
                  <span>{timeAgo(notification.createdAt)}</span>
                  {notification.link && (
                    <Link to={notification.link} className="text-brand-400 hover:text-brand-300">
                      Open →
                    </Link>
                  )}
                </div>
              </div>
              {!notification.read && (
                <button
                  type="button"
                  className="btn-secondary !px-2.5 !py-1 text-xs"
                  onClick={() => markRead(notification.id)}
                >
                  Mark read
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
