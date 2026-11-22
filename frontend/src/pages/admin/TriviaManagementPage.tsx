import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Plus, Save, Trash2, X } from 'lucide-react'
import { getApiErrorMessage } from '../../api/client'
import { triviaApi } from '../../api/triviaApi'
import { warSessionApi } from '../../api/warSessionApi'
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../components/ui/Feedback'
import type { TriviaOptionRequest, TriviaQuestionRequest, TriviaQuestionResponse } from '../../types/api'

interface QuestionForm { id: string | null; questionText: string; questionOrder: string; options: TriviaOptionRequest[] }
const initialOptions = (): TriviaOptionRequest[] => [
  { optionText: '', optionOrder: 1, isCorrect: true },
  { optionText: '', optionOrder: 2, isCorrect: false },
  { optionText: '', optionOrder: 3, isCorrect: false },
  { optionText: '', optionOrder: 4, isCorrect: false },
]

export function TriviaManagementPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const [form, setForm] = useState<QuestionForm | null>(null)
  const [formError, setFormError] = useState('')
  const sessionQuery = useQuery({ queryKey: ['war-session', id], queryFn: () => warSessionApi.get(id) })
  const questionsQuery = useQuery({ queryKey: ['questions', id], queryFn: () => triviaApi.list(id) })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['questions', id] })
  const saveMutation = useMutation({
    mutationFn: ({ request, questionId }: { request: TriviaQuestionRequest; questionId: string | null }) =>
      questionId ? triviaApi.update(id, questionId, request) : triviaApi.create(id, request),
    onSuccess: async () => { setForm(null); await refresh() },
  })
  const deleteMutation = useMutation({ mutationFn: (questionId: string) => triviaApi.delete(id, questionId), onSuccess: refresh })
  const session = sessionQuery.data
  const questions = questionsQuery.data ?? []
  const canEdit = session?.status === 'DRAFT'

  function startCreate() {
    const nextQuestionOrder = Math.max(0, ...questions.map((question) => question.questionOrder)) + 1
    setForm({ id: null, questionText: '', questionOrder: String(nextQuestionOrder), options: initialOptions() })
    setFormError('')
  }

  function startEdit(question: TriviaQuestionResponse) {
    setForm({
      id: question.id,
      questionText: question.questionText,
      questionOrder: String(question.questionOrder),
      options: question.options.map((option) => ({ optionText: option.optionText, optionOrder: option.optionOrder, isCorrect: option.isCorrect })),
    })
    setFormError('')
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!form) return
    setFormError('')
    if (!form.questionText.trim()) return setFormError('Enter the question text.')
    if (form.options.length < 2 || form.options.some((option) => !option.optionText.trim())) {
      return setFormError('Add at least two options and fill in every option.')
    }
    if (form.options.filter((option) => option.isCorrect).length !== 1) return setFormError('Select exactly one correct option.')
    saveMutation.mutate({
      questionId: form.id,
      request: {
        questionText: form.questionText.trim(),
        questionOrder: Number(form.questionOrder),
        options: form.options.map((option, index) => ({ ...option, optionText: option.optionText.trim(), optionOrder: index + 1 })),
      },
    })
  }

  if (sessionQuery.isLoading) return <LoadingState label="Loading War Session…" />
  if (sessionQuery.isError || !session) return <ErrorState message={getApiErrorMessage(sessionQuery.error)} onRetry={() => void sessionQuery.refetch()} />

  return (
    <>
      <PageHeader eyebrow={session.name} title="Trivia management" detail={`${questionsQuery.isLoading ? 'Loading question count…' : questionsQuery.isError ? 'Question count unavailable' : `Question count: ${questions.length}`} · Questions per attempt: ${session.questionsPerAttempt}`}
        action={<><Link className="button button-secondary" to={`/admin/war-sessions/${id}`}><ArrowLeft size={15} /> Session details</Link>{canEdit && <button className="button" onClick={startCreate}><Plus size={15} /> Add question</button>}</>} />
      {saveMutation.error && <ErrorState message={getApiErrorMessage(saveMutation.error)} />}
      {deleteMutation.error && <ErrorState message={getApiErrorMessage(deleteMutation.error)} />}
      <div className="notice notice-info">Add at least 10 questions and choose one correct answer for each.</div>
      {questionsQuery.isLoading ? <LoadingState label="Loading questions…" /> : questionsQuery.isError ? <ErrorState message={getApiErrorMessage(questionsQuery.error)} onRetry={() => void questionsQuery.refetch()} /> : (
        <section className="card">
          <div className="card-title"><div><h2>Question bank</h2><p>{questions.length} configured · {session.questionsPerAttempt} selected for each attempt</p></div></div>
          {!questions.length && <EmptyState title="No questions yet" detail="Add questions with at least two options and exactly one correct answer." />}
          {questions.length > 0 && <div className="grid" style={{ gap: 10 }}>{questions.map((question) => <article className="question-row" key={question.id}>
            <div className="question-heading"><span className="question-number">{String(question.questionOrder).padStart(2, '0')}</span><strong>{question.questionText}</strong><div className="table-actions"><button className="button button-secondary button-small" disabled={!canEdit} onClick={() => startEdit(question)}>Edit</button><button className="button button-danger button-small" disabled={!canEdit || deleteMutation.isPending} onClick={() => { if (window.confirm(`Delete question ${question.questionOrder}?`)) deleteMutation.mutate(question.id) }}><Trash2 size={13} /> Delete</button></div></div>
            <div className="option-summary">{question.options.map((option) => <span key={option.id} className={option.isCorrect ? 'option-summary-correct' : ''}>{option.optionOrder}. {option.optionText}{option.isCorrect ? ' · Correct' : ''}</span>)}</div>
          </article>)}</div>}
          {!canEdit && <div className="field-hint" style={{ marginTop: 13 }}>This session is {session.status}; trivia edits are disabled.</div>}
        </section>
      )}
      {form && canEdit && <div className="modal-backdrop" role="presentation"><section className="modal" role="dialog" aria-modal="true" aria-labelledby="question-dialog-title">
        <div className="card-title"><div><h2 id="question-dialog-title">{form.id ? 'Edit question' : 'New trivia question'}</h2><p>Question order is assigned automatically. Choose exactly one correct option.</p></div><button className="button button-quiet button-small" onClick={() => setForm(null)} aria-label="Close question editor"><X size={16} /></button></div>
        {formError && <div className="form-error" role="alert">{formError}</div>}
        <form onSubmit={submit}>
          <Field label="Question text"><textarea required value={form.questionText} onChange={(event) => setForm({ ...form, questionText: event.target.value })} /></Field>
          <div className="card-title" style={{ marginTop: 19 }}><div><h2>Answer options</h2><p>Choose the radio button beside the single correct answer.</p></div><button type="button" className="button button-secondary button-small" onClick={() => setForm({ ...form, options: [...form.options, { optionText: '', optionOrder: form.options.length + 1, isCorrect: false }] })}><Plus size={13} /> Add option</button></div>
          <div className="option-editor">{form.options.map((option, index) => <div className="option-row" key={index}>
            <input className="correct-toggle" type="radio" name="correct-option" checked={option.isCorrect} aria-label={`Mark option ${index + 1} correct`} onChange={() => setForm({ ...form, options: form.options.map((item, itemIndex) => ({ ...item, isCorrect: itemIndex === index })) })} />
            <input required maxLength={500} aria-label={`Option ${index + 1} text`} value={option.optionText} placeholder={`Option ${index + 1}`} onChange={(event) => setForm({ ...form, options: form.options.map((item, itemIndex) => itemIndex === index ? { ...item, optionText: event.target.value } : item) })} />
            <button type="button" className="button button-danger button-small" disabled={form.options.length <= 2} aria-label={`Remove option ${index + 1}`} onClick={() => { const options = form.options.filter((_, itemIndex) => itemIndex !== index).map((item, itemIndex) => ({ ...item, optionOrder: itemIndex + 1 })); if (!options.some((item) => item.isCorrect)) options[0].isCorrect = true; setForm({ ...form, options }) }}><X size={14} /></button>
          </div>)}</div>
          <div className="form-footer"><button type="button" className="button button-secondary" onClick={() => setForm(null)}>Cancel</button><button className="button" disabled={saveMutation.isPending}><Save size={14} />{saveMutation.isPending ? 'Saving…' : 'Save question'}</button></div>
        </form>
      </section></div>}
    </>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <div className="field"><label>{label}{children}</label></div>
}
