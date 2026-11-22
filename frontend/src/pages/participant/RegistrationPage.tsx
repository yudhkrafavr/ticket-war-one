import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowRight, Mail, ShieldCheck } from 'lucide-react'
import { eventApi } from '../../api/eventApi'
import { getApiErrorMessage } from '../../api/client'
import { participantApi } from '../../api/participantApi'
import { warSessionApi } from '../../api/warSessionApi'
import { useParticipantAuth } from '../../contexts/ParticipantAuthContext'
import { ErrorState, LoadingState, StatusBadge } from '../../components/ui/Feedback'
import { formatDateTime } from '../../utils/date'

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function RegistrationPage() {
  const { warSessionId = '' } = useParams()
  const navigate = useNavigate()
  const { setToken } = useParticipantAuth()
  const [email, setEmail] = useState('')
  const [validationError, setValidationError] = useState('')
  const [accessLinkMessage, setAccessLinkMessage] = useState('')
  const sessionQuery = useQuery({ queryKey: ['war-session', warSessionId], queryFn: () => warSessionApi.get(warSessionId) })
  const eventId = sessionQuery.data?.eventId
  const eventQuery = useQuery({ queryKey: ['event', eventId], queryFn: () => eventApi.get(eventId!), enabled: Boolean(eventId) })
  const registerMutation = useMutation({
    mutationFn: () => participantApi.register(warSessionId, email.trim()),
    onSuccess: (response) => {
      // The backend returns the raw participant token once. Keep it only for this browser session.
      setToken(response.accessToken)
      navigate('/waiting-room')
    },
  })
  const accessLinkMutation = useMutation({
    mutationFn: () => participantApi.requestAccessLink(warSessionId, email.trim()),
    onSuccess: () => setAccessLinkMessage('If this email is registered, a fresh access link will be sent shortly.'),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setValidationError('')
    setAccessLinkMessage('')
    if (!emailPattern.test(email.trim())) return setValidationError('Enter a valid email address.')
    if (sessionQuery.data && sessionQuery.data.status !== 'SCHEDULED') {
      return setValidationError(`Registration is available only for SCHEDULED sessions. This session is ${sessionQuery.data.status}.`)
    }
    registerMutation.mutate()
  }

  function requestAccessLink() {
    setValidationError('')
    setAccessLinkMessage('')
    if (!emailPattern.test(email.trim())) return setValidationError('Enter a valid email address.')
    accessLinkMutation.mutate()
  }

  const session = sessionQuery.data
  return (
    <div className="participant-session-card participant-card">
      <div className="eyebrow">Participant registration</div>
      <h1>Claim your place in the war.</h1>
      <p>Register with your email to receive a one-time participant access session for this competition.</p>
      {sessionQuery.isLoading && <LoadingState label="Loading War Session details…" />}
      {sessionQuery.isError && <ErrorState message={`Session details could not be loaded: ${getApiErrorMessage(sessionQuery.error)}`} onRetry={() => void sessionQuery.refetch()} />}
      {eventQuery.isError && <div className="notice notice-info" style={{ marginTop: 14 }}>Event information is unavailable: {getApiErrorMessage(eventQuery.error)}</div>}
      {session && <>
        <div className="participant-session-meta">
          <div className="participant-meta-item">War Session<strong>{session.name}</strong></div>
          <div className="participant-meta-item">Status<strong><StatusBadge status={session.status} /></strong></div>
          <div className="participant-meta-item">Registration opens<strong>{formatDateTime(session.registrationOpensAt)}</strong></div>
          <div className="participant-meta-item">Registration closes<strong>{formatDateTime(session.registrationClosesAt)}</strong></div>
          {eventQuery.data?.eventDate && <div className="participant-meta-item">Event date<strong>{formatDateTime(eventQuery.data.eventDate)}</strong></div>}
        </div>
        {eventQuery.data?.description && <p>{eventQuery.data.description}</p>}
        {session.status !== 'SCHEDULED' && <div className="notice notice-warning" style={{ marginTop: 14 }}>Registration is open only for scheduled sessions.</div>}
      </>}
      {validationError && <div className="form-error" role="alert">{validationError}</div>}
      {registerMutation.error && <ErrorState message={getApiErrorMessage(registerMutation.error)} />}
      <form onSubmit={submit}>
        <div className="field"><label htmlFor="registration-email">Email address</label><input id="registration-email" type="email" autoComplete="email" required value={email} onChange={(event) => { setEmail(event.target.value); setAccessLinkMessage('') }} placeholder="you@example.com" /></div>
        <button className="button" style={{ width: '100%', marginTop: 15 }} disabled={registerMutation.isPending || accessLinkMutation.isPending || sessionQuery.isLoading || !session || session.status !== 'SCHEDULED'}>
          {registerMutation.isPending ? 'Registering…' : <>Preregister <ArrowRight size={15} /></>}
        </button>
        <button className="button button-secondary" type="button" style={{ width: '100%', marginTop: 12 }} onClick={requestAccessLink} disabled={registerMutation.isPending || accessLinkMutation.isPending || sessionQuery.isLoading || !session}>
          {accessLinkMutation.isPending ? 'Sending link…' : <>Already joined? Email me an access link <Mail size={15} /></>}
        </button>
      </form>
      {accessLinkMessage && <div className="notice notice-info" role="status" style={{ marginTop: 14 }}>{accessLinkMessage}</div>}
      {accessLinkMutation.error && <ErrorState message={getApiErrorMessage(accessLinkMutation.error)} />}
      <div className="secure-note"><ShieldCheck size={15} /><span>The access token is kept in session storage only. It is never displayed or logged.</span></div>
    </div>
  )
}
