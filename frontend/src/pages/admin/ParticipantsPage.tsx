import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Search } from 'lucide-react'
import { getApiErrorMessage } from '../../api/client'
import { participantApi } from '../../api/participantApi'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from '../../components/ui/Feedback'
import { formatDateTime } from '../../utils/date'

export function ParticipantsPage() {
  const { id } = useParams()
  const [search, setSearch] = useState('')
  const rosterQuery = useQuery({
    queryKey: ['admin-participants', id],
    queryFn: () => participantApi.adminRoster(id!),
    enabled: Boolean(id),
  })
  const participants = (rosterQuery.data ?? []).filter((participant) => {
    const value = search.trim().toLowerCase()
    return !value || participant.email.toLowerCase().includes(value)
      || participant.status.toLowerCase().includes(value)
      || String(participant.queuePosition ?? '').includes(value)
  })

  return (
    <>
      <PageHeader title="Participants" detail={id ? 'Registered participants for this session.' : 'Open a War Session to view its participants.'}
        action={id ? <Link className="button button-secondary" to={`/admin/war-sessions/${id}`}><ArrowLeft size={15} /> Session</Link> : undefined} />
      {!id && <EmptyState title="Choose a War Session first" detail="Participant lists are shown from a session’s details page." />}
      {id && <section className="card">
        <div className="filters">
          <div className="search-box"><Search size={15} /><input aria-label="Search participants" placeholder="Search email, status, or queue number" value={search} onChange={(event) => setSearch(event.target.value)} /></div>
        </div>
        {rosterQuery.isLoading && <LoadingState label="Loading participants…" />}
        {rosterQuery.isError && <ErrorState message={getApiErrorMessage(rosterQuery.error)} onRetry={() => void rosterQuery.refetch()} />}
        {!rosterQuery.isLoading && !rosterQuery.isError && participants.length === 0 && <EmptyState title={search ? 'No matches' : 'No participants yet'} />}
        {participants.length > 0 && <div className="table-wrap"><table><thead><tr><th>Email</th><th>Registered</th><th>Waiting room entry</th><th>Batch</th><th>Queue</th><th>Status</th></tr></thead><tbody>
          {participants.map((participant) => <tr key={participant.participantId}>
            <td>{participant.email}</td>
            <td>{formatDateTime(participant.registeredAt)}</td>
            <td>{formatDateTime(participant.queueEnteredAt)}</td>
            <td>{participant.batchNumber == null ? '—' : `Batch ${participant.batchNumber}`}</td>
            <td>{participant.queuePosition?.toLocaleString() ?? '—'}</td>
            <td><StatusBadge status={participant.status} /></td>
          </tr>)}
        </tbody></table></div>}
      </section>}
    </>
  )
}
