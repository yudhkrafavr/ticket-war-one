import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Pencil, Send } from 'lucide-react'
import { batchApi } from '../../api/batchApi'
import { getApiErrorMessage } from '../../api/client'
import { triviaApi } from '../../api/triviaApi'
import { warSessionApi } from '../../api/warSessionApi'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from '../../components/ui/Feedback'
import { SessionLinks } from '../../components/ui/SessionLinks'
import { formatDateTime } from '../../utils/date'

export function WarSessionDetailPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const sessionQuery = useQuery({ queryKey: ['war-session', id], queryFn: () => warSessionApi.get(id) })
  const batchesQuery = useQuery({ queryKey: ['batches', id], queryFn: () => batchApi.list(id), enabled: Boolean(sessionQuery.data) })
  const questionsQuery = useQuery({ queryKey: ['questions', id], queryFn: () => triviaApi.list(id), enabled: Boolean(sessionQuery.data) })
  const publishMutation = useMutation({
    mutationFn: () => warSessionApi.publish(id),
    onSuccess: async (session) => {
      await queryClient.setQueryData(['war-session', id], session)
      await queryClient.invalidateQueries({ queryKey: ['war-sessions'] })
    },
  })
  const session = sessionQuery.data

  if (sessionQuery.isLoading) return <LoadingState label="Loading session details…" />
  if (sessionQuery.isError || !session) return <ErrorState message={getApiErrorMessage(sessionQuery.error)} onRetry={() => void sessionQuery.refetch()} />

  const canEdit = session.status === 'DRAFT'
  const requiredQuestionCount = Math.max(10, session.questionsPerAttempt)
  const configuredQuestionCount = questionsQuery.data?.length ?? 0
  const questionsHaveValidAnswers = (questionsQuery.data ?? []).every(
    (question) => question.options.filter((option) => option.isCorrect).length === 1,
  )
  const questionsReady = !questionsQuery.isLoading
    && !questionsQuery.isError
    && configuredQuestionCount >= requiredQuestionCount
    && questionsHaveValidAnswers
  const ticketQuota = (batchesQuery.data ?? []).reduce((sum, batch) => sum + batch.ticketQuota, 0)
  const publish = () => {
    if (!questionsReady) return
    if (window.confirm('Publish this session? Finish the schedule, batches, and questions first.')) publishMutation.mutate()
  }

  return (
    <>
      <PageHeader eyebrow="Session details" title={session.name} detail={`War Session ${session.id}`}
        action={<Link className="button button-secondary" to={`/admin/events/${session.eventId}`}><ArrowLeft size={15} /> Event</Link>} />
      {publishMutation.error && <ErrorState message={getApiErrorMessage(publishMutation.error)} />}
      <div className="grid" style={{ gap: 16 }}>
        <section className="card">
          <div className="card-title"><div><h2>Configuration</h2></div><div className="event-card-actions">
            <StatusBadge status={session.status} />
            {canEdit && <><Link className="button button-secondary button-small" to={`/admin/war-sessions/${id}/edit`}><Pencil size={13} /> Edit</Link><button className="button button-small" onClick={publish} disabled={publishMutation.isPending || !questionsReady}><Send size={13} />{publishMutation.isPending ? 'Publishing…' : 'Publish'}</button></>}
          </div></div>
          {canEdit && !questionsReady && <div className="field-hint" role="status">
            {questionsQuery.isLoading
              ? 'Checking saved trivia questions before enabling publish…'
              : questionsQuery.isError
                ? 'Trivia questions could not be verified. Retry loading them before publishing.'
                : !questionsHaveValidAnswers
                  ? <><Link className="table-link" to={`/admin/war-sessions/${id}/questions`}>Fix trivia answers</Link>: every question must have exactly one correct answer before publishing.</>
                  : <><Link className="table-link" to={`/admin/war-sessions/${id}/questions`}>Add {requiredQuestionCount - configuredQuestionCount} more saved trivia questions</Link> before publishing.</>}
          </div>}
          <div className="detail-grid">
            <Detail label="Participant capacity" value={session.totalParticipants.toLocaleString()} />
            <Detail label="Batch allocation" value={`${batchesQuery.data?.length ?? '—'} / ${session.totalBatches}`} />
            <Detail label="Configured ticket quota" value={batchesQuery.isError ? 'Unavailable' : `${ticketQuota.toLocaleString()} / ${session.totalTickets.toLocaleString()}`} />
            <Detail label="Challenge questions" value={questionsQuery.isError ? 'Unavailable' : `${questionsQuery.data?.length ?? '—'} / ${session.questionsPerAttempt} per attempt`} />
            <Detail label="Challenge duration" value={`${session.challengeDurationSeconds.toLocaleString()} seconds`} />
            <Detail label="Waiting room opens" value={formatDateTime(session.waitingRoomOpensAt)} />
            <Detail label="Challenge starts" value={formatDateTime(session.challengeStartsAt)} />
          </div>
        </section>
        <div className="two-column">
          <section className="card"><div className="card-title"><div><h2>Calculated batches</h2><p>Participant capacity and ticket allocation are split evenly across batches.</p></div></div>
            {batchesQuery.isLoading ? <LoadingState /> : batchesQuery.isError ? <ErrorState message={getApiErrorMessage(batchesQuery.error)} onRetry={() => void batchesQuery.refetch()} /> : !batchesQuery.data?.length ? <EmptyState title="No calculated batches" detail="Edit and save the War Session to refresh its automatic batch allocation." /> : <div className="table-wrap"><table><thead><tr><th>Batch</th><th>Participant capacity</th><th>Ticket allocation</th><th>Status</th></tr></thead><tbody>{batchesQuery.data.map((batch) => <tr key={batch.id}><td>Batch {batch.batchNumber}</td><td>{batch.participantCapacity.toLocaleString()}</td><td>{batch.ticketQuota.toLocaleString()}</td><td><StatusBadge status={batch.status} /></td></tr>)}</tbody></table></div>}
          </section>
          <section className="card"><div className="card-title"><div><h2>Trivia set</h2><p>Save at least {requiredQuestionCount} questions before publishing.</p></div><Link className="table-link" to={`/admin/war-sessions/${id}/questions`}>Manage</Link></div>
            {questionsQuery.isLoading ? <LoadingState /> : questionsQuery.isError ? <ErrorState message={getApiErrorMessage(questionsQuery.error)} onRetry={() => void questionsQuery.refetch()} /> : !questionsQuery.data?.length ? <EmptyState title="No questions configured" detail="Add questions and exactly one correct option to each." /> : <div className="detail-item"><span>Question count</span><strong>{questionsQuery.data.length} questions · {session.questionsPerAttempt} per attempt</strong></div>}
          </section>
        </div>
        <details className="card session-results-links"><summary>More links</summary><div style={{ marginTop: 12 }}><SessionLinks id={id} /></div></details>
      </div>
    </>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div className="detail-item"><span>{label}</span><strong>{value}</strong></div>
}
