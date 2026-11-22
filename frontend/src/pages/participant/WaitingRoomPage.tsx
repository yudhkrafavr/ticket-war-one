import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, DoorOpen, LoaderCircle, ShieldCheck } from 'lucide-react'
import { getApiErrorMessage } from '../../api/client'
import { participantApi } from '../../api/participantApi'
import { challengeApi } from '../../api/challengeApi'
import { waitingRoomApi } from '../../api/waitingRoomApi'
import { warSessionApi } from '../../api/warSessionApi'
import { useParticipantAuth } from '../../contexts/ParticipantAuthContext'
import { ErrorState, LoadingState, StatusBadge } from '../../components/ui/Feedback'
import { formatCountdown, formatDateTime } from '../../utils/date'

const terminalStatuses = ['COMPLETED', 'WINNER', 'ALTERNATE', 'LOST', 'CANCELLED']

export function WaitingRoomPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { token } = useParticipantAuth()
  const [now, setNow] = useState(Date.now())
  const profileQuery = useQuery({ queryKey: ['participant-profile'], queryFn: participantApi.profile, enabled: Boolean(token) })
  const sessionId = profileQuery.data?.warSessionId
  const sessionQuery = useQuery({ queryKey: ['war-session', sessionId], queryFn: () => warSessionApi.get(sessionId!), enabled: Boolean(sessionId) })
  const roomQuery = useQuery({
    queryKey: ['waiting-room'],
    queryFn: waitingRoomApi.get,
    enabled: Boolean(token),
    refetchInterval: (query) => {
      const sessionStatus = sessionQuery.data?.status
      if (sessionStatus === 'COMPLETED' || sessionStatus === 'CANCELLED') return false
      return terminalStatuses.includes(query.state.data?.status ?? '') ? false : 4000
    },
    refetchIntervalInBackground: false,
  })
  const enterMutation = useMutation({
    mutationFn: waitingRoomApi.enter,
    onSuccess: async (data) => { await queryClient.setQueryData(['waiting-room'], data) },
  })
  const prepareMutation = useMutation({ mutationFn: challengeApi.prepare })

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    const room = roomQuery.data
    if (!room || !room.batchId || !['WAITING', 'CHALLENGE_IN_PROGRESS'].includes(room.status)) return
    if (!prepareMutation.data && !prepareMutation.isPending && !prepareMutation.isError) prepareMutation.mutate()
  }, [roomQuery.data, prepareMutation.data, prepareMutation.isPending, prepareMutation.isError, prepareMutation.mutate])

  useEffect(() => {
    if (roomQuery.data?.challengeAvailable && prepareMutation.data) navigate('/challenge', { replace: true })
  }, [roomQuery.data?.challengeAvailable, prepareMutation.data, navigate])

  const room = roomQuery.data
  const opensAt = room?.waitingRoomOpensAt ? new Date(room.waitingRoomOpensAt).getTime() : null
  const startsAt = room?.challengeStartsAt ? new Date(room.challengeStartsAt).getTime() : null
  const canEnter = Boolean(room && room.status === 'PREREGISTERED' && opensAt !== null && now >= opensAt)
  const countdown = startsAt === null ? null : Math.ceil((startsAt - now) / 1000)

  return (
    <div className="participant-flow">
      <section className="waiting-hero">
        <div className="waiting-eyebrow">YOU ARE IN THE WAITING ROOM</div>
        <h1>{sessionQuery.data?.name ?? 'Your War Session'}</h1>
        <p>Stay here. Your batch and place in line are assigned automatically.</p>
      </section>
      {profileQuery.isError && <ErrorState message={getApiErrorMessage(profileQuery.error)} onRetry={() => void profileQuery.refetch()} />}
      {roomQuery.isLoading && <div style={{ marginTop: 16 }}><LoadingState label="Connecting to your waiting room…" /></div>}
      {roomQuery.isError && <div style={{ marginTop: 16 }}><ErrorState message={getApiErrorMessage(roomQuery.error)} onRetry={() => void roomQuery.refetch()} /></div>}
      {room && <>
        <div className="waiting-stats">
          <WaitingStat label="Your batch" value={room.batchNumber ? `Batch ${room.batchNumber}` : 'Not assigned'} note={room.totalBatches ? `of ${room.totalBatches} batches` : 'Assigned automatically'} />
          <WaitingStat label="Your queue position" value={room.queuePosition?.toLocaleString() ?? '—'} note="Assigned by the server" />
          <WaitingStat label="Participants in batch" value={room.participantCapacity == null ? `${room.batchParticipantCount}` : `${room.batchParticipantCount} / ${room.participantCapacity}`} note="Current waiting-room count" />
        </div>
        <div className="waiting-status-row"><span>Participant status</span><StatusBadge status={room.status} /><span className="room-refresh"><span className="online-dot" /> Live refresh every 4 seconds</span></div>
        <div className="countdown-card"><div><span>Challenge starts at {formatDateTime(room.challengeStartsAt)}</span><strong className="countdown-label">Time until challenge</strong></div><div className="countdown-value">{countdown === null ? '—' : countdown > 0 ? formatCountdown(countdown) : 'Starting'}</div></div>
        <div className="field-hint" style={{ marginTop: 10 }}>The competition server controls when the challenge begins.</div>
        {room.status === 'PREREGISTERED' && <div className="participant-action-card">
          <div><strong>{opensAt === null ? 'Waiting room schedule unavailable' : canEnter ? 'Your waiting room is open' : 'Waiting room opens later'}</strong><span>{formatDateTime(room.waitingRoomOpensAt)}</span></div>
          <button className="button" disabled={!canEnter || enterMutation.isPending} onClick={() => enterMutation.mutate()}><DoorOpen size={15} />{enterMutation.isPending ? 'Entering…' : 'Enter waiting room'}</button>
        </div>}
        {enterMutation.error && <div style={{ marginTop: 12 }}><ErrorState message={getApiErrorMessage(enterMutation.error)} onRetry={() => enterMutation.mutate()} /></div>}
        {prepareMutation.isPending && <div className="notice notice-info" style={{ marginTop: 13 }}><LoaderCircle className="spin" size={14} /> Getting your challenge ready…</div>}
        {prepareMutation.isError && <div style={{ marginTop: 12 }}><ErrorState message={`Challenge preparation was not confirmed: ${getApiErrorMessage(prepareMutation.error)}`} onRetry={() => prepareMutation.mutate()} /></div>}
        {prepareMutation.data && <div className="notice notice-info" style={{ marginTop: 13 }}><ShieldCheck size={15} /> Your challenge is ready with {prepareMutation.data.totalQuestions} questions.</div>}
        {terminalStatuses.includes(room.status) && <div className="participant-action-card"><div><strong>Your competition state is {room.status.replaceAll('_', ' ')}</strong><span>Open your result page to see finalized results when available.</span></div><button className="button" onClick={() => navigate('/result')}>View result <ArrowRight size={15} /></button></div>}
      </>}
    </div>
  )
}

function WaitingStat({ label, value, note }: { label: string; value: string; note: string }) {
  return <div className="waiting-stat"><span>{label}</span><strong>{value}</strong><small>{note}</small></div>
}

