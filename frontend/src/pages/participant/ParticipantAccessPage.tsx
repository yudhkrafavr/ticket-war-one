import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ErrorState, LoadingState } from '../../components/ui/Feedback'
import { getApiErrorMessage } from '../../api/client'
import { participantApi } from '../../api/participantApi'
import { useParticipantAuth } from '../../contexts/ParticipantAuthContext'

export function ParticipantAccessPage() {
  const navigate = useNavigate()
  const { setToken } = useParticipantAuth()
  const started = useRef(false)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    if (started.current) return
    started.current = true

    const token = new URLSearchParams(window.location.hash.slice(1)).get('token')
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`)
    if (!token) {
      setErrorMessage('This access link is missing its token. Open the latest link from your registration email.')
      return
    }

    participantApi.validateAccess(token)
      .then(() => {
        setToken(token)
        navigate('/waiting-room', { replace: true })
      })
      .catch((error: unknown) => setErrorMessage(getApiErrorMessage(error)))
  }, [navigate, setToken])

  if (errorMessage) {
    return <section className="participant-card result-card"><div className="eyebrow">Participant access</div><h1>This access link could not be opened.</h1><ErrorState message={errorMessage} /></section>
  }

  return <section className="participant-card result-card"><div className="eyebrow">Participant access</div><h1>Opening your session…</h1><LoadingState label="Checking your secure access link…" /></section>
}
