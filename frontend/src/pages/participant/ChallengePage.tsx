import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'
import { AlertTriangle, CheckCircle2, Clock3, LoaderCircle, Send } from 'lucide-react'
import { challengeApi } from '../../api/challengeApi'
import { getApiErrorMessage } from '../../api/client'
import { ErrorState, LoadingState } from '../../components/ui/Feedback'
import { formatCountdown } from '../../utils/date'
import type { ChallengeQuestionResponse } from '../../types/api'

type AnswerMap = Record<string, string>

export function ChallengePage() {
  const navigate = useNavigate()
  const [now, setNow] = useState(Date.now())
  const [serverOffset, setServerOffset] = useState<number | null>(null)
  const [answers, setAnswers] = useState<AnswerMap>({})
  const hydratedAttempt = useRef<string | null>(null)
  const submitStarted = useRef(false)
  const prepareMutation = useMutation({ mutationFn: challengeApi.prepare })
  const startMutation = useMutation({ mutationFn: challengeApi.start })
  const startResponse = startMutation.data
  const challengeQuery = useQuery({
    queryKey: ['challenge', startResponse?.attemptId],
    queryFn: () => challengeApi.get(startResponse!.attemptId),
    enabled: Boolean(startResponse?.attemptId),
    staleTime: Infinity,
    retry: false,
  })
  const submissionMutation = useMutation({
    mutationFn: () => challengeApi.submit(
      challengeQuery.data!.attemptId,
      Object.entries(answers).map(([attemptQuestionId, selectedOptionId]) => ({ attemptQuestionId, selectedOptionId })),
    ),
    onSuccess: () => {
      if (challengeQuery.data) window.sessionStorage.removeItem(answerStorageKey(challengeQuery.data.attemptId))
      navigate('/result')
    },
    onError: () => { submitStarted.current = false },
  })

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 500)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (!prepareMutation.data && !prepareMutation.isPending && !prepareMutation.isError) prepareMutation.mutate()
  }, [prepareMutation.data, prepareMutation.isPending, prepareMutation.isError, prepareMutation.mutate])

  const prepared = prepareMutation.data
  const secondsToStart = prepared ? Math.ceil((new Date(prepared.scheduledStartAt).getTime() - now) / 1000) : null
  const earlyStartError = isScheduledStartNotReached(startMutation.error)
  const canStart = Boolean(prepared && secondsToStart !== null && secondsToStart <= 0 && !startResponse && !startMutation.isPending)

  useEffect(() => {
    if (!canStart || (startMutation.isError && !earlyStartError)) return
    const timer = window.setTimeout(() => startMutation.mutate(), startMutation.isError ? 1500 : 0)
    return () => window.clearTimeout(timer)
  }, [canStart, startMutation.isError, startMutation.isPending, startMutation.mutate, earlyStartError])

  useEffect(() => {
    const challenge = challengeQuery.data
    if (!challenge || hydratedAttempt.current === challenge.attemptId) return
    setServerOffset(new Date(challenge.serverTime).getTime() - Date.now())
    hydratedAttempt.current = challenge.attemptId
    try {
      const stored = window.sessionStorage.getItem(answerStorageKey(challenge.attemptId))
      const parsed = stored ? JSON.parse(stored) as AnswerMap : {}
      const validQuestionIds = new Set(challenge.questions.map((question) => question.attemptQuestionId))
      setAnswers(Object.fromEntries(Object.entries(parsed).filter(([questionId]) => validQuestionIds.has(questionId))))
    } catch {
      setAnswers({})
    }
  }, [challengeQuery.data])

  const challenge = challengeQuery.data
  const remainingSeconds = challenge
    ? serverOffset === null
      ? challenge.remainingSeconds
      : Math.max(0, Math.ceil((new Date(challenge.deadlineAt).getTime() - (now + serverOffset)) / 1000))
    : null
  const locked = submissionMutation.isPending || (remainingSeconds !== null && remainingSeconds <= 0)

  useEffect(() => {
    if (!challenge || serverOffset === null || remainingSeconds !== 0 || challenge.status !== 'IN_PROGRESS' || submitStarted.current) return
    submitStarted.current = true
    submissionMutation.mutate()
  }, [challenge, serverOffset, remainingSeconds, submissionMutation.mutate])

  useEffect(() => {
    if (challenge?.status === 'SUBMITTED') navigate('/result', { replace: true })
  }, [challenge?.status, navigate])

  function selectAnswer(question: ChallengeQuestionResponse, optionId: string) {
    if (locked || !challenge) return
    setAnswers((current) => {
      const next = { ...current, [question.attemptQuestionId]: optionId }
      window.sessionStorage.setItem(answerStorageKey(challenge.attemptId), JSON.stringify(next))
      return next
    })
  }

  function submitAnswers() {
    if (!challenge || submitStarted.current || submissionMutation.isPending) return
    submitStarted.current = true
    submissionMutation.mutate()
  }

  const questions = useMemo(
    () => [...(challenge?.questions ?? [])].sort((a, b) => a.displayOrder - b.displayOrder),
    [challenge?.questions],
  )

  if (prepareMutation.isPending || (!prepared && !prepareMutation.isError)) return <LoadingState label="Preparing your assigned challenge…" />
  if (prepareMutation.isError) return <section className="participant-card"><h1>Your challenge is not ready yet.</h1><ErrorState message={getApiErrorMessage(prepareMutation.error)} onRetry={() => prepareMutation.mutate()} /></section>
  if (prepared && secondsToStart !== null && secondsToStart > 0) {
    return <section className="participant-card challenge-card"><h1>Get ready!</h1><p>Your questions will appear when the challenge starts.</p><div className="countdown-card"><div><span>Challenge starts in</span></div><div className="countdown-value">{formatCountdown(secondsToStart)}</div></div></section>
  }
  if (startMutation.isError && !earlyStartError && !startResponse) return <section className="participant-card challenge-card"><div className="eyebrow">Challenge start</div><h1>We could not start your challenge.</h1><ErrorState message={getApiErrorMessage(startMutation.error)} onRetry={() => startMutation.mutate()} /><p>Your prepared assignment remains on the server. Your answers have not been submitted.</p></section>
  if (!startResponse || startMutation.isPending) return <LoadingState label={earlyStartError ? 'Checking the scheduled start with the server…' : 'Starting your challenge…'} />
  if (challengeQuery.isLoading) return <LoadingState label="Loading assigned questions…" />
  if (challengeQuery.isError || !challenge) return <section className="participant-card challenge-card"><h1>Your questions are unavailable.</h1><ErrorState message={getApiErrorMessage(challengeQuery.error)} onRetry={() => void challengeQuery.refetch()} /></section>

  return (
    <section className="participant-card challenge-card">
      <div className="eyebrow">Ticket War challenge</div>
      <div className="challenge-topline"><span>Choose your answers before time runs out.</span><span><Clock3 size={14} /> {remainingSeconds === null ? '—' : formatCountdown(remainingSeconds)} remaining</span></div>
      {remainingSeconds === 0 && <div className="notice notice-warning" style={{ marginTop: 14 }}><LoaderCircle className="spin" size={15} /> Time is up. Your selected answers are being submitted automatically.</div>}
      {submissionMutation.error && <div style={{ marginTop: 14 }}><ErrorState message={`Submission was not confirmed: ${getApiErrorMessage(submissionMutation.error)}`} /></div>}
      {submissionMutation.error && remainingSeconds === 0 && <div className="notice notice-warning">The deadline has passed, but the server did not confirm submission. Retry now; the frontend will not mark this challenge as submitted without a response.</div>}
      {questions.map((question, index) => <Question key={question.attemptQuestionId} question={question} number={index + 1} total={questions.length} selected={answers[question.attemptQuestionId]} locked={locked} onSelect={(optionId) => selectAnswer(question, optionId)} />)}
      {questions.length === 0 && <div className="notice notice-info">No questions are available for this challenge.</div>}
      <div className="challenge-footer"><span className="field-hint">{Object.keys(answers).length} answer{Object.keys(answers).length === 1 ? '' : 's'} saved in this browser session</span>
        <button className="button" disabled={submissionMutation.isPending || questions.length === 0} onClick={submitAnswers}><Send size={15} />{submissionMutation.isPending ? 'Submitting…' : 'Submit all answers'}</button>
      </div>
      {submissionMutation.error && <button className="button button-secondary" style={{ marginTop: 12 }} disabled={submissionMutation.isPending} onClick={submitAnswers}><AlertTriangle size={14} /> Retry bulk submission</button>}
      {submissionMutation.isSuccess && <div className="notice notice-info"><CheckCircle2 size={15} /> Submission confirmed by the War Service.</div>}
    </section>
  )
}

function Question({ question, number, total, selected, locked, onSelect }: {
  question: ChallengeQuestionResponse
  number: number
  total: number
  selected?: string
  locked: boolean
  onSelect: (optionId: string) => void
}) {
  return (
    <article className="challenge-question-block">
      <div className="question-count">QUESTION {number} <span>OF {total}</span></div>
      <h2 className="challenge-question">{question.questionText}</h2>
      <div className="answer-list">{[...question.options].sort((a, b) => a.optionOrder - b.optionOrder).map((option, index) => (
        <button type="button" key={option.id} className={`answer-option${selected === option.id ? ' selected' : ''}`} aria-pressed={selected === option.id} disabled={locked} onClick={() => onSelect(option.id)}>
          <span className="answer-letter">{String.fromCharCode(65 + index)}</span>{option.optionText}
        </button>
      ))}</div>
    </article>
  )
}

function answerStorageKey(attemptId: string) {
  return `ticket-war.answers.${attemptId}`
}

function isScheduledStartNotReached(error: unknown) {
  if (!axios.isAxiosError(error) || error.response?.status !== 409) return false
  const message = (error.response.data as { message?: unknown } | undefined)?.message
  return typeof message === 'string' && message.toLowerCase().includes('scheduled challenge start time has not arrived')
}
