import { useMemo, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Award, Clock3, Ticket, Trophy, Users } from 'lucide-react'
import axios from 'axios'
import { getApiErrorMessage } from '../../api/client'
import { leaderboardApi } from '../../api/leaderboardApi'
import { warSessionApi } from '../../api/warSessionApi'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from '../../components/ui/Feedback'
import { formatDateTime, formatDuration } from '../../utils/date'

export function LeaderboardIndexPage() {
  const sessionsQuery = useQuery({ queryKey: ['war-sessions'], queryFn: () => warSessionApi.list() })
  return (
    <>
      <PageHeader eyebrow="Competition results" title="Leaderboards" detail="Open finalized, read-only results for a War Session." />
      {sessionsQuery.isLoading && <LoadingState label="Loading sessions…" />}
      {sessionsQuery.isError && <ErrorState message={getApiErrorMessage(sessionsQuery.error)} onRetry={() => void sessionsQuery.refetch()} />}
      {!sessionsQuery.isLoading && !sessionsQuery.isError && !sessionsQuery.data?.length && <EmptyState title="No sessions yet" detail="Create a War Session to view its results." />}
      {!!sessionsQuery.data?.length && <section className="card"><div className="table-wrap"><table><thead><tr><th>War Session</th><th>Event</th><th>Status</th><th>Result availability</th><th /></tr></thead><tbody>
        {sessionsQuery.data.map((session) => <tr key={session.id}><td><span className="table-primary">{session.name}</span><span className="table-secondary">{session.id}</span></td><td>{session.eventId}</td><td><StatusBadge status={session.status} /></td><td>{session.status === 'COMPLETED' ? 'Check finalization status in the leaderboard API' : 'Not finalized'}</td><td><Link className="table-link" to={`/admin/war-sessions/${session.id}/leaderboard`}>Open results</Link></td></tr>)}
      </tbody></table></div></section>}
    </>
  )
}

export function LeaderboardPage() {
  const { id = '' } = useParams()
  const [batchId, setBatchId] = useState('')
  const [page, setPage] = useState(0)
  const leaderboardQuery = useQuery({ queryKey: ['leaderboard', id], queryFn: () => leaderboardApi.get(id) })
  const batchQuery = useQuery({ queryKey: ['batch-leaderboard', id, batchId], queryFn: () => leaderboardApi.getBatch(id, batchId), enabled: Boolean(batchId) })
  const result = leaderboardQuery.data
  const selectedBatch = batchId ? batchQuery.data?.batch : undefined
  const rows = batchId ? selectedBatch?.participants ?? [] : result?.batches.flatMap((batch) => batch.participants) ?? []
  const pageSize = 20
  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize))
  const pagedRows = useMemo(() => rows.slice(page * pageSize, (page + 1) * pageSize), [rows, page])
  const batches = result?.batches ?? []
  const stats = batches.reduce((totals, batch) => ({
    participants: totals.participants + batch.totalParticipants,
    winners: totals.winners + batch.winnerCount,
    alternates: totals.alternates + batch.alternateCount,
    lost: totals.lost + batch.lostCount,
  }), { participants: 0, winners: 0, alternates: 0, lost: 0 })
  const notFinalized = axios.isAxiosError(leaderboardQuery.error) && leaderboardQuery.error.response?.status === 409
  const batchError = axios.isAxiosError(batchQuery.error) && batchQuery.error.response?.status === 409

  return (
    <>
      <PageHeader title={result?.sessionName ?? 'Leaderboard'} detail={result ? `Results finalized ${formatDateTime(result.leaderboardFinalizedAt)}` : 'Final competition results.'}
        action={<Link className="button button-secondary" to={`/admin/war-sessions/${id}`}><ArrowLeft size={15} /> Session details</Link>} />
      {leaderboardQuery.isLoading && <LoadingState label="Loading leaderboard…" />}
      {leaderboardQuery.isError && (notFinalized
        ? <div className="notice notice-info">Results aren’t ready yet. Check back later.</div>
        : <ErrorState message={getApiErrorMessage(leaderboardQuery.error)} onRetry={() => void leaderboardQuery.refetch()} />)}
      {result && <>
        <div className="grid stats-grid">
          <Summary icon={<Users size={16} />} label="Participants" value={stats.participants} note="Included in finalized batches" />
          <Summary icon={<Trophy size={16} />} label="Winners" value={stats.winners} note="Final result: WINNER" />
          <Summary icon={<Award size={16} />} label="Alternates" value={stats.alternates} note="Final result: ALTERNATE" />
          <Summary icon={<Ticket size={16} />} label="Configured tickets" value={result.totalTickets} note={`Lost participants: ${stats.lost}`} />
        </div>
        <section className="card">
          <div className="card-title"><div><h2>{batchId ? `Batch ${selectedBatch?.batchNumber ?? ''} leaderboard` : 'Overall leaderboard'}</h2></div>
            <select aria-label="Select leaderboard batch" value={batchId} onChange={(event) => { setBatchId(event.target.value); setPage(0) }}><option value="">All batches</option>{batches.map((batch) => <option key={batch.batchId} value={batch.batchId}>Batch {batch.batchNumber} · {batch.ticketQuota} tickets</option>)}</select>
          </div>
          {batchQuery.isLoading && <LoadingState label="Loading selected batch…" />}
          {batchQuery.isError && (batchError ? <div className="notice notice-info">This batch’s results aren’t ready yet.</div> : <ErrorState message={getApiErrorMessage(batchQuery.error)} onRetry={() => void batchQuery.refetch()} />)}
          {selectedBatch && <div className="batch-summary"><span><StatusBadge status={selectedBatch.batchStatus} /></span><span>Ticket quota <strong>{selectedBatch.ticketQuota}</strong></span><span>Capacity <strong>{selectedBatch.participantCapacity}</strong></span><span>Winners <strong>{selectedBatch.winnerCount}</strong></span><span>Alternates <strong>{selectedBatch.alternateCount}</strong></span></div>}
          {!batchQuery.isLoading && !batchQuery.isError && rows.length === 0 && <EmptyState title="No results yet" />}
          {rows.length > 0 && <>
            <div className="table-wrap"><table><thead><tr><th>Rank</th><th>Participant</th><th>Batch</th><th>Queue position</th><th>Queue entered</th><th>Completion</th><th>Result</th></tr></thead><tbody>
              {pagedRows.map((participant) => {
                const batch = batches.find((item) => item.participants.some((row) => row.participantId === participant.participantId))
                return <tr key={participant.participantId} className={participant.competitionResult === 'WINNER' ? 'winner-row' : ''}>
                  <td><span className="rank-cell">{participant.competitionRank ?? '—'}</span></td>
                  <td><span className="table-primary">{participant.email}</span><span className="table-secondary">{participant.participantId}</span></td>
                  <td>{selectedBatch?.batchNumber ?? batch?.batchNumber ?? '—'}</td>
                  <td>{participant.queuePosition ?? '—'}</td>
                  <td>{formatDateTime(participant.queueEnteredAt)}</td>
                  <td><Clock3 size={13} className="inline-icon" />{formatDuration(participant.completionDurationMs)}</td>
                  <td><StatusBadge status={participant.competitionResult} /></td>
                </tr>
              })}
            </tbody></table></div>
            <div className="pagination"><span>{rows.length} participant results · page {page + 1} of {totalPages}</span><div className="table-actions"><button className="button button-secondary button-small" disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</button><button className="button button-secondary button-small" disabled={page + 1 >= totalPages} onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}>Next</button></div></div>
          </>}
        </section>
      </>}
    </>
  )
}

function Summary({ icon, label, value, note }: { icon: ReactNode; label: string; value: number; note: string }) {
  return <section className="card stat-card"><div className="stat-label">{icon}{label}</div><div className="stat-value">{value.toLocaleString()}</div><div className="stat-note">{note}</div></section>
}
