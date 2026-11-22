import { Outlet, useNavigate } from 'react-router-dom'
import { Ticket } from 'lucide-react'
import { useParticipantAuth } from '../contexts/ParticipantAuthContext'

export function ParticipantLayout() {
  const navigate = useNavigate()
  const { token, clearToken } = useParticipantAuth()

  function exitSession() {
    clearToken()
    navigate('/waiting-room', { replace: true })
  }

  return (
    <div className="participant-shell">
      <header className="participant-topbar">
        <div className="participant-brand"><span className="participant-brand-mark"><Ticket size={19} /></span>Ticket War</div>
        <div className="participant-topbar-actions"><span className="participant-topbar-caption">Live competition experience</span>{token && <button className="button button-secondary button-small" onClick={exitSession}>Exit session</button>}</div>
      </header>
      <main className="participant-content"><Outlet /></main>
      <footer className="participant-footer">Good luck, player! Grab those tickets 🎟</footer>
    </div>
  )
}
