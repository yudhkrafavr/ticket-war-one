import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Save, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { eventApi } from '../../api/eventApi'
import { getApiErrorMessage } from '../../api/client'
import { EmptyState, ErrorState, LoadingState, PageHeader, StatusBadge } from '../../components/ui/Feedback'
import type { CreateEventRequest } from '../../types/api'
import { formatDateTime, fromDateTimeLocal } from '../../utils/date'

interface EventForm {
  name: string
  description: string
  venue: string
  eventDate: string
  totalTickets: string
  registrationStartAt: string
  registrationEndAt: string
}

const emptyForm: EventForm = {
  name: '', description: '', venue: '', eventDate: '', totalTickets: '',
  registrationStartAt: '', registrationEndAt: '',
}

export function EventsPage() {
  const queryClient = useQueryClient()
  const [formOpen, setFormOpen] = useState(false)
  const [form, setForm] = useState<EventForm>(emptyForm)
  const [validationError, setValidationError] = useState('')
  const [notice, setNotice] = useState('')
  const eventsQuery = useQuery({ queryKey: ['events'], queryFn: eventApi.list })
  const createMutation = useMutation({
    mutationFn: eventApi.create,
    onSuccess: async () => {
      closeForm()
      setNotice('Event created as DRAFT.')
      await queryClient.invalidateQueries({ queryKey: ['events'] })
    },
  })
  const saveError = createMutation.error
  const saving = createMutation.isPending

  function closeForm() {
    setFormOpen(false)
    setForm(emptyForm)
    setValidationError('')
  }

  function beginCreate() {
    if (formOpen) {
      closeForm()
      return
    }
    setNotice('')
    setForm(emptyForm)
    setValidationError('')
    createMutation.reset()
    setFormOpen(true)
  }

  function updateField(field: keyof EventForm, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setValidationError('')

    const eventDate = fromDateTimeLocal(form.eventDate)
    if (!form.name.trim()) return setValidationError('Enter an event name.')
    if (!eventDate) return setValidationError('Choose an event date.')
    if (!Number.isInteger(Number(form.totalTickets)) || Number(form.totalTickets) < 1) {
      return setValidationError('Total tickets must be a positive whole number.')
    }

    const registrationStartAt = fromDateTimeLocal(form.registrationStartAt)
    const registrationEndAt = fromDateTimeLocal(form.registrationEndAt)
    if (registrationStartAt && registrationEndAt && registrationEndAt <= registrationStartAt) {
      return setValidationError('Registration must end after it starts.')
    }
    if (registrationEndAt && registrationEndAt >= eventDate) {
      return setValidationError('Registration must end before the event date.')
    }

    const request: CreateEventRequest = {
      name: form.name.trim(),
      description: form.description.trim(),
      venue: form.venue.trim(),
      eventDate,
      totalTickets: Number(form.totalTickets),
      registrationStartAt,
      registrationEndAt,
    }
    createMutation.mutate(request)
  }

  return (
    <>
      <PageHeader title="Events" detail="Create an event first, then use it for a War Session."
        action={<button className="button" disabled={saving} onClick={beginCreate}>
          {formOpen ? <X size={15} /> : <Plus size={15} />}{formOpen ? 'Close form' : 'Create event'}
        </button>} />

      {notice && <div className="notice notice-info" role="status">{notice}</div>}
      {saveError && <ErrorState message={getApiErrorMessage(saveError)} />}

      {formOpen && <form className="card event-create-form" onSubmit={submit}>
        <div className="card-title"><div><h2>New event</h2><p>It will start as a draft.</p></div></div>
        {validationError && <div className="form-error" role="alert">{validationError}</div>}
        <div className="form-grid">
          <Field label="Event name"><input required maxLength={150} value={form.name} onChange={(event) => updateField('name', event.target.value)} /></Field>
          <Field label="Venue"><input maxLength={255} value={form.venue} onChange={(event) => updateField('venue', event.target.value)} /></Field>
          <Field label="Event date and time"><input required type="datetime-local" value={form.eventDate} onChange={(event) => updateField('eventDate', event.target.value)} /></Field>
          <Field label="Total tickets"><input required type="number" min="1" step="1" value={form.totalTickets} onChange={(event) => updateField('totalTickets', event.target.value)} /></Field>
          <Field label="Registration opens (optional)"><input type="datetime-local" value={form.registrationStartAt} onChange={(event) => updateField('registrationStartAt', event.target.value)} /></Field>
          <Field label="Registration closes (optional)"><input type="datetime-local" value={form.registrationEndAt} onChange={(event) => updateField('registrationEndAt', event.target.value)} /></Field>
          <Field label="Description" className="span-2"><textarea value={form.description} onChange={(event) => updateField('description', event.target.value)} /></Field>
        </div>
        <div className="form-footer">
          <button type="button" className="button button-secondary" disabled={saving} onClick={closeForm}>Cancel</button>
          <button className="button" disabled={saving}><Save size={14} />{saving ? 'Saving…' : 'Save event'}</button>
        </div>
      </form>}

      <section className="card">
        <div className="card-title"><div><h2>Event list</h2><p>Events returned by the Event Service</p></div></div>
        {eventsQuery.isLoading && <LoadingState label="Loading events…" />}
        {eventsQuery.isError && <ErrorState message={getApiErrorMessage(eventsQuery.error)} onRetry={() => void eventsQuery.refetch()} />}
        {!eventsQuery.isLoading && !eventsQuery.isError && !eventsQuery.data?.length && <EmptyState title="No events yet" detail="Create your first event above." />}
        {!!eventsQuery.data?.length && <div className="table-wrap"><table><thead><tr><th>Event</th><th>Date</th><th>Tickets</th><th>Registration</th><th>Status</th></tr></thead><tbody>
          {eventsQuery.data.map((item) => <tr key={item.id}>
            <td><Link className="table-primary event-name-link" to={`/admin/events/${item.id}`}>{item.name}</Link><span className="table-secondary">{item.id}</span></td>
            <td>{formatDateTime(item.eventDate)}</td>
            <td>{item.totalTickets.toLocaleString()}</td>
            <td>{formatDateTime(item.registrationStartAt)} – {formatDateTime(item.registrationEndAt)}</td>
            <td><StatusBadge status={item.status} /></td>
          </tr>)}
        </tbody></table></div>}
      </section>
    </>
  )
}

function Field({ label, className, children }: { label: string; className?: string; children: ReactNode }) {
  return <div className={`field ${className ?? ''}`}><label>{label}{children}</label></div>
}
