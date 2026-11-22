import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Save } from 'lucide-react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { eventApi } from '../../api/eventApi'
import { getApiErrorMessage } from '../../api/client'
import { warSessionApi } from '../../api/warSessionApi'
import { ErrorState, LoadingState, PageHeader } from '../../components/ui/Feedback'
import type { WarSessionRequest } from '../../types/api'
import { formatDateTime, fromDateTimeLocal, toDateTimeLocal } from '../../utils/date'

type FormState = {
  totalParticipants: string
  totalBatches: string
  questionsPerAttempt: string
  challengeDurationSeconds: string
  waitingRoomOpensAt: string
  challengeStartsAt: string
}

const emptyForm: FormState = {
  totalParticipants: '',
  totalBatches: '',
  questionsPerAttempt: '',
  challengeDurationSeconds: '',
  waitingRoomOpensAt: '',
  challengeStartsAt: '',
}

export function WarSessionFormPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const eventIdFromUrl = searchParams.get('eventId') ?? ''
  const editing = Boolean(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState>(emptyForm)
  const [validationError, setValidationError] = useState('')
  const eventsQuery = useQuery({ queryKey: ['events'], queryFn: eventApi.list })
  const sessionQuery = useQuery({ queryKey: ['war-session', id], queryFn: () => warSessionApi.get(id!), enabled: editing })
  const eventId = editing ? sessionQuery.data?.eventId ?? '' : eventIdFromUrl
  const event = eventsQuery.data?.find((item) => item.id === eventId)

  const onSuccess = async (session: { id: string }) => {
    await queryClient.invalidateQueries({ queryKey: ['war-sessions'] })
    await queryClient.invalidateQueries({ queryKey: ['war-session', session.id] })
    navigate(`/admin/war-sessions/${session.id}`)
  }
  const createMutation = useMutation({ mutationFn: warSessionApi.create, onSuccess })
  const updateMutation = useMutation({ mutationFn: (request: WarSessionRequest) => warSessionApi.update(id!, request), onSuccess })
  const saving = createMutation.isPending || updateMutation.isPending
  const serverError = createMutation.error ?? updateMutation.error
  const backTo = editing ? `/admin/war-sessions/${id}` : eventId ? `/admin/events/${eventId}` : '/admin/events'

  useEffect(() => {
    const session = sessionQuery.data
    if (!session) return
    setForm({
      totalParticipants: String(session.totalParticipants),
      totalBatches: String(session.totalBatches),
      questionsPerAttempt: String(session.questionsPerAttempt),
      challengeDurationSeconds: String(session.challengeDurationSeconds),
      waitingRoomOpensAt: toDateTimeLocal(session.waitingRoomOpensAt),
      challengeStartsAt: toDateTimeLocal(session.challengeStartsAt),
    })
  }, [sessionQuery.data])

  function setField(field: keyof FormState, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  function submit(submitEvent: FormEvent<HTMLFormElement>) {
    submitEvent.preventDefault()
    setValidationError('')
    if (!eventId || !event) return setValidationError('The linked Event could not be loaded. Return to Events and open it again.')

    const numbers = ['totalParticipants', 'totalBatches', 'questionsPerAttempt', 'challengeDurationSeconds'] as const
    if (numbers.some((key) => !Number.isInteger(Number(form[key])) || Number(form[key]) < 1)) {
      return setValidationError('Enter positive whole numbers for participants, batches, questions, and duration.')
    }

    const registrationClosesAt = event.registrationEndAt
    const registrationCloseTime = registrationClosesAt ? new Date(registrationClosesAt).getTime() : null
    const waitingRoomTime = form.waitingRoomOpensAt ? new Date(form.waitingRoomOpensAt).getTime() : null
    const challengeStartTime = form.challengeStartsAt ? new Date(form.challengeStartsAt).getTime() : null
    if (registrationCloseTime !== null && waitingRoomTime !== null && registrationCloseTime > waitingRoomTime) {
      return setValidationError('The Event registration must close no later than the waiting room opening.')
    }
    if (waitingRoomTime !== null && challengeStartTime !== null && waitingRoomTime > challengeStartTime) {
      return setValidationError('The waiting room must open no later than the challenge start.')
    }

    const request: WarSessionRequest = {
      eventId,
      name: editing ? sessionQuery.data?.name ?? event.name : event.name,
      description: editing ? sessionQuery.data?.description ?? '' : '',
      totalParticipants: Number(form.totalParticipants),
      totalBatches: Number(form.totalBatches),
      questionsPerAttempt: Number(form.questionsPerAttempt),
      challengeDurationSeconds: Number(form.challengeDurationSeconds),
      waitingRoomOpensAt: fromDateTimeLocal(form.waitingRoomOpensAt),
      challengeStartsAt: fromDateTimeLocal(form.challengeStartsAt),
    }
    if (editing) updateMutation.mutate(request)
    else createMutation.mutate(request)
  }

  if (editing && sessionQuery.isLoading) return <LoadingState label="Loading War Session…" />
  if (editing && sessionQuery.isError) return <ErrorState message={getApiErrorMessage(sessionQuery.error)} onRetry={() => void sessionQuery.refetch()} />
  if (editing && sessionQuery.data && sessionQuery.data.status !== 'DRAFT') return <ErrorState message="Only DRAFT War Sessions can be edited. The session was not changed." />
  if (!editing && !eventId) return <ErrorState message="Start War Session setup from an Event so its ticket and registration details are linked." />
  if (eventsQuery.isError) return <ErrorState message={getApiErrorMessage(eventsQuery.error)} onRetry={() => void eventsQuery.refetch()} />
  if (eventsQuery.isLoading || !event) return <LoadingState label="Loading linked Event…" />

  return (
    <>
      <PageHeader
        eyebrow={event.name}
        title={editing ? 'Edit War Session' : 'Set up War Session'}
        detail="Set the participant target, batch count, questions, and timing. Per-batch capacity and ticket allocation are calculated automatically."
        action={<Link className="button button-secondary" to={backTo}><ArrowLeft size={15} /> Back</Link>}
      />
      {serverError && <div className="form-error" role="alert">{getApiErrorMessage(serverError)}</div>}
      {validationError && <div className="form-error" role="alert">{validationError}</div>}
      <section className="card event-linked-summary">
        <div>
          <strong>{event.name}</strong>
          <span>{formatDateTime(event.eventDate)} · {event.venue || 'Venue not specified'}</span>
          <span>{event.totalTickets.toLocaleString()} tickets · registration {formatDateTime(event.registrationStartAt)} – {formatDateTime(event.registrationEndAt)}</span>
          {(!event.registrationStartAt || !event.registrationEndAt) && <span className="event-linked-warning">Add both Event registration dates before publishing this War Session.</span>}
        </div>
        <Link className="table-link" to={`/admin/events/${event.id}`}>View Event</Link>
      </section>
      <form className="card" onSubmit={submit} noValidate>
        <div className="card-title"><div><h2>Batch setup</h2><p>Participant capacity and tickets are split evenly; any remainder goes to lower-numbered batches.</p></div></div>
        <div className="form-grid">
          <NumberField label="Total participants" value={form.totalParticipants} onChange={(value) => setField('totalParticipants', value)} />
          <NumberField label="Total batches" value={form.totalBatches} onChange={(value) => setField('totalBatches', value)} />
        </div>

        <div className="card-title" style={{ marginTop: 27 }}><div><h2>Questions and timing</h2></div></div>
        <div className="form-grid">
          <NumberField label="Questions per attempt" value={form.questionsPerAttempt} onChange={(value) => setField('questionsPerAttempt', value)} />
          <NumberField label="Challenge duration (seconds)" value={form.challengeDurationSeconds} onChange={(value) => setField('challengeDurationSeconds', value)} />
          <DateField label="Waiting room opens" value={form.waitingRoomOpensAt} onChange={(value) => setField('waitingRoomOpensAt', value)} />
          <DateField label="Challenge starts" value={form.challengeStartsAt} onChange={(value) => setField('challengeStartsAt', value)} />
        </div>
        <div className="form-footer"><button className="button" type="submit" disabled={saving}><Save size={15} />{saving ? 'Saving…' : editing ? 'Save changes' : 'Create draft session'}</button></div>
      </form>
    </>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <div className="field"><label>{label}{children}</label></div>
}

function NumberField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <Field label={label}><input type="number" min="1" step="1" value={value} onChange={(event) => onChange(event.target.value)} /></Field>
}

function DateField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <Field label={label}><input type="datetime-local" value={value} onChange={(event) => onChange(event.target.value)} /></Field>
}
