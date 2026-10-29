import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, ChevronDown, Copy, Lock, Send, UserPlus } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { CodeEditor } from '@/components/CodeEditor'
import { DesignCanvas } from '@/components/DesignCanvas'
import { Avatar, Badge, Button, DifficultyTag, ErrorState, Field, Select, Skeleton, Textarea } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import { serverNow } from '@/lib/realtime'
import type {
  CandidateQuestion, DesignDocument, Diagram, FriendsOverview, GuideQuestion, InterviewerView, RubricCriterion, SessionView,
} from '@/lib/types'
import { useRoom } from '@/lib/useRoom'
import { cn, formatClock } from '@/lib/utils'
import { useCandidateCode, useMirroredCode } from './useLiveCode'

export default function InterviewRoom() {
  const { id = '' } = useParams()
  const qc = useQueryClient()
  const session = useQuery({ queryKey: ['interview', id], queryFn: () => api.get<SessionView>(`/api/interviews/${id}`) })
  const isInterviewer = session.data?.yourRole === 'INTERVIEWER'
  const guide = useQuery({
    queryKey: ['interviewer', id],
    queryFn: () => api.get<InterviewerView>(`/api/interviews/${id}/interviewer`),
    enabled: isInterviewer,
  })

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['interview', id] })
    if (isInterviewer) void qc.invalidateQueries({ queryKey: ['interviewer', id] })
  }
  useRoom(`interview:${id}`, (m) => {
    if (m.type.startsWith('INTERVIEW_') || m.type === 'QUESTION_CHANGED' || m.type === 'PLAYER_JOINED') refresh()
  }, refresh)

  if (session.isLoading) return <Skeleton className="h-[70vh]" />
  if (session.error) return <ErrorState error={session.error} onRetry={() => void session.refetch()} />
  const s = session.data!
  if (s.status === 'COMPLETED') return <Navigate to={`/interviews/${id}/report`} replace />

  return (
    <div className="flex min-h-[calc(100dvh-7rem)] flex-col">
      <RoomHeader s={s} />
      {(s.status === 'CREATED' || s.status === 'LOBBY') && <Lobby s={s} />}
      {s.status === 'ACTIVE' && (isInterviewer
        ? (guide.data ? <InterviewerActive s={s} g={guide.data} /> : <Skeleton className="h-[60vh]" />)
        : <CandidateActive s={s} />)}
      {s.status === 'FEEDBACK' && (isInterviewer
        ? (guide.data ? <EvaluationForm s={s} rubric={guide.data.rubric} /> : <Skeleton className="h-96" />)
        : <Waiting title="Interview finished" body="Your interviewer is writing feedback. The report opens here automatically." />)}
      {s.status === 'CANCELLED' && <Waiting title="This interview was cancelled" body="" action={<Link to="/interviews"><Button>Back</Button></Link>} />}
    </div>
  )
}

function RoomHeader({ s }: { s: SessionView }) {
  const [now, setNow] = useState(serverNow())
  useEffect(() => {
    const t = setInterval(() => setNow(serverNow()), 1000)
    return () => clearInterval(t)
  }, [])
  const left = s.endsAt ? new Date(s.endsAt).getTime() - now : null
  return (
    <header className="mb-5 flex flex-wrap items-center justify-between gap-4 border-b-2 border-line pb-4">
      <div>
        <p className="eyebrow">// interview · you are the {s.yourRole.toLowerCase()}</p>
        <h1 className="mt-1 text-2xl font-semibold">{s.typeLabel}</h1>
      </div>
      <div className="flex flex-wrap items-center gap-4">
        <DifficultyTag value={s.difficulty} />
        <People s={s} />
        {s.status === 'ACTIVE' && left != null && (
          <span role="timer" aria-label="Time remaining"
            className={cn('num font-mono text-xl', left < 0 ? 'text-danger' : left < 5 * 60_000 ? 'text-warning' : 'text-text')}>
            {left < 0 ? `+${formatClock(-left)}` : formatClock(left)}
          </span>
        )}
      </div>
    </header>
  )
}

function People({ s }: { s: SessionView }) {
  const person = (u: SessionView['interviewer'] | null, role: string) => u && (
    <span className="flex items-center gap-2 text-sm">
      <span className="relative">
        <Avatar name={u.displayName} url={u.avatarUrl} size={26} />
        <span className={cn('absolute -bottom-0.5 -right-0.5 size-2.5 rounded-full border-2 border-bg', s.online.includes(u.id) ? 'bg-success' : 'bg-surface-3')}
          aria-label={s.online.includes(u.id) ? 'online' : 'offline'} />
      </span>
      <span className="hidden sm:inline">{u.displayName}</span>
      <span className="font-mono text-2xs text-muted">{role}</span>
    </span>
  )
  return <div className="flex items-center gap-4">{person(s.interviewer, 'ivr')}{person(s.candidate, 'cand')}</div>
}

function Waiting({ title, body, action }: { title: string; body: string; action?: React.ReactNode }) {
  return (
    <div className="panel mx-auto mt-10 max-w-lg p-8 text-center">
      <p className="display text-2xl font-extrabold">{title}</p>
      {body && <p className="mt-2 text-sm text-muted">{body}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

// ------------------------------------------------------------------ lobby

function Lobby({ s }: { s: SessionView }) {
  const qc = useQueryClient()
  const isInterviewer = s.yourRole === 'INTERVIEWER'
  const start = useMutation({
    mutationFn: () => api.post<SessionView>(`/api/interviews/${s.id}/start`),
    onSuccess: (v) => qc.setQueryData(['interview', s.id], v),
    onError: (e) => toast.error(errorMessage(e)),
  })
  const cancel = useMutation({
    mutationFn: () => api.post<SessionView>(`/api/interviews/${s.id}/cancel`),
    onSuccess: (v) => qc.setQueryData(['interview', s.id], v),
  })
  const [copied, setCopied] = useState(false)
  const friends = useQuery({ queryKey: ['friends'], queryFn: () => api.get<FriendsOverview>('/api/friends'), enabled: isInterviewer })
  const [friend, setFriend] = useState('')
  const invite = useMutation({
    mutationFn: () => api.post(`/api/interviews/${s.id}/invite`, { userId: friend }),
    onSuccess: () => toast.success('Invite sent'),
    onError: (e) => toast.error(errorMessage(e)),
  })

  if (!isInterviewer) {
    return <Waiting title="You're in" body={`Waiting for ${s.interviewer.displayName} to start the interview. Take a breath.`} />
  }
  return (
    <div className="mx-auto grid w-full max-w-4xl gap-6 md:grid-cols-2">
      <section className="panel p-6">
        <p className="eyebrow">Invite code</p>
        <div className="mt-2 flex items-center gap-3">
          <span data-testid="room-code" className="num font-mono text-3xl tracking-[0.25em] text-live-text">{s.roomCode}</span>
          <Button size="sm" variant="ghost" aria-label="Copy invite link" onClick={() => {
            void navigator.clipboard.writeText(`${location.origin}/interviews/join/${s.roomCode}`)
            setCopied(true)
            setTimeout(() => setCopied(false), 1500)
          }}>{copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />}</Button>
        </div>
        <p className="mt-2 text-sm text-muted">Only one candidate can join. The room is private to the two of you.</p>
        {friends.data && friends.data.friends.length > 0 && (
          <div className="mt-5 flex items-end gap-2">
            <div className="flex-1">
              <Field id="invite-friend" label="Or invite a friend">
                <Select value={friend} onChange={(e) => setFriend(e.target.value)}>
                  <option value="">Choose…</option>
                  {friends.data.friends.map((f) => <option key={f.user.id} value={f.user.id}>{f.user.displayName}</option>)}
                </Select>
              </Field>
            </div>
            <Button disabled={!friend} loading={invite.isPending} onClick={() => invite.mutate()}>
              <UserPlus className="size-4" aria-hidden /> Invite
            </Button>
          </div>
        )}
      </section>
      <section className="panel flex flex-col gap-3 p-6">
        <p className="text-sm text-muted">
          {s.candidate ? <><span className="text-text">{s.candidate.displayName}</span> has joined.</> : 'Waiting for the candidate to join…'}
        </p>
        <p className="text-sm text-muted">{s.questionCount} questions · {s.durationMinutes} minutes. You&apos;ll see the answer guide; they won&apos;t.</p>
        <Button variant="primary" disabled={s.status !== 'LOBBY'} loading={start.isPending} onClick={() => start.mutate()}>
          Start interview
        </Button>
        <Button variant="ghost" className="mt-auto" loading={cancel.isPending} onClick={() => cancel.mutate()}>Cancel room</Button>
      </section>
    </div>
  )
}

// ------------------------------------------------------------------ candidate

function CandidateActive({ s }: { s: SessionView }) {
  const q = s.current
  if (!q) return <Waiting title="Waiting for the first question" body="" />
  return (
    <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[minmax(320px,0.9fr)_1.4fr]">
      <QuestionBrief q={q} index={s.currentIndex} total={s.questionCount} />
      <section className="panel flex min-h-[480px] flex-col overflow-hidden">
        {q.coding ? <CandidateCode s={s} q={q} /> : q.design ? <CandidateDesign s={s} q={q} /> : <CandidateNotes s={s} q={q} />}
      </section>
    </div>
  )
}

function QuestionBrief({ q, index, total }: { q: CandidateQuestion; index: number; total: number }) {
  return (
    <section className="panel overflow-y-auto p-5" aria-live="polite">
      <p className="eyebrow">Question {index + 1} of {total}</p>
      <h2 className="mt-1 text-xl">{q.title}</h2>
      <p className="mt-3 leading-relaxed">{q.prompt}</p>
      {q.coding && (
        <div className="mt-4 space-y-3 text-sm">
          <p className="leading-relaxed">{q.coding.statement}</p>
          <pre className="overflow-x-auto rounded-ctl border-2 border-line bg-bg p-3 font-mono text-xs leading-5">{q.coding.examples}</pre>
          <div><p className="eyebrow mb-1">Constraints</p><pre className="whitespace-pre-wrap font-mono text-xs text-muted">{q.coding.constraints}</pre></div>
        </div>
      )}
      {q.design && (
        <div className="mt-4 space-y-3 text-sm">
          <p>{q.design.summary}</p>
          <List title="Functional" items={q.design.functionalReqs} />
          <List title="Non-functional" items={q.design.nonFunctionalReqs} />
        </div>
      )}
    </section>
  )
}

function CandidateCode({ s, q }: { s: SessionView; q: CandidateQuestion }) {
  const live = useCandidateCode(s.id, true)
  const submit = useMutation({
    mutationFn: () => api.post(`/api/interviews/${s.id}/code/submissions`, { code: live.currentText(), language: 'JAVA', note: q.title }),
    onSuccess: () => toast.success('Code submitted to your interviewer'),
    onError: (e) => toast.error(errorMessage(e)),
  })
  return (
    <>
      <div className="min-h-0 flex-1">
        {live.initial == null ? <Skeleton className="h-full" /> : <CodeEditor value={live.initial} onChanges={live.onChanges} />}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t-2 border-line px-4 py-3">
        <p className="flex items-center gap-1.5 text-xs text-muted">
          <Lock className="size-3.5" aria-hidden /> Your interviewer sees this live. Code isn&apos;t executed here; it&apos;s reviewed by hand.
        </p>
        <Button size="sm" variant="live" loading={submit.isPending} onClick={() => submit.mutate()}>
          <Send className="size-3.5" aria-hidden /> Submit solution
        </Button>
      </div>
    </>
  )
}

function CandidateDesign({ s, q }: { s: SessionView; q: CandidateQuestion }) {
  const problemId = q.design!.id
  const doc = useQuery({
    queryKey: ['design-doc', problemId, s.id],
    queryFn: () => api.get<DesignDocument | undefined>(`/api/design/documents?problemId=${problemId}&sessionId=${s.id}`),
  })
  const [diagram, setDiagram] = useState<Diagram | null>(null)
  const [notes, setNotes] = useState('')
  useEffect(() => {
    if (doc.data !== undefined && diagram == null) {
      setDiagram(doc.data?.diagram ?? { nodes: [], edges: [] })
      setNotes(doc.data?.notes ?? '')
    }
  }, [doc.data, diagram])
  const save = useMutation({
    mutationFn: () => api.put('/api/design/documents', { problemId, sessionId: s.id, diagram, notes }),
    onError: (e) => toast.error(errorMessage(e)),
  })
  // Autosave so the interviewer's view stays current.
  useEffect(() => {
    if (!diagram) return
    const t = setTimeout(() => save.mutate(), 1200)
    return () => clearTimeout(t)
  }, [diagram, notes])
  if (!diagram) return <Skeleton className="h-full" />
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="min-h-0 flex-1"><DesignCanvas value={diagram} onChange={setDiagram} /></div>
      <div className="border-t-2 border-line p-3">
        <Textarea aria-label="Design notes and trade-offs" rows={3} placeholder="Notes and trade-offs…" value={notes} onChange={(e) => setNotes(e.target.value)} />
        <p className="mt-1 text-2xs text-muted">{save.isPending ? 'Saving…' : 'Autosaved'} · your interviewer sees updates within a few seconds.</p>
      </div>
    </div>
  )
}

function CandidateNotes({ s, q }: { s: SessionView; q: CandidateQuestion }) {
  const key = `iv-notes-${s.id}-${q.sessionQuestionId}`
  const [text, setText] = useState(() => localStorage.getItem(key) ?? '')
  useEffect(() => localStorage.setItem(key, text), [key, text])
  return (
    <div className="flex flex-1 flex-col p-4">
      <label htmlFor="cand-notes" className="eyebrow mb-2">Your scratchpad (private, stays on this device)</label>
      <Textarea id="cand-notes" className="flex-1 font-mono text-sm" value={text} onChange={(e) => setText(e.target.value)}
        placeholder="Jot down your structure before you answer out loud…" />
    </div>
  )
}

// ------------------------------------------------------------------ interviewer

function InterviewerActive({ s, g }: { s: SessionView; g: InterviewerView }) {
  const qc = useQueryClient()
  const current = g.questions.find((q) => q.position === s.currentIndex)
  const next = useMutation({
    mutationFn: () => api.post<SessionView>(`/api/interviews/${s.id}/next`),
    onSuccess: (v) => { qc.setQueryData(['interview', s.id], v); void qc.invalidateQueries({ queryKey: ['interviewer', s.id] }) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const finish = useMutation({
    mutationFn: () => api.post<SessionView>(`/api/interviews/${s.id}/finish`),
    onSuccess: (v) => qc.setQueryData(['interview', s.id], v),
    onError: (e) => toast.error(errorMessage(e)),
  })
  const [confirmFinish, setConfirmFinish] = useState(false)
  const isLast = s.currentIndex + 1 >= s.questionCount

  return (
    <div className="grid min-h-0 flex-1 gap-4 xl:grid-cols-[1fr_1.1fr]">
      <section className="space-y-3 overflow-y-auto" aria-label="Interviewer guide">
        {g.questions.map((q) => <GuideItem key={q.sessionQuestionId} q={q} sessionId={s.id} current={q.position === s.currentIndex} />)}
        <div className="flex flex-wrap gap-2 pt-2">
          {!isLast && <Button variant="primary" loading={next.isPending} onClick={() => next.mutate()}>Next question</Button>}
          {!confirmFinish
            ? <Button variant={isLast ? 'primary' : 'secondary'} onClick={() => setConfirmFinish(true)}>Finish interview</Button>
            : <Button variant="danger" loading={finish.isPending} onClick={() => finish.mutate()}>Confirm finish and write feedback</Button>}
        </div>
      </section>
      <section className="panel flex min-h-[460px] flex-col overflow-hidden" aria-label="Candidate workspace">
        {current?.coding ? <MirroredCode sessionId={s.id} /> :
          current?.design ? <MirroredDesign sessionId={s.id} problemId={current.design.id} /> : (
            <div className="grid flex-1 place-items-center p-6 text-center text-sm text-muted">
              Verbal question: listen, probe with follow-ups, and take notes on the left.
            </div>
          )}
      </section>
    </div>
  )
}

function GuideItem({ q, sessionId, current }: { q: GuideQuestion; sessionId: string; current: boolean }) {
  const [open, setOpen] = useState(current)
  useEffect(() => { if (current) setOpen(true) }, [current])
  const [note, setNote] = useState(q.privateNote ?? '')
  const saved = useRef(q.privateNote ?? '')
  const save = useMutation({
    mutationFn: (body: string) => api.put(`/api/interviews/${sessionId}/notes`, { sessionQuestionId: q.sessionQuestionId, body }),
    onSuccess: (_, body) => { saved.current = body },
  })
  useEffect(() => {
    if (note === saved.current) return
    const t = setTimeout(() => save.mutate(note), 900)
    return () => clearTimeout(t)
  }, [note])

  return (
    <article className={cn('panel', current && 'border-live-text/40')}>
      <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open}
        className="flex w-full cursor-pointer items-center gap-3 px-4 py-3 text-left">
        <span className={cn('num font-mono text-sm', current ? 'text-live-text' : 'text-muted')}>Q{q.position + 1}</span>
        <span className="flex-1 font-medium">{q.title}</span>
        {current && <Badge className="border-live-text/40 text-live-text">asking now</Badge>}
        {!q.askedAt && <Badge>not asked</Badge>}
        <ChevronDown className={cn('size-4 text-muted transition-transform', open && 'rotate-180')} aria-hidden />
      </button>
      {open && (
        <div className="space-y-4 border-t-2 border-line px-4 py-4 text-sm leading-relaxed">
          <p className="text-muted">{q.prompt}</p>
          <List title="Expected concepts" items={q.expectedConcepts} />
          <div><p className="eyebrow mb-1">Strong answer</p><p>{q.answerOutline}</p></div>
          {q.solutionOutline && <div><p className="eyebrow mb-1">Reference solution</p><p>{q.solutionOutline}</p></div>}
          {q.designDiscussionTopics && <List title="Discussion topics" items={q.designDiscussionTopics} />}
          <List title="Common mistakes" items={q.commonMistakes} tone="danger" />
          <List title="Follow-ups" items={[...q.followUps, ...(q.designFollowUps ?? [])]} />
          <div><p className="eyebrow mb-1">Rubric notes</p><p className="text-muted">{q.rubricNotes}</p></div>
          <Field id={`note-${q.sessionQuestionId}`} label="Private notes" hint={save.isPending ? 'Saving…' : 'Only you can see these. Autosaved.'}>
            <Textarea rows={4} value={note} onChange={(e) => setNote(e.target.value)} />
          </Field>
        </div>
      )}
    </article>
  )
}

function MirroredCode({ sessionId }: { sessionId: string }) {
  const code = useMirroredCode(sessionId, true)
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="min-h-0 flex-1"><CodeEditor value={code} readOnly /></div>
      <p className="border-t-2 border-line px-4 py-2 text-xs text-muted">Live view of the candidate&apos;s editor. Not executed; evaluate it by reading.</p>
    </div>
  )
}

function MirroredDesign({ sessionId, problemId }: { sessionId: string; problemId: string }) {
  const doc = useQuery({
    queryKey: ['candidate-doc', sessionId, problemId],
    queryFn: () => api.get<DesignDocument | undefined>(`/api/design/sessions/${sessionId}/candidate-document?problemId=${problemId}`),
    refetchInterval: 4000,
  })
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="min-h-0 flex-1"><DesignCanvas value={doc.data?.diagram ?? { nodes: [], edges: [] }} readOnly /></div>
      <div className="border-t-2 border-line p-3 text-sm">
        <p className="eyebrow mb-1">Candidate notes</p>
        <p className="whitespace-pre-wrap text-muted">{doc.data?.notes || 'Nothing yet.'}</p>
      </div>
    </div>
  )
}

// ------------------------------------------------------------------ evaluation

function EvaluationForm({ s, rubric }: { s: SessionView; rubric: RubricCriterion[] }) {
  const qc = useQueryClient()
  const [scores, setScores] = useState<Record<string, number>>({})
  const [strengths, setStrengths] = useState('')
  const [improvements, setImprovements] = useState('')
  const [summary, setSummary] = useState('')
  const overall = useMemo(() => rubric.reduce((acc, c) => acc + (scores[c.key] ?? 0) * c.weight, 0), [scores, rubric])
  const complete = rubric.every((c) => scores[c.key]) && strengths.trim() && improvements.trim()
  const submit = useMutation({
    mutationFn: () => api.post(`/api/interviews/${s.id}/evaluation`, { ...scores, strengths, improvements, summary }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['interview', s.id] }),
    onError: (e) => toast.error(errorMessage(e)),
  })
  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <section className="panel p-6">
        <h2 className="text-xl">Score {s.candidate?.displayName ?? 'the candidate'}</h2>
        <p className="mt-1 text-sm text-muted">1 = well below expectations, 3 = meets, 5 = exceptional. The overall score is a fixed weighted average.</p>
        <div className="mt-5 space-y-5">
          {rubric.map((c) => (
            <fieldset key={c.key}>
              <legend className="flex w-full justify-between text-sm font-medium">
                <span>{c.label}</span><span className="num font-mono text-2xs text-muted">weight {Math.round(c.weight * 100)}%</span>
              </legend>
              <p className="text-xs text-muted">{c.description}</p>
              <div className="mt-2 flex gap-2">
                {[1, 2, 3, 4, 5].map((n) => (
                  <button key={n} type="button" aria-pressed={scores[c.key] === n} onClick={() => setScores((x) => ({ ...x, [c.key]: n }))}
                    className={cn('num size-11 cursor-pointer rounded-ctl border font-mono',
                      scores[c.key] === n ? 'border-brand-text bg-brand/20 text-text' : 'border-line text-muted hover:border-line-strong')}>
                    {n}
                  </button>
                ))}
              </div>
            </fieldset>
          ))}
        </div>
        <p className="mt-6 font-mono text-sm">Overall: <span className="num text-xl text-live-text">{overall.toFixed(2)}</span> / 5</p>
      </section>
      <section className="panel space-y-4 p-6">
        <Field id="strengths" label="Strengths"><Textarea rows={3} value={strengths} onChange={(e) => setStrengths(e.target.value)} /></Field>
        <Field id="improvements" label="What to improve"><Textarea rows={3} value={improvements} onChange={(e) => setImprovements(e.target.value)} /></Field>
        <Field id="summary" label="Summary (optional)"><Textarea rows={3} value={summary} onChange={(e) => setSummary(e.target.value)} /></Field>
        <p className="text-xs text-muted">The candidate sees your scores and these comments. Your per-question private notes stay private.</p>
        <Button variant="primary" disabled={!complete} loading={submit.isPending} onClick={() => submit.mutate()}>Save feedback</Button>
      </section>
    </div>
  )
}

function List({ title, items, tone }: { title: string; items: string[]; tone?: 'danger' }) {
  if (!items.length) return null
  return (
    <div>
      <p className="eyebrow mb-1">{title}</p>
      <ul className="space-y-1">
        {items.map((x) => (
          <li key={x} className="flex gap-2"><span aria-hidden className={tone === 'danger' ? 'text-danger' : 'text-live-text'}>›</span><span>{x}</span></li>
        ))}
      </ul>
    </div>
  )
}
