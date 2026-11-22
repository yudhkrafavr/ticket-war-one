import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { warSessionApi } from '../../api/warSessionApi'
import { getApiErrorMessage } from '../../api/client'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from '../../components/ui/Feedback'
import { formatDateTime } from '../../utils/date'

export function DashboardPage() {
  const sessionsQuery = useQuery({ queryKey: ['war-sessions'], queryFn: () => warSessionApi.list() })
  const sessions = [...(sessionsQuery.data ?? [])]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 6)

  return (
    <>
      <PageHeader title="Overview" detail="A quick look at your War Sessions." />
      <section className="card dashboard-sessions">
        <div className="card-title">
          <div><h2>Recent sessions</h2><p>Your latest War Sessions</p></div>
          <Link className="table-link" to="/admin/war-sessions">All sessions</Link>
        </div>
        {sessionsQuery.isLoading && <LoadingState label="Loading sessions…" />}
        {sessionsQuery.isError && <ErrorState message={getApiErrorMessage(sessionsQuery.error)} onRetry={() => void sessionsQuery.refetch()} />}
        {!sessionsQuery.isLoading && !sessionsQuery.isError && sessions.length === 0 && (
          <EmptyState title="No sessions yet" detail="Create a War Session to get started." />
        )}
        {sessions.length > 0 && (
          <div className="table-wrap"><table><thead><tr><th>Session</th><th>Challenge starts</th><th>Status</th></tr></thead><tbody>
            {sessions.map((session) => <tr key={session.id}>
              <td><Link className="table-primary" to={`/admin/war-sessions/${session.id}`}>{session.name}</Link></td>
              <td>{formatDateTime(session.challengeStartsAt)}</td>
              <td><StatusBadge status={session.status} /></td>
            </tr>)}
          </tbody></table></div>
        )}
      </section>
    </>
  )
}
