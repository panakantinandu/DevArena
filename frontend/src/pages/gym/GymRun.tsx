import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Check, Eye, Flag, Trophy, X } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { Badge, Button, DifficultyTag, ErrorState, Field, Input, Skeleton } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import { cn } from '@/lib/utils'
import { MODE_INFO, parseAmount, sci, type AnswerResult, type PuzzleView, type RunView } from './model'

export default function GymRun() {
  const { id = '' } = useParams()
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['gym-run', id], queryFn: () => api.get<RunView>(`/api/gym/runs/${id}`) })
  const [index, setIndex] = useState<number | null>(null)

  if (q.isLoading) return <Skeleton className="h-[70vh]" />
  if (q.error) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const run = q.data!
  const firstOpen = run.puzzles.findIndex((p) => !p.answer)
  const i = index ?? (firstOpen === -1 ? 0 : firstOpen)
  const p = run.puzzles[i]

  const onAnswered = (r: AnswerResult) => {
    // Stay on this puzzle so the verdict and explanation are read before moving on.
    setIndex(i)
    qc.setQueryData<RunView>(['gym-run', id], (v) => v && {
      ...v, status: r.runStatus as RunView['status'], totalScore: r.totalScore,
      puzzles: v.puzzles.map((x) => (x.id === r.puzzle.id ? r.puzzle : x)),
    })
    void qc.invalidateQueries({ queryKey: ['gym'] })
    if (r.runStatus === 'COMPLETED' && r.xpAwarded > 0) toast.success(`Run complete · ${r.totalScore}/100 · +${r.xpAwarded} XP`)
  }

  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Link to="/gym" className="inline-flex min-h-11 items-center gap-1.5 text-sm font-semibold text-muted hover:text-text">
          <ArrowLeft className="size-4" aria-hidden /> Brain gym
        </Link>
        <ol className="flex gap-2" aria-label="Puzzles in this run">
          {run.puzzles.map((x, n) => (
            <li key={x.id}>
              <button type="button" onClick={() => setIndex(n)} aria-current={n === i ? 'step' : undefined}
                aria-label={`Puzzle ${n + 1}: ${MODE_INFO[x.mode].label}${x.answer ? `, scored ${x.answer.score}` : ''}`}
                className={cn('num grid h-11 min-w-11 cursor-pointer place-items-center rounded-ctl border-2 border-line px-2 font-mono text-sm font-bold',
                  n === i && 'shadow-[3px_3px_0_0_var(--shadow)]',
                  x.answer ? (x.answer.score >= 70 ? 'bg-[#86efac] text-ink' : x.answer.score >= 40 ? 'bg-brand text-ink' : 'bg-[#ff8a70] text-ink') : 'bg-surface-1')}>
                {x.answer ? x.answer.score : n + 1}
              </button>
            </li>
          ))}
        </ol>
      </div>

      {run.status === 'COMPLETED' && (
        <div className="panel mb-6 flex flex-wrap items-center justify-between gap-4 bg-brand p-5 text-ink">
          <div className="flex items-center gap-3">
            <Trophy className="size-7" aria-hidden />
            <div>
              <p className="font-mono text-2xs font-bold uppercase tracking-widest">{run.kind === 'DAILY' ? `Daily · ${run.runDate}` : 'Practice run'} complete</p>
              <p className="display text-3xl font-extrabold">{run.totalScore}/100</p>
            </div>
          </div>
          <p className="max-w-sm text-sm font-semibold">Read each solution, especially the ones where you were confident and wrong. That&apos;s where calibration improves.</p>
        </div>
      )}

      <article className="panel p-6 sm:p-8" key={p.id}>
        <header className="mb-5 flex flex-wrap items-center gap-2">
          <Badge className="bg-brand text-ink">{MODE_INFO[p.mode].label}</Badge>
          <DifficultyTag value={p.difficulty} />
          <span className="eyebrow ml-auto">puzzle {i + 1} of {run.puzzles.length}</span>
        </header>
        <h1 className="text-[1.6rem] font-extrabold">{p.title}</h1>
        <div className="mt-4">
          {p.mode === 'ESTIMATION' && <Estimation runId={run.id} p={p} onAnswered={onAnswered} />}
          {p.mode === 'INCIDENT' && <Incident runId={run.id} p={p} onAnswered={onAnswered} />}
          {p.mode === 'REVIEW' && <Review runId={run.id} p={p} onAnswered={onAnswered} />}
          {p.mode === 'TRADEOFF' && <Tradeoff runId={run.id} p={p} onAnswered={onAnswered} />}
        </div>
        {p.answer && <Verdict p={p} />}
        <footer className="mt-6 flex justify-between">
          <Button variant="ghost" disabled={i === 0} onClick={() => setIndex(i - 1)}><ArrowLeft className="size-4" aria-hidden /> Previous</Button>
          <Button disabled={i === run.puzzles.length - 1} onClick={() => setIndex(i + 1)}>Next <ArrowRight className="size-4" aria-hidden /></Button>
        </footer>
      </article>
    </div>
  )
}

// ------------------------------------------------------------------ shared

type PuzzleProps = { runId: string; p: PuzzleView; onAnswered: (r: AnswerResult) => void }

function useAnswer(runId: string, p: PuzzleView, onAnswered: (r: AnswerResult) => void) {
  return useMutation({
    mutationFn: (body: { response: unknown; confidence: number }) =>
      api.post<AnswerResult>(`/api/gym/runs/${runId}/puzzles/${p.id}/answer`, body),
    onSuccess: onAnswered,
    onError: (e) => toast.error(errorMessage(e)),
  })
}

function Confidence({ value, onChange, disabled, prompt }: { value: number; onChange: (v: number) => void; disabled?: boolean; prompt: string }) {
  return (
    <div className="rounded-ctl border-2 border-line bg-surface-2 p-4">
      <label htmlFor="confidence" className="flex items-baseline justify-between text-sm font-bold">
        <span>{prompt}</span>
        <span className="num font-mono text-lg">{value}%</span>
      </label>
      <input id="confidence" type="range" min={50} max={99} step={1} value={value} disabled={disabled}
        onChange={(e) => onChange(Number(e.target.value))}
        className="mt-2 w-full cursor-pointer accent-[var(--live)]" aria-valuetext={`${value} percent`} />
      <div className="mt-1 flex justify-between font-mono text-2xs text-muted"><span>50% coin flip</span><span>99% certain</span></div>
    </div>
  )
}

function Verdict({ p }: { p: PuzzleView }) {
  const a = p.answer!
  return (
    <section aria-live="polite" className={cn('mt-6 rounded-ctl border-2 border-line p-5',
      a.correct ? 'shadow-[5px_5px_0_0_var(--success)]' : 'shadow-[5px_5px_0_0_var(--danger)]')}>
      <div className="flex flex-wrap items-center gap-3">
        <span className={cn('grid size-9 place-items-center rounded-full border-2 border-line text-ink', a.correct ? 'bg-[#86efac]' : 'bg-[#ff8a70]')}>
          {a.correct ? <Check className="size-5" aria-hidden /> : <X className="size-5" aria-hidden />}
        </span>
        <p className="display text-2xl font-extrabold">{a.score}/100</p>
        <p className="text-sm text-muted">
          You were {a.confidence}% sure and {a.correct ? 'got it' : "didn't get it"}.
          {!a.correct && a.confidence >= 80 && ' Overconfidence on a miss is the most expensive habit for a senior engineer.'}
          {a.correct && a.confidence <= 60 && ' You knew more than you thought, so trust that next time.'}
        </p>
      </div>
      <p className="mt-4 leading-relaxed">{a.explanation}</p>
    </section>
  )
}

// ------------------------------------------------------------------ estimation

function Estimation({ runId, p, onAnswered }: PuzzleProps) {
  const done = p.answer
  const [value, setValue] = useState('')
  const [low, setLow] = useState('')
  const [high, setHigh] = useState('')
  const [conf, setConf] = useState(70)
  const submit = useAnswer(runId, p, onAnswered)
  const v = parseAmount(value), l = parseAmount(low), h = parseAmount(high)
  const rangeError = v && l && h && !(l <= v && v <= h) ? 'Your best guess must sit inside your range.' : undefined

  if (done) {
    const truth = done.solution.answer as number
    const r = done.response as { value: number; low: number; high: number }
    return (
      <div>
        <p className="text-lg leading-relaxed">{p.payload.prompt}</p>
        <dl className="mt-5 grid gap-3 sm:grid-cols-3">
          <Stat label="Answer" value={sci(truth)} sub={p.payload.unit} strong />
          <Stat label="Your guess" value={sci(r.value)} sub={`${done.detail.factorOff}× ${done.detail.direction}`} />
          <Stat label="Your range" value={`${sci(r.low)} – ${sci(r.high)}`} sub={done.detail.rangeContainsTruth ? 'contains the answer' : 'missed the answer'} />
        </dl>
      </div>
    )
  }
  return (
    <form noValidate onSubmit={(e) => { e.preventDefault(); if (v && l && h && !rangeError) submit.mutate({ response: { value: v, low: l, high: h }, confidence: conf }) }}>
      <p className="text-lg leading-relaxed">{p.payload.prompt}</p>
      <p className="mt-2 text-sm text-muted">Unit: <b>{p.payload.unit}</b>. Type 23000, 2.3e4, 23k, 1.5M or 2bn.</p>
      <div className="mt-5 grid gap-4 sm:grid-cols-3">
        <AmountField id="best" label="Best guess" value={value} setValue={setValue} parsed={v} />
        <AmountField id="low" label="Low (90% sure it's above)" value={low} setValue={setLow} parsed={l} />
        <AmountField id="high" label="High (90% sure it's below)" value={high} setValue={setHigh} parsed={h} error={rangeError} />
      </div>
      <p className="mt-2 text-xs text-muted">Scoring: closeness in orders of magnitude (70%) plus a range that contains the answer, tighter scoring higher (30%).</p>
      <div className="mt-5"><Confidence value={conf} onChange={setConf} prompt="How sure are you that your guess is within 2× of the answer?" /></div>
      <Button type="submit" variant="primary" className="mt-5" loading={submit.isPending} disabled={!v || !l || !h || !!rangeError}>Lock in estimate</Button>
    </form>
  )
}

function AmountField({ id, label, value, setValue, parsed, error }: { id: string; label: string; value: string; setValue: (v: string) => void; parsed: number | null; error?: string }) {
  return (
    <Field id={id} label={label} error={error ?? (value && !parsed ? 'Not a positive number' : undefined)} hint={parsed ? `= ${sci(parsed)}` : ' '}>
      <Input inputMode="decimal" autoComplete="off" value={value} onChange={(e) => setValue(e.target.value)} className="font-mono" />
    </Field>
  )
}

function Stat({ label, value, sub, strong }: { label: string; value: string; sub?: string; strong?: boolean }) {
  return (
    <div className={cn('rounded-ctl border-2 border-line p-3', strong ? 'bg-brand text-ink' : 'bg-surface-1')}>
      <dt className={cn('font-mono text-2xs font-bold uppercase tracking-wider', !strong && 'text-muted')}>{label}</dt>
      <dd className="num mt-1 font-mono text-lg font-bold">{value}</dd>
      {sub && <dd className={cn('text-xs', !strong && 'text-muted')}>{sub}</dd>}
    </div>
  )
}

// ------------------------------------------------------------------ incident

function Incident({ runId, p, onAnswered }: PuzzleProps) {
  const qc = useQueryClient()
  const done = p.answer
  const [root, setRoot] = useState<string | null>(null)
  const [action, setAction] = useState<string | null>(null)
  const [conf, setConf] = useState(70)
  const submit = useAnswer(runId, p, onAnswered)
  const reveal = useMutation({
    mutationFn: (clueId: string) => api.post<{ clueId: string; body: string }>(`/api/gym/runs/${runId}/puzzles/${p.id}/clues/${clueId}`),
    onSuccess: (c) => qc.setQueryData<RunView>(['gym-run', runId], (v) => v && {
      ...v, puzzles: v.puzzles.map((x) => (x.id === p.id ? { ...x, revealedClues: { ...x.revealedClues, [c.clueId]: c.body } } : x)),
    }),
    onError: (e) => toast.error(errorMessage(e)),
  })
  const pl = p.payload
  const spent = (pl.clues as { id: string; cost: number }[]).filter((c) => p.revealedClues[c.id]).reduce((s, c) => s + c.cost, 0)
  const chosenRoot = done ? (done.response.rootCause as string) : root
  const chosenAction = done ? (done.response.action as string) : action

  return (
    <div>
      <p className="text-lg leading-relaxed">{pl.scenario}</p>
      <ul className="mt-3 flex flex-wrap gap-2">
        {(pl.symptoms as string[]).map((s) => <li key={s}><Badge>{s}</Badge></li>)}
      </ul>

      <section className="mt-6" aria-labelledby="clues-h">
        <div className="flex items-baseline justify-between">
          <h2 id="clues-h" className="text-lg">Investigate</h2>
          <span className="num font-mono text-sm">clue cost so far: <b>−{spent}</b></span>
        </div>
        <ul className="mt-3 grid gap-3 sm:grid-cols-2">
          {(pl.clues as { id: string; title: string; cost: number }[]).map((c) => {
            const body = p.revealedClues[c.id] ?? (done ? done.solution.clues?.[c.id] : undefined)
            const wasRevealed = !!p.revealedClues[c.id]
            return (
              <li key={c.id} className={cn('rounded-ctl border-2 border-line p-3', wasRevealed ? 'bg-surface-1' : 'bg-surface-2')}>
                <div className="flex items-center justify-between gap-2">
                  <p className="font-bold">{c.title}</p>
                  {!body && !done && (
                    <Button size="sm" loading={reveal.isPending && reveal.variables === c.id} onClick={() => reveal.mutate(c.id)}>
                      <Eye className="size-3.5" aria-hidden /> Reveal −{c.cost}
                    </Button>
                  )}
                  {wasRevealed && <Badge>−{c.cost}</Badge>}
                </div>
                {body && <p className="mt-2 font-mono text-xs leading-relaxed">{body}</p>}
                {done && !wasRevealed && <p className="mt-1 text-2xs text-muted">(not revealed during the run)</p>}
              </li>
            )
          })}
        </ul>
      </section>

      <ChoiceGroup legend="Root cause" options={pl.rootCauses} value={chosenRoot} onChange={setRoot} disabled={!!done}
        correct={done?.solution.rootCause} />
      <ChoiceGroup legend="First action" options={pl.actions} value={chosenAction} onChange={setAction} disabled={!!done}
        correct={done?.solution.action} />

      {!done && (
        <>
          <p className="mt-3 text-xs text-muted">Scoring: root cause 70 + first action 30, minus the cost of the clues you revealed.</p>
          <div className="mt-4"><Confidence value={conf} onChange={setConf} prompt="How sure are you about the root cause?" /></div>
          <Button variant="primary" className="mt-5" disabled={!root || !action} loading={submit.isPending}
            onClick={() => submit.mutate({ response: { rootCause: root, action }, confidence: conf })}>Submit diagnosis</Button>
        </>
      )}
    </div>
  )
}

function ChoiceGroup({ legend, options, value, onChange, disabled, correct, scores }: {
  legend: string; options: { id: string; text?: string; label?: string; description?: string }[]; value: string | null
  onChange: (v: string) => void; disabled: boolean; correct?: string; scores?: Record<string, { score: number; rationale: string }>
}) {
  return (
    <fieldset className="mt-6">
      <legend className="mb-2 text-lg font-extrabold">{legend}</legend>
      <div className="space-y-2" role="radiogroup" aria-label={legend}>
        {options.map((o) => {
          const sel = value === o.id
          const isRight = correct === o.id || (scores && scores[o.id]?.score === Math.max(...Object.values(scores).map((s) => s.score)))
          return (
            <button key={o.id} type="button" role="radio" aria-checked={sel} disabled={disabled} onClick={() => onChange(o.id)}
              className={cn('w-full cursor-pointer rounded-ctl border-2 border-line px-4 py-3 text-left text-sm transition-[transform,box-shadow] duration-100 disabled:cursor-default',
                !disabled && !sel && 'bg-surface-1 hover:-translate-y-px hover:shadow-[3px_3px_0_0_var(--shadow)]',
                !disabled && sel && 'bg-brand text-ink shadow-[3px_3px_0_0_var(--shadow)]',
                disabled && isRight && 'bg-[#86efac] text-ink',
                disabled && sel && !isRight && 'bg-[#ff8a70] text-ink',
                disabled && !sel && !isRight && 'bg-surface-1 opacity-60')}>
              <span className="flex items-start justify-between gap-3">
                <span>
                  {o.label && <span className="block font-bold">{o.label}</span>}
                  <span className={o.label ? 'text-sm opacity-90' : 'font-medium'}>{o.text ?? o.description}</span>
                </span>
                {disabled && scores?.[o.id] && <span className="num shrink-0 font-mono text-sm font-bold">{scores[o.id].score}</span>}
                {disabled && sel && <span className="sr-only">(your choice)</span>}
                {disabled && isRight && <span className="sr-only">(best answer)</span>}
              </span>
              {disabled && scores?.[o.id] && <span className="mt-2 block text-xs opacity-90">{scores[o.id].rationale}</span>}
            </button>
          )
        })}
      </div>
    </fieldset>
  )
}

// ------------------------------------------------------------------ review

function Review({ runId, p, onAnswered }: PuzzleProps) {
  const done = p.answer
  const [flags, setFlags] = useState<number[]>([])
  const [conf, setConf] = useState(70)
  const submit = useAnswer(runId, p, onAnswered)
  const lines = (p.payload.code as string).split('\n')
  const flagged = done ? (done.response.lines as number[]) : flags
  const bugs = (done?.solution.bugs ?? []) as { lines: number[]; title: string; explanation: string }[]
  const bugLine = new Map<number, number>()
  bugs.forEach((b, i) => b.lines.forEach((l) => bugLine.set(l, i)))
  const toggle = (n: number) => setFlags((f) => (f.includes(n) ? f.filter((x) => x !== n) : [...f, n]))

  return (
    <div>
      <p className="leading-relaxed">{p.payload.intro}</p>
      <div className="mt-4 overflow-x-auto rounded-ctl border-2 border-line bg-surface-1" role="group" aria-label="Code. Toggle a line to flag it.">
        {lines.map((text, idx) => {
          const n = idx + 1
          const isFlag = flagged.includes(n)
          const bug = bugLine.get(n)
          return (
            <button key={n} type="button" disabled={!!done} onClick={() => toggle(n)} aria-pressed={isFlag}
              aria-label={`Line ${n}${isFlag ? ', flagged' : ''}${done && bug !== undefined ? `, part of bug ${bug + 1}` : ''}: ${text.trim() || 'blank'}`}
              className={cn('flex w-full cursor-pointer items-stretch text-left font-mono text-[13px] leading-6 disabled:cursor-default',
                !done && 'hover:bg-surface-2',
                isFlag && !done && 'bg-brand text-ink',
                done && bug !== undefined && 'bg-[#86efac]/70 text-ink',
                done && isFlag && bug === undefined && 'bg-[#ff8a70]/70 text-ink')}>
              <span className="w-10 shrink-0 select-none border-r-2 border-line pr-2 text-right opacity-70">{n}</span>
              <span className="w-6 shrink-0 text-center">{isFlag ? <Flag className="mt-1 inline size-3.5" aria-hidden /> : ''}</span>
              <span className="whitespace-pre pr-4">{text || ' '}</span>
            </button>
          )
        })}
      </div>
      {!done ? (
        <>
          <p className="mt-3 text-xs text-muted">Scoring: F1. Each real bug you touch counts as found; every flag outside a bug is a false alarm. Flagging everything doesn&apos;t pay.</p>
          <div className="mt-4"><Confidence value={conf} onChange={setConf} prompt="How sure are you that you found the important bugs without false alarms?" /></div>
          <Button variant="primary" className="mt-5" disabled={flags.length === 0} loading={submit.isPending}
            onClick={() => submit.mutate({ response: { lines: flags }, confidence: conf })}>Submit review ({flags.length} flagged)</Button>
        </>
      ) : (
        <ol className="mt-5 space-y-3">
          {bugs.map((b, i) => (
            <li key={b.title} className="rounded-ctl border-2 border-line bg-surface-1 p-4">
              <p className="flex flex-wrap items-center gap-2 font-bold">
                <Badge className={done.detail.bugsFound?.[i] ? 'bg-[#86efac] text-ink' : 'bg-[#ff8a70] text-ink'}>{done.detail.bugsFound?.[i] ? 'found' : 'missed'}</Badge>
                {b.title} <span className="font-mono text-xs font-normal text-muted">line {b.lines.join(', ')}</span>
              </p>
              <p className="mt-1 text-sm leading-relaxed">{b.explanation}</p>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ trade-off

function Tradeoff({ runId, p, onAnswered }: PuzzleProps) {
  const done = p.answer
  const [option, setOption] = useState<string | null>(null)
  const [risk, setRisk] = useState<string | null>(null)
  const [conf, setConf] = useState(70)
  const submit = useAnswer(runId, p, onAnswered)
  const pl = p.payload
  return (
    <div>
      <p className="text-lg leading-relaxed">{pl.context}</p>
      <ul className="mt-3 flex flex-wrap gap-2">{(pl.constraints as string[]).map((c) => <li key={c}><Badge>{c}</Badge></li>)}</ul>
      <ChoiceGroup legend="Your design" options={pl.options} value={done ? (done.response.option as string) : option}
        onChange={setOption} disabled={!!done} scores={done?.solution.options} />
      <ChoiceGroup legend="The main risk you're accepting" options={pl.risks} value={done ? (done.response.risk as string) : risk}
        onChange={setRisk} disabled={!!done} correct={done?.solution.bestRisk} />
      {done && <p className="mt-4 rounded-ctl border-2 border-line bg-surface-2 p-4 text-sm leading-relaxed"><b>On the risk: </b>{done.solution.riskExplanation}</p>}
      {!done && (
        <>
          <p className="mt-3 text-xs text-muted">Scoring: 75% from how well your design fits (defensible options earn partial credit) + 25 for naming the main risk.</p>
          <div className="mt-4"><Confidence value={conf} onChange={setConf} prompt="How sure are you that you picked the best design?" /></div>
          <Button variant="primary" className="mt-5" disabled={!option || !risk} loading={submit.isPending}
            onClick={() => submit.mutate({ response: { option, risk }, confidence: conf })}>Commit decision</Button>
        </>
      )}
    </div>
  )
}
