import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Pencil, Plus, Save, Send, Trash2, X } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { eventApi } from '../../api/eventApi'
import { getApiErrorMessage } from '../../api/client'
import { warSessionApi } from '../../api/warSessionApi'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from '../../components/ui/Feedback'
import type { CreateEventRequest, EventResponse } from '../../types/api'
import { formatDateTime, fromDateTimeLocal, toDateTimeLocal } from '../../utils/date'

type EventForm = {
  name: string
  description: string
  venue: string
  eventDate: string
  totalTickets: string
  registrationStartAt: string
  registrationEndAt: string
}

const toEventForm = (event: EventResponse): EventForm => ({
  name: event.name,
  description: event.description ?? '',
  venue: event.venue ?? '',
  eventDate: toDateTimeLocal(event.eventDate),
  totalTickets: String(event.totalTickets),
  registrationStartAt: toDateTimeLocal(event.registrationStartAt),
  registrationEndAt: toDateTimeLocal(event.registrationEndAt),
})

export function EventDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<EventForm | null>(null)
  const [formError, setFormError] = useState('')
  const eventQuery = useQuery({ queryKey: ['event', id], queryFn: () => eventApi.get(id), enabled: Boolean(id) })
  const sessionsQuery = useQuery({ queryKey: ['war-sessions', 'event', id], queryFn: () => warSessionApi.list(), enabled: Boolean(id) })
  const saveMutation = useMutation({
    mutationFn: (request: CreateEventRequest) => eventApi.update(id, request),
    onSuccess: async (updatedEvent) => {
      queryClient.setQueryData(['event', id], updatedEvent)
      await queryClient.invalidateQueries({ queryKey: ['events'] })
      setEditing(false)
      setForm(null)
      setFormError('')
    },
  })
  const publishMutation = useMutation({
    mutationFn: () => eventApi.publish(id),
    onSuccess: async (updatedEvent) => {
      queryClient.setQueryData(['event', id], updatedEvent)
      await queryClient.invalidateQueries({ queryKey: ['events'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: () => eventApi.remove(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['events'] })
      navigate('/admin/events')
    },
  })

  const event = eventQuery.data
  const sessions = (sessionsQuery.data ?? []).filter((session) => session.eventId === id)

  if (eventQuery.isLoading) return <LoadingState label="Loading event details…" />
  if (eventQuery.isError || !event) return <ErrorState message={getApiErrorMessage(eventQuery.error)} onRetry={() => void eventQuery.refetch()} />

  function updateField(field: keyof EventForm, value: string) {
    setForm((current) => current ? { ...current, [field]: value } : current)
  }

  function beginEdit() {
    setForm(toEventForm(event))
    setFormError('')
    saveMutation.reset()
    setEditing(true)
  }

  function cancelEdit() {
    setEditing(false)
    setForm(null)
    setFormError('')
  }

  function saveEvent(submitEvent: FormEvent<HTMLFormElement>) {
    submitEvent.preventDefault()
    setFormError('')
    if (!form) return
    const eventDate = fromDateTimeLocal(form.eventDate)
    if (!form.name.trim()) return setFormError('Enter an event name.')
    if (!eventDate) return setFormError('Choose an event date.')
    if (!Number.isInteger(Number(form.totalTickets)) || Number(form.totalTickets) < 1) {
      return setFormError('Total tickets must be a positive whole number.')
    }
    const registrationStartAt = fromDateTimeLocal(form.registrationStartAt)
    const registrationEndAt = fromDateTimeLocal(form.registrationEndAt)
    if (registrationStartAt && registrationEndAt && registrationEndAt <= registrationStartAt) {
      return setFormError('Registration must end after it starts.')
    }
    if (registrationEndAt && registrationEndAt >= eventDate) {
      return setFormError('Registration must end before the event date.')
    }
    saveMutation.mutate({
      name: form.name.trim(),
      description: form.description.trim(),
      venue: form.venue.trim(),
      eventDate,
      totalTickets: Number(form.totalTickets),
      registrationStartAt,
      registrationEndAt,
    })
  }

  return (
    <>
      <PageHeader
        eyebrow="Event details"
        title={event.name}
        detail="Manage this Event and set up its War Session here."
        action={<Link className="button button-secondary" to="/admin/events"><ArrowLeft size={15} /> All events</Link>}
      />

      <section className="card">
        <div className="card-title">
          <div><h2>Event</h2></div>
          <div className="event-card-actions">
            <StatusBadge status={event.status} />
            {event.status === 'DRAFT' && !editing && <>
              <button className="button button-secondary button-small" onClick={beginEdit}><Pencil size={13} /> Edit</button>
              <button className="button button-small" disabled={publishMutation.isPending || deleteMutation.isPending} onClick={() => {
                if (window.confirm(`Publish “${event.name}”? The event will no longer be editable.`)) publishMutation.mutate()
              }}><Send size={13} />{publishMutation.isPending ? 'Publishing…' : 'Publish'}</button>
              <button className="button button-danger button-small" disabled={publishMutation.isPending || deleteMutation.isPending} onClick={() => {
                if (window.confirm(`Delete draft event “${event.name}”? This cannot be undone.`)) deleteMutation.mutate()
              }}><Trash2 size={13} />{deleteMutation.isPending ? 'Deleting…' : 'Delete'}</button>
            </>}
            {editing && <button className="button button-quiet button-small" onClick={cancelEdit}><X size={14} /> Cancel</button>}
          </div>
        </div>

        {(saveMutation.error || publishMutation.error || deleteMutation.error) && <ErrorState message={getApiErrorMessage(saveMutation.error ?? publishMutation.error ?? deleteMutation.error)} />}
        {editing && form ? <form onSubmit={saveEvent}>
          {formError && <div className="form-error" role="alert">{formError}</div>}
          <div className="form-grid">
            <Field label="Event name"><input required maxLength={150} value={form.name} onChange={(event) => updateField('name', event.target.value)} /></Field>
            <Field label="Venue"><input maxLength={255} value={form.venue} onChange={(event) => updateField('venue', event.target.value)} /></Field>
            <Field label="Event date and time"><input required type="datetime-local" value={form.eventDate} onChange={(event) => updateField('eventDate', event.target.value)} /></Field>
            <Field label="Total tickets"><input required type="number" min="1" step="1" value={form.totalTickets} onChange={(event) => updateField('totalTickets', event.target.value)} /></Field>
            <Field label="Registration opens"><input type="datetime-local" value={form.registrationStartAt} onChange={(event) => updateField('registrationStartAt', event.target.value)} /></Field>
            <Field label="Registration closes"><input type="datetime-local" value={form.registrationEndAt} onChange={(event) => updateField('registrationEndAt', event.target.value)} /></Field>
            <Field label="Description" className="span-2"><textarea value={form.description} onChange={(event) => updateField('description', event.target.value)} /></Field>
          </div>
          <div className="form-footer"><button className="button" disabled={saveMutation.isPending}><Save size={14} />{saveMutation.isPending ? 'Saving…' : 'Save changes'}</button></div>
        </form> : <>
          <div className="detail-grid">
            <Detail label="Date" value={formatDateTime(event.eventDate)} />
            <Detail label="Venue" value={event.venue || 'Not specified'} />
            <Detail label="Total tickets" value={event.totalTickets.toLocaleString()} />
            <Detail label="Registration opens" value={formatDateTime(event.registrationStartAt)} />
            <Detail label="Registration closes" value={formatDateTime(event.registrationEndAt)} />
          </div>
          {event.description && <p className="muted" style={{ marginTop: 15 }}>{event.description}</p>}
        </>}
      </section>

      <section className="card">
        <div className="card-title">
          <div><h2>War Sessions</h2><p>Competitions connected to this event.</p></div>
          <Link className="button" to={`/admin/war-sessions/new?eventId=${event.id}`}><Plus size={15} /> Set up War Session</Link>
        </div>
        {sessionsQuery.isLoading && <LoadingState label="Loading War Sessions…" />}
        {sessionsQuery.isError && <ErrorState message={getApiErrorMessage(sessionsQuery.error)} onRetry={() => void sessionsQuery.refetch()} />}
        {!sessionsQuery.isLoading && !sessionsQuery.isError && sessions.length === 0 && (
          <EmptyState title="No War Session yet" detail="Set one up for this Event to prepare the competition." />
        )}
        {!sessionsQuery.isLoading && !sessionsQuery.isError && sessions.length > 0 && (
          <div className="table-wrap"><table>
            <thead><tr><th>War Session</th><th>Challenge starts</th><th>Status</th></tr></thead>
            <tbody>{sessions.map((session) => (
              <tr key={session.id}>
                <td><Link className="table-primary" to={`/admin/war-sessions/${session.id}`}>{session.name}</Link></td>
                <td>{formatDateTime(session.challengeStartsAt)}</td>
                <td><StatusBadge status={session.status} /></td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
      </section>
    </>
  )
}

function Field({ label, className, children }: { label: string; className?: string; children: ReactNode }) {
  return <div className={`field ${className ?? ''}`}><label>{label}{children}</label></div>
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div className="detail-item"><span>{label}</span><strong>{value}</strong></div>
}
