import axios from 'axios'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Award, Clock3, Trophy } from 'lucide-react'
import { bookingApi } from '../../api/bookingApi'
import { participantApi } from '../../api/participantApi'
import { getApiErrorMessage } from '../../api/client'
import { ErrorState, LoadingState, StatusBadge } from '../../components/ui/Feedback'
import { formatDateTime, formatDuration } from '../../utils/date'

export function ResultPage() {
  const queryClient = useQueryClient()
  const resultQuery = useQuery({
    queryKey: ['participant-result'],
    queryFn: participantApi.result,
    refetchInterval: (query) => query.state.data ? false : 10_000,
    refetchIntervalInBackground: false,
    retry: false,
  })
  const bookingQuery = useQuery({
    queryKey: ['participant-booking'],
    queryFn: bookingApi.getMyStatus,
    enabled: resultQuery.data?.result === 'WINNER' || resultQuery.data?.result === 'ALTERNATE',
    refetchInterval: (query) => ['WAITING', 'INVITED'].includes(query.state.data?.status ?? '') ? 5_000 : false,
    refetchIntervalInBackground: false,
    retry: false,
  })
  const claimMutation = useMutation({
    mutationFn: bookingApi.claim,
    onSuccess: (booking) => queryClient.setQueryData(['participant-booking'], booking),
    onError: () => void queryClient.invalidateQueries({ queryKey: ['participant-booking'] }),
  })
  const notFinalized = axios.isAxiosError(resultQuery.error) && resultQuery.error.response?.status === 409
  const result = resultQuery.data

  if (resultQuery.isLoading || (!result && notFinalized)) {
    return <section className="participant-card result-card"><div className="eyebrow">Your result</div><h1>Results are being processed.</h1><p>We’ll check again automatically once the competition is complete.</p>{resultQuery.isLoading ? <LoadingState label="Checking results…" /> : <div className="notice notice-info">Your final result isn’t ready yet.</div>}</section>
  }
  if (resultQuery.isError || !result) {
    return <section className="participant-card result-card"><div className="eyebrow">Competition result</div><h1>Your result could not be loaded.</h1><ErrorState message={getApiErrorMessage(resultQuery.error)} onRetry={() => void resultQuery.refetch()} /></section>
  }

  const resultStatus = result.result
  const markClass = resultStatus === 'WINNER' ? '' : resultStatus === 'ALTERNATE' ? 'alternate' : 'lost'
  const icon = resultStatus === 'WINNER' ? <Trophy size={32} /> : resultStatus === 'ALTERNATE' ? <Award size={32} /> : <Clock3 size={32} />

  return (
    <section className="participant-card result-card">
      <div className={`result-mark ${markClass}`}>{icon}</div>
      <div className="eyebrow">Final result · {formatDateTime(result.leaderboardFinalizedAt)}</div>
      <h1>{resultStatus === 'WINNER' ? 'You won.' : resultStatus === 'ALTERNATE' ? 'You are an alternate.' : 'Competition complete.'}</h1>
      <p>{result.warSessionName}</p>
      <StatusBadge status={resultStatus} />
      <div className="result-details">
        <ResultDetail label="Batch" value={result.batchNumber == null ? '—' : String(result.batchNumber)} />
        <ResultDetail label="Queue position" value={result.queuePosition?.toLocaleString() ?? '—'} />
        <ResultDetail label="Competition rank" value={result.competitionRank?.toLocaleString() ?? '—'} />
        <ResultDetail label="Completion time" value={formatDuration(result.completionDurationMs)} />
      </div>
      {(resultStatus === 'WINNER' || resultStatus === 'ALTERNATE') && <section className="card" style={{ marginTop: 16, textAlign: 'left' }}>
        <div className="card-title"><div><h2>Ticket claim</h2><p>{bookingQuery.data?.message ?? 'Checking for your ticket invitation…'}</p></div></div>
        {bookingQuery.isLoading && <LoadingState label="Checking ticket availability…" />}
        {bookingQuery.isError && <ErrorState message={getApiErrorMessage(bookingQuery.error)} onRetry={() => void bookingQuery.refetch()} />}
        {bookingQuery.data?.status === 'INVITED' && <>
          <div className="notice notice-info">Ticket {bookingQuery.data.ticketNumber} in Batch {bookingQuery.data.batchNumber} · claim by {formatDateTime(bookingQuery.data.expiresAt)}</div>
          {claimMutation.error && <ErrorState message={getApiErrorMessage(claimMutation.error)} />}
          <button className="button" style={{ marginTop: 12 }} disabled={claimMutation.isPending} onClick={() => claimMutation.mutate()}>
            {claimMutation.isPending ? 'Claiming…' : 'Claim ticket'}
          </button>
        </>}
        {bookingQuery.data?.status === 'CLAIMED' && <div className="notice notice-info">Ticket {bookingQuery.data.ticketNumber} in Batch {bookingQuery.data.batchNumber} is claimed.</div>}
        {bookingQuery.data?.status === 'EXPIRED' && <div className="notice notice-warning">This invitation has expired.</div>}
        {bookingQuery.data?.status === 'WAITING' && <div className="notice notice-info">Alternates are offered tickets in rank order when a winner’s claim expires.</div>}
      </section>}
    </section>
  )
}

function ResultDetail({ label, value }: { label: string; value: string }) {
  return <div className="result-detail"><span>{label}</span><strong>{value}</strong></div>
}
