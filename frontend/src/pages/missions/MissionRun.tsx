import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, CheckCircle2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { Explanation, QuestionCard } from '@/components/QuestionCard'
import { Badge, Button, DifficultyTag, ErrorState, Field, PageHeader, Skeleton, Textarea } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import type { MissionDetail, MissionSubmitResult } from '@/lib/types'
import { cn } from '@/lib/utils'

export default function MissionRun() {
  const { id } = useParams()
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['mission', id], queryFn: () => api.get<MissionDetail>(`/api/missions/${id}`) })
  const submit = useMutation({
    mutationFn: (body: object) => api.post<MissionSubmitResult>(`/api/missions/${id}/submit`, body),
    onSuccess: (r) => {
      qc.setQueryData(['mission', id], r.detail)
      void qc.invalidateQueries({ queryKey: ['missions'] })
      void qc.invalidateQueries({ queryKey: ['dashboard'] })
      if (r.firstCompletion) {
        toast.success(`Mission complete · +${r.xpAwarded} XP`)
        r.unlockedAchievements.forEach((a) => toast(`Achievement unlocked: ${a.replaceAll('_', ' ').toLowerCase()}`))
      } else {
        toast('Already completed. Showing your review (no extra XP).')
      }
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  if (q.isLoading) return <Skeleton className="h-96" />
  if (q.error) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const d = q.data!

  return (
    <>
      <Link to="/missions" className="mb-4 inline-flex min-h-11 items-center gap-1.5 text-sm text-muted hover:text-text">
        <ArrowLeft className="size-4" aria-hidden /> All missions
      </Link>
      <PageHeader
        eyebrow={`// ${d.card.revision ? 'revision' : d.card.categoryLabel} · ${d.card.estimatedMinutes} min`}
        title={d.card.title}
        actions={<><DifficultyTag value={d.card.difficulty} />{d.card.completed && <Badge className="border-success/40 text-success">completed</Badge>}</>}
      />
      {d.card.kind === 'QUESTION_SET' && <QuestionSet d={d} onSubmit={(answers) => submit.mutate({ answers })} pending={submit.isPending} />}
      {d.card.kind !== 'QUESTION_SET' && <StudyMission d={d} onSubmit={(notes, selfRating) => submit.mutate({ notes, selfRating })} pending={submit.isPending} />}
    </>
  )
}

function QuestionSet({ d, onSubmit, pending }: { d: MissionDetail; onSubmit: (a: Record<string, string>) => void; pending: boolean }) {
  const questions = d.questions ?? []
  const storageKey = `mission-draft-${d.card.id}`
  const [answers, setAnswers] = useState<Record<string, string>>(() => {
    try { return JSON.parse(sessionStorage.getItem(storageKey) ?? '{}') } catch { return {} }
  })
  const [i, setI] = useState(0)
  const review = d.review
  useEffect(() => { if (!review) sessionStorage.setItem(storageKey, JSON.stringify(answers)) }, [answers, review, storageKey])
  useEffect(() => { if (review) sessionStorage.removeItem(storageKey) }, [review, storageKey])

  const q = questions[i]
  const r = review?.find((x) => x.questionId === q?.id)
  const answered = Object.keys(answers).length
  const allAnswered = questions.every((x) => answers[x.id])

  if (!q) return null
  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_220px]">
      <section className="panel p-5 sm:p-7" aria-live="polite">
        <QuestionCard
          question={q}
          selected={r ? r.yourOptionId : answers[q.id] ?? null}
          correctOptionId={r?.correctOptionId}
          onSelect={(opt) => setAnswers((a) => ({ ...a, [q.id]: opt }))}
          header={<span className="num font-mono text-xs text-live-text">Q{i + 1}/{questions.length}</span>}
        />
        {r && <Explanation correct={r.correct} text={r.explanation} />}
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <Button variant="ghost" disabled={i === 0} onClick={() => setI(i - 1)}>
            <ArrowLeft className="size-4" aria-hidden /> Previous
          </Button>
          {i < questions.length - 1 ? (
            <Button onClick={() => setI(i + 1)}>Next <ArrowRight className="size-4" aria-hidden /></Button>
          ) : !review ? (
            <Button variant="primary" loading={pending} disabled={!allAnswered} onClick={() => onSubmit(answers)}>
              Submit {answered}/{questions.length}
            </Button>
          ) : (
            <Link to="/missions"><Button variant="primary">Back to missions</Button></Link>
          )}
        </div>
      </section>
      <aside>
        <p className="eyebrow mb-2">Questions</p>
        <ol className="grid grid-cols-5 gap-2 lg:grid-cols-4">
          {questions.map((x, idx) => {
            const rv = review?.find((y) => y.questionId === x.id)
            return (
              <li key={x.id}>
                <button type="button" onClick={() => setI(idx)} aria-current={idx === i ? 'step' : undefined}
                  aria-label={`Question ${idx + 1}${rv ? (rv.correct ? ', correct' : ', incorrect') : answers[x.id] ? ', answered' : ''}`}
                  className={cn('num grid h-11 w-full cursor-pointer place-items-center rounded-ctl border font-mono text-sm',
                    idx === i ? 'border-live-text text-live-text' : 'border-line text-muted hover:border-line-strong',
                    rv?.correct && 'bg-success/15 text-success', rv && !rv.correct && 'bg-danger/15 text-danger',
                    !rv && answers[x.id] && idx !== i && 'bg-surface-2 text-text')}>
                  {idx + 1}
                </button>
              </li>
            )
          })}
        </ol>
        {review && (
          <p className="mt-4 flex items-center gap-2 text-sm">
            <CheckCircle2 className="size-4 text-success" aria-hidden />
            <span className="num font-mono">{review.filter((x) => x.correct).length}/{review.length}</span> correct
          </p>
        )}
        <p className="mt-4 text-xs text-muted">Tip: press A–D to pick an option.</p>
      </aside>
    </div>
  )
}

function StudyMission({ d, onSubmit, pending }: { d: MissionDetail; onSubmit: (notes: string, rating: number) => void; pending: boolean }) {
  const done = d.card.completed
  const min = d.card.kind === 'DESIGN_STUDY' ? 150 : 60
  const [notes, setNotes] = useState(d.yourResponse?.notes ?? '')
  const [rating, setRating] = useState<number>(d.yourResponse?.selfRating ?? 0)
  const item = d.interviewItems?.[0]
  const design = d.design

  return (
    <div className="grid gap-8 lg:grid-cols-2">
      <section className="panel p-6">
        {item && (
          <>
            <p className="eyebrow">{item.type}</p>
            <h2 className="mt-1 text-xl">{item.title}</h2>
            <p className="mt-3 leading-relaxed">{item.prompt}</p>
          </>
        )}
        {design && (
          <>
            <p className="eyebrow">System design</p>
            <h2 className="mt-1 text-xl">{design.title}</h2>
            <p className="mt-3 leading-relaxed">{design.summary}</p>
            <ReqList title="Functional requirements" items={design.functionalReqs} />
            <ReqList title="Non-functional requirements" items={design.nonFunctionalReqs} />
          </>
        )}
        <div className="mt-6 space-y-4">
          <Field id="notes" label="Your answer" hint={done ? undefined : `Write it as you'd say it in an interview, at least ${min} characters. The guide unlocks after you submit.`}>
            <Textarea rows={9} value={notes} readOnly={done} onChange={(e) => setNotes(e.target.value)} />
          </Field>
          <fieldset>
            <legend className="mb-2 text-sm font-medium">How confident are you in this answer?</legend>
            <div className="flex gap-2">
              {[1, 2, 3, 4, 5].map((n) => (
                <button key={n} type="button" disabled={done} aria-pressed={rating === n} onClick={() => setRating(n)}
                  className={cn('num size-11 cursor-pointer rounded-ctl border font-mono disabled:cursor-default',
                    rating === n ? 'border-brand-text bg-brand/20 text-text' : 'border-line text-muted hover:border-line-strong')}>
                  {n}
                </button>
              ))}
            </div>
          </fieldset>
          {!done && (
            <Button variant="primary" loading={pending} disabled={notes.trim().length < min || rating === 0}
              onClick={() => onSubmit(notes, rating)}>
              Submit &amp; reveal guide
            </Button>
          )}
          {!done && <p className="num font-mono text-2xs text-muted">{notes.trim().length}/{min} characters</p>}
        </div>
      </section>
      <section aria-label="Answer guide" className={cn('panel p-6', !done && 'grid place-items-center border-dashed')}>
        {!done ? (
          <p className="max-w-xs text-center text-sm text-muted">
            The answer guide stays hidden until you&apos;ve committed to your own answer. That&apos;s what makes the practice count.
          </p>
        ) : (
          <div className="space-y-5 text-sm leading-relaxed">
            {item?.expectedConcepts && <ReqList title="Expected concepts" items={item.expectedConcepts} />}
            {item?.answerOutline && (
              <div><p className="eyebrow mb-1.5">Strong answer outline</p><p>{item.answerOutline}</p></div>
            )}
            {item?.commonMistakes && <ReqList title="Common mistakes" items={item.commonMistakes} tone="danger" />}
            {item?.followUps && <ReqList title="Likely follow-ups" items={item.followUps} />}
            {design?.discussionTopics && <ReqList title="What a strong design covers" items={design.discussionTopics} />}
            {design?.followUps && <ReqList title="Likely follow-ups" items={design.followUps} />}
          </div>
        )}
      </section>
    </div>
  )
}

function ReqList({ title, items, tone }: { title: string; items: string[]; tone?: 'danger' }) {
  return (
    <div className="mt-4 first:mt-0">
      <p className="eyebrow mb-1.5">{title}</p>
      <ul className="space-y-1.5">
        {items.map((x) => (
          <li key={x} className="flex gap-2 text-sm">
            <span aria-hidden className={tone === 'danger' ? 'text-danger' : 'text-live-text'}>›</span>
            <span>{x}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
