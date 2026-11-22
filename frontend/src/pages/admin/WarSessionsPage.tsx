import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Search } from 'lucide-react'
import { warSessionApi } from '../../api/warSessionApi'
import { getApiErrorMessage } from '../../api/client'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from '../../components/ui/Feedback'
import type { WarSessionStatus } from '../../types/api'
import { formatDateTime } from '../../utils/date'

const statuses: (WarSessionStatus | '')[] = ['', 'DRAFT', 'SCHEDULED', 'WAITING_ROOM_OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']

export function WarSessionsPage() {
  const [status, setStatus] = useState<WarSessionStatus | ''>('')
  const [search, setSearch] = useState('')
  const sessionsQuery = useQuery({ queryKey: ['war-sessions', status], queryFn: () => warSessionApi.list(status || undefined) })
  const sessions = (sessionsQuery.data ?? []).filter((session) => {
    const query = search.trim().toLowerCase()
    return !query || session.name.toLowerCase().includes(query) || session.eventId.toLowerCase().includes(query)
  })

  return (
    <>
      <PageHeader title="War Sessions" detail="Sessions are managed from their Event details." />
      <section className="card">
        <div className="filters">
          <div className="search-box"><Search size={15} /><input aria-label="Search sessions" placeholder="Search name or event ID" value={search} onChange={(event) => setSearch(event.target.value)} /></div>
          <select aria-label="Filter by status" value={status} onChange={(event) => setStatus(event.target.value as WarSessionStatus | '')}>
            {statuses.map((item) => <option key={item || 'all'} value={item}>{item ? item.replaceAll('_', ' ') : 'All statuses'}</option>)}
          </select>
        </div>
        {sessionsQuery.isLoading && <LoadingState label="Loading sessions…" />}
        {sessionsQuery.isError && <ErrorState message={getApiErrorMessage(sessionsQuery.error)} onRetry={() => void sessionsQuery.refetch()} />}
        {!sessionsQuery.isLoading && !sessionsQuery.isError && sessions.length === 0 && <EmptyState title="No matching sessions" detail="Try another search or open an Event to set up a session." />}
        {!sessionsQuery.isLoading && !sessionsQuery.isError && sessions.length > 0 && (
          <div className="table-wrap"><table><thead><tr><th>Session</th><th>Event ID</th><th>Capacity</th><th>Batches</th><th>Challenge start</th><th>Status</th></tr></thead>
            <tbody>{sessions.map((session) => <tr key={session.id}>
              <td><Link className="table-primary" to={`/admin/war-sessions/${session.id}`}>{session.name}</Link><span className="table-secondary">{session.id}</span></td>
              <td>{session.eventId}</td><td>{session.totalParticipants.toLocaleString()}</td><td>{session.totalBatches}</td>
              <td>{formatDateTime(session.challengeStartsAt)}</td><td><StatusBadge status={session.status} /></td>
            </tr>)}</tbody></table></div>
        )}
      </section>
    </>
  )
}
