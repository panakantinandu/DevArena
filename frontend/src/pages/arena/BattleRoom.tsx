import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Copy, Crown, LogOut, Trophy } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Explanation, QuestionCard } from '@/components/QuestionCard'
import { Avatar, Badge, Button, DifficultyTag, ErrorState, Skeleton } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import { useMe } from '@/lib/auth'
import { serverNow } from '@/lib/realtime'
import type { BattleResults, BattleState, BattleView, LiveQuestion, PlayerState } from '@/lib/types'
import { useRoom } from '@/lib/useRoom'
import { cn } from '@/lib/utils'

export default function BattleRoom() {
  const { id } = useParams()
  const qc = useQueryClient()
  const { data: me } = useMe()
  const key = ['battle', id]
  const q = useQuery({ queryKey: key, queryFn: () => api.get<BattleView>(`/api/battles/${id}`) })

  useRoom(`battle:${id}`, (m) => {
    if (m.type === 'ANSWER_SUBMITTED') {
      const { userId } = m.data as { userId: string }
      qc.setQueryData<BattleView>(key, (v) => v && {
        ...v, state: { ...v.state, players: v.state.players.map((p) => (p.user.id === userId ? { ...p, answeredCurrent: true } : p)) },
      })
      return
    }
    if (m.type === 'ERROR') return
    const state = m.data as BattleState
    if (!state?.id) return
    qc.setQueryData<BattleView>(key, (v) => ({
      state,
      // A new round clears your previous answer; otherwise keep it.
      yourAnswerForCurrent: m.type === 'QUESTION_STARTED' ? null : (v?.yourAnswerForCurrent ?? null),
    }))
  }, () => void q.refetch())

  if (q.isLoading) return <Skeleton className="h-[70vh]" />
  if (q.error) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const { state, yourAnswerForCurrent } = q.data!
  const myId = me?.id ?? ''

  return (
    <div className="grid-bg -mx-4 -my-6 min-h-[calc(100dvh-3.5rem)] px-4 py-6 sm:-mx-6 sm:px-6 lg:-mx-10 lg:-my-8 lg:min-h-dvh lg:px-10 lg:py-8">
      <Hud state={state} />
      {(state.status === 'WAITING' || state.status === 'READY') && <Lobby state={state} myId={myId} />}
      {state.status === 'IN_PROGRESS' && <Live state={state} myId={myId} yourAnswer={yourAnswerForCurrent} />}
      {state.status === 'COMPLETED' && <Results state={state} myId={myId} />}
      {state.status === 'CANCELLED' && (
        <div className="panel mx-auto mt-10 max-w-md p-6 text-center">
          <p className="text-xl font-extrabold">This battle was cancelled</p>
          <Link to="/arena" className="mt-4 inline-block"><Button>Back to arena</Button></Link>
        </div>
      )}
    </div>
  )
}

function Hud({ state }: { state: BattleState }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    await navigator.clipboard.writeText(`${location.origin}/arena/join/${state.roomCode}`)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }
  return (
    <header className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b-2 border-line pb-4">
      <div>
        <p className="eyebrow">// battle · {state.categoryLabel}</p>
        <div className="mt-1 flex items-center gap-3">
          <span data-testid="room-code" className="num font-mono text-2xl font-semibold tracking-[0.3em] text-live-text">{state.roomCode}</span>
          {(state.status === 'WAITING' || state.status === 'READY') && (
            <Button size="sm" variant="ghost" onClick={() => void copy()} aria-label="Copy invite link">
              {copied ? <Check className="size-3.5 text-success" /> : <Copy className="size-3.5" />} {copied ? 'Copied' : 'Invite link'}
            </Button>
          )}
        </div>
      </div>
      <div className="flex items-center gap-3">
        <DifficultyTag value={state.difficulty} />
        <Badge>{state.secondsPerQuestion}s / q</Badge>
        {state.status === 'IN_PROGRESS' && (
          <span className="num font-mono text-sm text-muted">
            round <span className="text-text">{state.currentIndex + 1}</span>/{state.questionCount}
          </span>
        )}
      </div>
    </header>
  )
}

function Lobby({ state, myId }: { state: BattleState; myId: string }) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const me = state.players.find((p) => p.user.id === myId)
  const isHost = state.host.id === myId
  const onDone = (v: BattleView) => qc.setQueryData(['battle', state.id], v)
  const ready = useMutation({ mutationFn: (r: boolean) => api.post<BattleView>(`/api/battles/${state.id}/ready`, { ready: r }), onSuccess: onDone, onError: (e) => toast.error(errorMessage(e)) })
  const start = useMutation({ mutationFn: () => api.post<BattleView>(`/api/battles/${state.id}/start`), onSuccess: onDone, onError: (e) => toast.error(errorMessage(e)) })
  const leave = useMutation({ mutationFn: () => api.post<void>(`/api/battles/${state.id}/leave`), onSuccess: () => navigate('/arena') })

  return (
    <div className="mx-auto grid max-w-4xl gap-6 lg:grid-cols-[1.3fr_1fr]">
      <section className="brackets panel p-6" aria-labelledby="players-h">
        <h2 id="players-h" className="text-xl">Players <span className="num font-mono text-sm text-muted">{state.players.length}/8</span></h2>
        <ul className="mt-4 space-y-2">
          {state.players.map((p) => (
            <li key={p.user.id} className="flex items-center gap-3 rounded-ctl border-2 border-line bg-bg/50 px-3 py-2.5">
              <Avatar name={p.user.displayName} url={p.user.avatarUrl} />
              <span className="flex-1 truncate">
                {p.user.displayName} {p.user.id === myId && <span className="text-muted">(you)</span>}
              </span>
              {p.host && <Crown className="size-4 text-warning" aria-label="Host" />}
              <span className={cn('size-2 rounded-full', p.online ? 'bg-success' : 'bg-surface-3')} aria-label={p.online ? 'Online' : 'Offline'} />
              <Badge className={p.ready ? 'border-success/40 text-success' : ''}>{p.ready ? 'ready' : 'not ready'}</Badge>
            </li>
          ))}
        </ul>
        {state.players.length < 2 && (
          <p className="mt-4 text-sm text-muted">Waiting for someone to join. Share the code or invite link above.</p>
        )}
      </section>
      <section className="panel flex flex-col gap-3 p-6">
        <p className="text-sm text-muted">
          {state.questionCount} questions from <span className="text-text">{state.categoryLabel}</span>. Answers lock in, and the
          right answer appears when the round ends.
        </p>
        <Button variant={me?.ready ? 'secondary' : 'live'} loading={ready.isPending} onClick={() => ready.mutate(!me?.ready)}>
          {me?.ready ? 'Not ready' : "I'm ready"}
        </Button>
        {isHost && (
          <Button variant="primary" loading={start.isPending} disabled={state.status !== 'READY'} onClick={() => start.mutate()}>
            Start battle
          </Button>
        )}
        {isHost && state.status !== 'READY' && <p className="text-xs text-muted">Starts when at least two players are ready.</p>}
        {!isHost && <p className="text-xs text-muted">The host starts the battle.</p>}
        <Button variant="ghost" className="mt-auto" loading={leave.isPending} onClick={() => leave.mutate()}>
          <LogOut className="size-4" aria-hidden /> {isHost ? 'Cancel room' : 'Leave'}
        </Button>
      </section>
    </div>
  )
}

function useCountdown(endsAt: string | null | undefined) {
  const [now, setNow] = useState(serverNow())
  useEffect(() => {
    const t = setInterval(() => setNow(serverNow()), 200)
    return () => clearInterval(t)
  }, [])
  return endsAt ? Math.max(0, new Date(endsAt).getTime() - now) : 0
}

function TimerRing({ ms, total }: { ms: number; total: number }) {
  const r = 30
  const c = 2 * Math.PI * r
  const frac = total > 0 ? ms / total : 0
  const urgent = ms <= 5000
  const secs = Math.ceil(ms / 1000)
  return (
    <div className="relative grid size-20 place-items-center" role="timer" aria-live={urgent ? 'assertive' : 'off'} aria-label={`${secs} seconds left`}>
      <svg viewBox="0 0 72 72" className="absolute inset-0 -rotate-90">
        <circle cx="36" cy="36" r={r} fill="none" stroke="var(--surface-3)" strokeWidth="6" />
        <circle cx="36" cy="36" r={r} fill="none" stroke={urgent ? 'var(--danger)' : 'var(--live-text)'} strokeWidth="4"
          strokeDasharray={c} strokeDashoffset={c * (1 - frac)} strokeLinecap="butt" style={{ transition: 'stroke-dashoffset 200ms linear' }} />
      </svg>
      <span className={cn('num font-mono text-2xl font-semibold', urgent ? 'text-danger' : 'text-text')}>{secs}</span>
    </div>
  )
}

function Live({ state, myId, yourAnswer }: { state: BattleState; myId: string; yourAnswer: string | null }) {
  const qc = useQueryClient()
  const [selected, setSelected] = useState<string | null>(null)
  const live = state.live
  const reveal = state.reveal
  // Remember the question after its round closes so the reveal can show it with the answer.
  const [lastLive, setLastLive] = useState<LiveQuestion | null>(live)
  if (live && live !== lastLive) setLastLive(live)
  const ms = useCountdown(live?.endsAt)
  const total = state.secondsPerQuestion * 1000

  useEffect(() => setSelected(null), [state.currentIndex])

  const answer = useMutation({
    mutationFn: (optionId: string) => api.post(`/api/battles/${state.id}/answer`, { questionIndex: state.currentIndex, optionId }),
    onSuccess: (_, optionId) => qc.setQueryData<BattleView>(['battle', state.id], (v) => v && { ...v, yourAnswerForCurrent: optionId }),
    onError: (e) => toast.error(errorMessage(e)),
  })
  const locked = !!yourAnswer
  const myRound = reveal?.results.find((r) => r.userId === myId)

  return (
    <div className="mx-auto grid max-w-6xl gap-6 lg:grid-cols-[1fr_300px]">
      <section className="panel relative p-6 sm:p-8" aria-live="polite">
        {live && (
          <>
            <div className="mb-4 flex items-start justify-between gap-4">
              <p className="eyebrow pt-2">Question {live.index + 1} of {state.questionCount}</p>
              <TimerRing ms={ms} total={total} />
            </div>
            <QuestionCard question={live.question} selected={yourAnswer ?? selected} disabled={locked || ms === 0}
              onSelect={(o) => setSelected(o)} />
            <div className="mt-6 flex items-center gap-3">
              {!locked ? (
                <Button variant="primary" disabled={!selected || ms === 0} loading={answer.isPending}
                  onClick={() => selected && answer.mutate(selected)}>
                  Lock in answer
                </Button>
              ) : (
                <p className="flex items-center gap-2 text-sm text-live-text"><Check className="size-4" aria-hidden /> Locked in. Waiting for the round to end…</p>
              )}
            </div>
          </>
        )}
        {!live && reveal && (
          <RevealPanel state={state} myId={myId} yourAnswer={yourAnswer} myPoints={myRound?.points ?? 0} question={lastLive} />
        )}
        {!live && !reveal && <p className="text-muted">Get ready…</p>}
      </section>
      <Scoreboard players={state.players} myId={myId} />
    </div>
  )
}

function RevealPanel({ state, myId, yourAnswer, myPoints, question }: {
  state: BattleState; myId: string; yourAnswer: string | null; myPoints: number; question: LiveQuestion | null
}) {
  const reveal = state.reveal!
  const result = reveal.results.find((r) => r.userId === myId)
  return (
    <div>
      <p className="eyebrow mb-2">Round {reveal.index + 1} result</p>
      <p className={cn('mb-5 display text-3xl font-extrabold', result?.correct ? 'text-success' : 'text-danger')}>
        {result?.answered ? (result.correct ? `Correct · +${myPoints}` : 'Wrong · +0') : "Time's up · +0"}
      </p>
      {question && question.index === reveal.index && (
        <QuestionCard question={question.question} selected={yourAnswer} correctOptionId={reveal.correctOptionId} compact />
      )}
      <Explanation correct={!!result?.correct} text={reveal.explanation} />
      {!yourAnswer && !result?.answered && <p className="mt-3 text-sm text-muted">No answer was locked in this round.</p>}
      <p className="mt-6 text-sm text-muted">
        {reveal.index + 1 < state.questionCount ? 'Next question in a moment…' : 'Tallying the final scores…'}
      </p>
    </div>
  )
}

function Scoreboard({ players, myId }: { players: PlayerState[]; myId: string }) {
  const sorted = [...players].sort((a, b) => b.score - a.score)
  return (
    <aside className="brackets panel h-fit p-4" aria-label="Scoreboard">
      <p className="eyebrow mb-3">Scoreboard</p>
      <ol className="space-y-2">
        {sorted.map((p, i) => (
          <li key={p.user.id} className={cn('flex items-center gap-3 rounded-ctl px-2 py-2', p.user.id === myId && 'bg-brand/10')}>
            <span className="num w-5 font-mono text-sm text-muted">{i + 1}</span>
            <Avatar name={p.user.displayName} url={p.user.avatarUrl} size={28} />
            <span className="min-w-0 flex-1 truncate text-sm">{p.user.displayName}</span>
            <span className={cn('size-2 rounded-full', p.answeredCurrent ? 'bg-live-text' : 'bg-surface-3')}
              aria-label={p.answeredCurrent ? 'Answered' : 'Thinking'} title={p.answeredCurrent ? 'Answered' : 'Thinking'} />
            <span className="num w-14 text-right font-mono font-semibold">{p.score}</span>
          </li>
        ))}
      </ol>
      <p className="mt-3 text-2xs text-muted">Cyan dot = answered this round. Correctness stays hidden until the round ends.</p>
    </aside>
  )
}

function Results({ state, myId }: { state: BattleState; myId: string }) {
  const results = useQuery({ queryKey: ['battle-results', state.id], queryFn: () => api.get<BattleResults>(`/api/battles/${state.id}/results`) })
  const sorted = [...state.players].sort((a, b) => b.score - a.score)
  const winner = state.players.find((p) => p.user.id === state.winnerId)
  const youWon = state.winnerId === myId
  return (
    <div className="mx-auto max-w-4xl space-y-8">
      <section className="brackets panel p-8 text-center">
        <Trophy className={cn('mx-auto size-10', youWon ? 'text-warning' : 'text-muted')} aria-hidden />
        <p className="mt-3 display text-4xl font-extrabold font-semibold">
          {winner ? (youWon ? 'Victory' : `${winner.user.displayName} wins`) : 'Draw'}
        </p>
        <ol className="mx-auto mt-6 max-w-md space-y-2 text-left">
          {sorted.map((p, i) => (
            <li key={p.user.id} className="flex items-center gap-3 border-b-2 border-line pb-2 last:border-0">
              <span className="num w-6 font-mono text-muted">{i + 1}</span>
              <Avatar name={p.user.displayName} url={p.user.avatarUrl} size={28} />
              <span className="flex-1">{p.user.displayName}{p.user.id === myId && <span className="text-muted"> (you)</span>}</span>
              <span className="num font-mono text-sm text-muted">{p.correctCount}/{state.questionCount}</span>
              <span className="num w-16 text-right font-mono text-lg font-semibold">{p.score}</span>
            </li>
          ))}
        </ol>
        <div className="mt-6 flex justify-center gap-3">
          <Link to="/arena?create=1"><Button variant="primary">Rematch</Button></Link>
          <Link to="/arena"><Button>Arena</Button></Link>
        </div>
      </section>
      <section aria-labelledby="bd-h">
        <h2 id="bd-h" className="mb-3 text-xl">Question breakdown</h2>
        {results.isLoading && <Skeleton className="h-60" />}
        {results.data && (
          <ol className="space-y-3">
            {results.data.questions.map((qb) => {
              const mine = qb.results.find((r) => r.userId === myId)
              return (
                <li key={qb.index} className="panel p-4">
                  <div className="flex items-start gap-3">
                    <span className="num font-mono text-sm text-muted">{qb.index + 1}.</span>
                    <div className="min-w-0 flex-1">
                      <p className="font-medium">{qb.prompt}</p>
                      <p className="mt-1 text-sm"><span className="text-success">Answer:</span> {qb.correctAnswer}</p>
                      <p className="mt-2 text-sm text-muted">{qb.explanation}</p>
                    </div>
                    <Badge className={mine?.correct ? 'border-success/40 text-success' : 'border-danger/40 text-danger'}>
                      {mine?.correct ? `+${mine.points}` : mine?.answered ? 'wrong' : 'no answer'}
                    </Badge>
                  </div>
                </li>
              )
            })}
          </ol>
        )}
      </section>
    </div>
  )
}
