import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './context/AuthContext.jsx'
import Layout from './components/Layout.jsx'

import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import JobBoardPage from './pages/JobBoardPage.jsx'
import JobDetailPage from './pages/JobDetailPage.jsx'
import ManageJobsPage from './pages/ManageJobsPage.jsx'
import PipelinePage from './pages/PipelinePage.jsx'
import MyApplicationsPage from './pages/MyApplicationsPage.jsx'
import InterviewsPage from './pages/InterviewsPage.jsx'
import AnalyticsPage from './pages/AnalyticsPage.jsx'
import CandidatesPage from './pages/CandidatesPage.jsx'
import ProfilePage from './pages/ProfilePage.jsx'
import AdminUsersPage from './pages/AdminUsersPage.jsx'
import NotificationsPage from './pages/NotificationsPage.jsx'

/** Route guard: requires a session, optionally a set of roles. */
function Protected({ children, roles }) {
  const { user, booting, role } = useAuth()
  if (booting) return null
  if (!user) return <Navigate to="/login" replace />
  if (roles && !roles.includes(role)) return <Navigate to="/" replace />
  return children
}

function HomeRedirect() {
  const { user, booting, isStaff } = useAuth()
  if (booting) return null
  if (!user) return <Navigate to="/login" replace />
  return <Navigate to={isStaff ? '/dashboard' : '/jobs'} replace />
}

export default function App() {
  const { user, booting } = useAuth()
  if (booting) {
    return (
      <div className="grid min-h-screen place-items-center text-slate-500">Loading SmartHire…</div>
    )
  }

  return (
    <Routes>
      <Route
        path="/login"
        element={user ? <Navigate to="/" replace /> : <LoginPage />}
      />
      <Route
        path="/register"
        element={user ? <Navigate to="/" replace /> : <RegisterPage />}
      />

      <Route element={<Layout />}>
        <Route path="/" element={<HomeRedirect />} />

        <Route path="/dashboard" element={<Protected roles={['ADMIN', 'RECRUITER']}><DashboardPage /></Protected>} />
        <Route path="/pipeline" element={<Protected roles={['ADMIN', 'RECRUITER']}><PipelinePage /></Protected>} />
        <Route path="/analytics" element={<Protected roles={['ADMIN', 'RECRUITER']}><AnalyticsPage /></Protected>} />
        <Route path="/candidates" element={<Protected roles={['ADMIN', 'RECRUITER']}><CandidatesPage /></Protected>} />
        <Route path="/jobs/manage" element={<Protected roles={['ADMIN', 'RECRUITER']}><ManageJobsPage /></Protected>} />
        <Route path="/admin/users" element={<Protected roles={['ADMIN']}><AdminUsersPage /></Protected>} />

        <Route path="/jobs" element={<JobBoardPage />} />
        <Route path="/jobs/:id" element={<JobDetailPage />} />
        <Route path="/applications" element={<Protected roles={['CANDIDATE']}><MyApplicationsPage /></Protected>} />
        <Route path="/interviews" element={<Protected><InterviewsPage /></Protected>} />
        <Route path="/profile" element={<Protected roles={['CANDIDATE']}><ProfilePage /></Protected>} />
        <Route path="/settings" element={<Protected><ProfilePage /></Protected>} />
        <Route path="/notifications" element={<Protected><NotificationsPage /></Protected>} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
