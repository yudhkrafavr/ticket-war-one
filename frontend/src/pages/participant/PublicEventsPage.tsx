import { useQuery } from '@tanstack/react-query'
import { CalendarDays, MapPin, Ticket, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import { eventApi } from '../../api/eventApi'
import { getApiErrorMessage } from '../../api/client'
import { EmptyState, ErrorState, LoadingState, StatusBadge } from '../../components/ui/Feedback'
import { warSessionApi } from '../../api/warSessionApi'
import { formatDateTime } from '../../utils/date'

export function PublicEventsPage() {
  const eventsQuery = useQuery({ queryKey: ['public-events'], queryFn: eventApi.list })
  const sessionsQuery = useQuery({
    queryKey: ['public-scheduled-war-sessions'],
    queryFn: () => warSessionApi.list('SCHEDULED'),
    refetchInterval: 10_000,
    refetchIntervalInBackground: false,
  })

  const eventsById = new Map((eventsQuery.data ?? []).map((event) => [event.id, event]))
  const sessions = sessionsQuery.data ?? []
  const isLoading = eventsQuery.isLoading || sessionsQuery.isLoading
  const error = eventsQuery.error ?? sessionsQuery.error

  return (
    <div className="public-events-page">
      <section className="waiting-hero public-events-hero">
        <div className="waiting-eyebrow">Ticket War · Public events</div>
        <h1>Pick a challenge. Claim your spot.</h1>
        <p>Find a scheduled ticket competition and preregister with your email.</p>
      </section>

      <section className="public-events-list" aria-labelledby="scheduled-events-title">
        <div className="public-events-heading">
          <h2 id="scheduled-events-title">Scheduled competitions</h2>
          <span>{sessions.length} {sessions.length === 1 ? 'session' : 'sessions'}</span>
        </div>
        {isLoading && <LoadingState label="Finding scheduled competitions…" />}
        {!isLoading && error && (
          <ErrorState
            message={getApiErrorMessage(error)}
            onRetry={() => {
              void eventsQuery.refetch()
              void sessionsQuery.refetch()
            }}
          />
        )}
        {!isLoading && !error && sessions.length === 0 && (
          <EmptyState title="No competitions are scheduled yet" detail="Check back soon for the next ticket war." />
        )}
        {!isLoading && !error && sessions.length > 0 && sessions.map((session) => {
          const event = eventsById.get(session.eventId)
          const spotsLeft = Math.max(0, session.totalParticipants - session.registeredParticipants)
          return (
            <article className="public-event-card" key={session.id}>
              <div className="public-event-copy">
                <div className="public-event-title-row">
                  <h3>{event?.name ?? session.name}</h3>
                  <StatusBadge status={session.status} />
                </div>
                <p>{event?.description || session.description || 'A new ticket competition is ready for players.'}</p>
                <div className="public-event-meta">
                  <span><CalendarDays size={15} /> {formatDateTime(event?.eventDate ?? session.challengeStartsAt)}</span>
                  {event?.venue && <span><MapPin size={15} /> {event.venue}</span>}
                  <span><Ticket size={15} /> Register {formatDateTime(session.registrationOpensAt)} – {formatDateTime(session.registrationClosesAt)}</span>
                  <span><Users size={15} /> {session.registeredParticipants.toLocaleString()} / {session.totalParticipants.toLocaleString()} joined</span>
                </div>
              </div>
              {spotsLeft > 0
                ? <Link className="button public-event-cta" to={`/register/${session.id}`}>Join this war</Link>
                : <button className="button public-event-cta" disabled>Full</button>}
            </article>
          )
        })}
      </section>
    </div>
  )
}
