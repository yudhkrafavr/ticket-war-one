import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AdminLayout } from './layouts/AdminLayout'
import { AdminLoginPage } from './pages/admin/AdminLoginPage'
import { ParticipantLayout } from './layouts/ParticipantLayout'
import { DashboardPage } from './pages/admin/DashboardPage'
import { EventsPage } from './pages/admin/EventsPage'
import { EventDetailPage } from './pages/admin/EventDetailPage'
import { WarSessionsPage } from './pages/admin/WarSessionsPage'
import { WarSessionFormPage } from './pages/admin/WarSessionFormPage'
import { WarSessionDetailPage } from './pages/admin/WarSessionDetailPage'
import { TriviaManagementPage } from './pages/admin/TriviaManagementPage'
import { ParticipantsPage } from './pages/admin/ParticipantsPage'
import { LeaderboardIndexPage, LeaderboardPage } from './pages/admin/LeaderboardPage'
import { RegistrationPage } from './pages/participant/RegistrationPage'
import { WaitingRoomPage } from './pages/participant/WaitingRoomPage'
import { ChallengePage } from './pages/participant/ChallengePage'
import { ResultPage } from './pages/participant/ResultPage'
import { PublicEventsPage } from './pages/participant/PublicEventsPage'
import { ParticipantAccessPage } from './pages/participant/ParticipantAccessPage'
import { BookingClaimPage } from './pages/participant/BookingClaimPage'
import { useParticipantAuth } from './contexts/ParticipantAuthContext'
import { useAdminAuth } from './contexts/AdminAuthContext'
import { LoadingState } from './components/ui/Feedback'

function ParticipantGate() {
  const { token, isValidating } = useParticipantAuth()
  if (isValidating) return <LoadingState label="Checking your participant access…" />
  if (!token) {
    return (
      <section className="participant-card access-card">
        <div className="eyebrow">Participant access</div>
        <h1>Open your registration session</h1>
        <p>Open your competition’s registration page first. After you register, this tab keeps your participant session active.</p>
      </section>
    )
  }
  return <Outlet />
}

function AdminGate() {
  const { isAuthenticated, isChecking } = useAdminAuth()
  if (isChecking) return <LoadingState label="Checking admin sign-in…" />
  if (!isAuthenticated) return <Navigate to="/admin/login" replace />
  return <Outlet />
}

export function App() {
  return (
    <Routes>
      <Route path="/admin/login" element={<AdminLoginPage />} />
      <Route element={<AdminGate />}>
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="events" element={<EventsPage />} />
        <Route path="events/:id" element={<EventDetailPage />} />
        <Route path="war-sessions" element={<WarSessionsPage />} />
        <Route path="war-sessions/new" element={<WarSessionFormPage />} />
        <Route path="war-sessions/:id/edit" element={<WarSessionFormPage />} />
        <Route path="war-sessions/:id" element={<WarSessionDetailPage />} />
        <Route path="war-sessions/:id/questions" element={<TriviaManagementPage />} />
        <Route path="war-sessions/:id/participants" element={<ParticipantsPage />} />
        <Route path="war-sessions/:id/leaderboard" element={<LeaderboardPage />} />
        <Route path="participants" element={<ParticipantsPage />} />
        <Route path="leaderboards" element={<LeaderboardIndexPage />} />
      </Route>
      </Route>
      <Route element={<ParticipantLayout />}>
        <Route path="/access" element={<ParticipantAccessPage />} />
        <Route path="/claim" element={<BookingClaimPage />} />
        <Route path="/events" element={<PublicEventsPage />} />
        <Route path="/register/:warSessionId" element={<RegistrationPage />} />
        <Route element={<ParticipantGate />}>
          <Route path="/waiting-room" element={<WaitingRoomPage />} />
          <Route path="/challenge" element={<ChallengePage />} />
          <Route path="/result" element={<ResultPage />} />
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/events" replace />} />
      <Route path="*" element={<Navigate to="/events" replace />} />
    </Routes>
  )
}
