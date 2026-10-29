import { useMutation, useQuery } from '@tanstack/react-query'
import { Brain, Bug, Calculator, Scale, Search, Target } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Avatar, Badge, Button, EmptyState, ErrorState, Meter, PageHeader, Skeleton } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import { cn, relativeTime } from '@/lib/utils'
import { MODE_INFO, type GymMode, type GymOverview, type RunView } from './model'

const MODE_ICON: Record<GymMode, typeof Brain> = { ESTIMATION: Calculator, INCIDENT: Search, REVIEW: Bug, TRADEOFF: Scale }

export default function Gym() {
  const navigate = useNavigate()
  const q = useQuery({ queryKey: ['gym'], queryFn: () => api.get<GymOverview>('/api/gym') })
  const daily = useMutation({
    mutationFn: () => api.post<RunView>('/api/gym/daily'),
    onSuccess: (r) => navigate(`/gym/runs/${r.id}`),
    onError: (e) => toast.error(errorMessage(e)),
  })
  const practice = useMutation({
    mutationFn: (mode: GymMode) => api.post<RunView>('/api/gym/practice', { mode }),
    onSuccess: (r) => navigate(`/gym/runs/${r.id}`),
    onError: (e) => toast.error(errorMessage(e)),
  })

  if (q.isLoading) return <Skeleton className="h-[70vh]" />
  if (q.error) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const o = q.data!
  const d = o.daily

  return (
    <>
      <PageHeader eyebrow="// brain gym" title="Brain gym"
        description="Train the judgment that matters when AI writes much of the code: estimation, debugging from evidence, reviewing generated code, and design trade-offs. Every answer also trains calibration, knowing how sure to be." />

      <div className="grid gap-8 xl:grid-cols-[1.35fr_1fr]">
        <div className="space-y-8">
          {/* Daily */}
          <section className="panel brackets grid-bg p-6" aria-labelledby="daily-h">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="eyebrow">// daily · {o.today}</p>
                <h2 id="daily-h" className="mt-1 text-2xl">Today&apos;s five</h2>
                <p className="mt-1 max-w-md text-sm text-muted">
                  Same five puzzles for everyone today: two estimates, an incident, a code review and a trade-off. About 10–15 minutes.
                </p>
              </div>
              {d?.status === 'COMPLETED' ? (
                <div className="text-right">
                  <p className="eyebrow">Your score</p>
                  <p className="display num text-5xl font-extrabold">{d.totalScore}</p>
                </div>
              ) : null}
            </div>
            <div className="mt-5 flex flex-wrap gap-3">
              {d?.status === 'COMPLETED' ? (
                <Link to={`/gym/runs/${d.id}`}><Button>Review today&apos;s answers</Button></Link>
              ) : (
                <Button variant="primary" loading={daily.isPending} onClick={() => daily.mutate()}>
                  <Brain className="size-4" aria-hidden /> {d ? 'Continue daily' : 'Start daily'}
                </Button>
              )}
            </div>
          </section>

          {/* Practice modes */}
          <section aria-labelledby="modes-h">
            <h2 id="modes-h" className="mb-3 text-xl">Practice a skill</h2>
            <ul className="grid gap-4 sm:grid-cols-2">
              {(Object.keys(MODE_INFO) as GymMode[]).map((m) => {
                const Icon = MODE_ICON[m]
                return (
                  <li key={m} className="panel flex flex-col p-5">
                    <div className="flex items-center gap-3">
                      <span className="grid size-10 place-items-center rounded-ctl border-2 border-line bg-brand text-ink"><Icon className="size-5" aria-hidden /></span>
                      <div>
                        <p className="font-extrabold">{MODE_INFO[m].label}</p>
                        <p className="font-mono text-2xs text-muted">{o.puzzlesPerMode[m]} puzzles · trains {MODE_INFO[m].skill.toLowerCase()}</p>
                      </div>
                    </div>
                    <p className="mt-3 flex-1 text-sm text-muted">{MODE_INFO[m].blurb}</p>
                    <Button className="mt-4 self-start" size="sm" loading={practice.isPending && practice.variables === m}
                      onClick={() => practice.mutate(m)}>Practice 4</Button>
                  </li>
                )
              })}
            </ul>
          </section>
        </div>

        <aside className="space-y-8">
          {/* Skill profile */}
          <section aria-labelledby="skills-h">
            <h2 id="skills-h" className="mb-3 text-xl">Senior signals</h2>
            {o.answeredTotal === 0 ? (
              <EmptyState icon={Target} title="No signal yet" body="Finish a daily or a practice run and your profile appears here." />
            ) : (
              <ul className="panel space-y-4 p-5">
                {o.skills.map((s) => (
                  <li key={s.key}>
                    <div className="mb-1 flex justify-between text-sm">
                      <span className="font-bold">{s.label}</span>
                      <span className="num font-mono text-xs text-muted">
                        {s.score == null ? 'no data' : `${s.score}/100`} · {s.samples} answers
                      </span>
                    </div>
                    <Meter value={(s.score ?? 0) / 100} tone={s.key === 'CALIBRATION' ? 'live' : 'brand'} label={`${s.label} score`} />
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* Calibration */}
          {o.answeredTotal > 0 && (
            <section aria-labelledby="cal-h">
              <h2 id="cal-h" className="text-xl">Calibration</h2>
              <p className="mt-1 text-sm text-muted">When you say you&apos;re X% sure, how often are you right? Bars should match their bucket.</p>
              <ul className="panel mt-3 space-y-3 p-5">
                {o.calibration.map((b) => {
                  const mid = (b.from + b.to + 1) / 2
                  const acc = b.accuracy == null ? null : Math.round(b.accuracy * 100)
                  const off = acc == null ? 0 : acc - mid
                  return (
                    <li key={b.label} className="grid grid-cols-[72px_1fr_88px] items-center gap-3 text-sm">
                      <span className="num font-mono text-xs">{b.label}</span>
                      <div className="relative h-4 rounded-[3px] border-2 border-line bg-surface-1" aria-hidden>
                        {acc != null && <div className="h-full bg-live" style={{ width: `${acc}%` }} />}
                        <span className="absolute inset-y-[-4px] w-0.5 bg-line" style={{ left: `${mid}%` }} title="Perfect calibration" />
                      </div>
                      <span className="num text-right font-mono text-xs">
                        {acc == null ? '—' : `${acc}% · n=${b.answers}`}
                        <span className="sr-only">{acc == null ? 'no answers' : off < -10 ? ', overconfident' : off > 10 ? ', underconfident' : ', well calibrated'}</span>
                      </span>
                    </li>
                  )
                })}
              </ul>
              <p className="mt-2 text-2xs text-muted">Black tick = perfect calibration. A bar well short of the tick means you were overconfident.</p>
            </section>
          )}

          {/* Daily board */}
          <section aria-labelledby="board-h">
            <h2 id="board-h" className="mb-3 text-xl">Today among friends</h2>
            <ol className="panel divide-y-2 divide-line">
              {o.dailyBoard.map((e, i) => (
                <li key={e.user.id} className={cn('flex items-center gap-3 px-4 py-3', e.you && 'bg-brand/25')}>
                  <span className="num w-5 font-mono text-sm text-muted">{i + 1}</span>
                  <Avatar name={e.user.displayName} url={e.user.avatarUrl} size={28} />
                  <span className="min-w-0 flex-1 truncate text-sm font-semibold">{e.user.displayName}{e.you && ' (you)'}</span>
                  {e.status === 'COMPLETED' ? <span className="num font-mono font-bold">{e.score}</span>
                    : <Badge>{e.status === 'IN_PROGRESS' ? 'playing' : 'not yet'}</Badge>}
                </li>
              ))}
            </ol>
            {o.dailyBoard.length === 1 && (
              <p className="mt-2 text-xs text-muted"><Link to="/friends" className="text-brand-text underline">Add your study partner</Link> to compare daily scores.</p>
            )}
          </section>

          {o.recent.length > 0 && (
            <section aria-labelledby="recent-h">
              <h2 id="recent-h" className="mb-3 text-xl">Recent runs</h2>
              <ul className="space-y-2">
                {o.recent.map((r) => (
                  <li key={r.id}>
                    <Link to={`/gym/runs/${r.id}`} className="flex items-center justify-between gap-3 rounded-ctl border-2 border-line bg-surface-1 px-3 py-2 text-sm hover:bg-surface-2">
                      <span className="font-semibold">{r.kind === 'DAILY' ? `Daily · ${r.runDate}` : MODE_INFO[r.mode!].label}</span>
                      <span className="font-mono text-xs text-muted">{r.status === 'COMPLETED' ? `${r.totalScore}/100` : 'in progress'} · {relativeTime(r.createdAt)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </aside>
      </div>
    </>
  )
}
