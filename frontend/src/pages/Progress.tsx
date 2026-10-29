import { useQuery } from '@tanstack/react-query'
import { BarChart3 } from 'lucide-react'
import { Bar, BarChart, CartesianGrid, LabelList, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { EmptyState, ErrorState, Meter, PageHeader, Skeleton } from '@/components/ui'
import { api } from '@/lib/api'
import type { Analytics, DayActivity } from '@/lib/types'
import { cn, pct, shortDate } from '@/lib/utils'

const AXIS = { stroke: 'var(--muted)', fontSize: 11, fontFamily: 'JetBrains Mono' }
const TOOLTIP = {
  contentStyle: { background: 'var(--surface-1)', border: '2px solid var(--line)', borderRadius: 6, fontSize: 12, color: 'var(--text)', boxShadow: '3px 3px 0 0 var(--shadow)' },
  labelStyle: { color: 'var(--muted)', fontWeight: 700 },
}

export default function Progress() {
  const q = useQuery({ queryKey: ['progress'], queryFn: () => api.get<Analytics>('/api/progress') })
  if (q.isLoading) return <Skeleton className="h-[80vh]" />
  if (q.error) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const a = q.data!
  const practised = a.topics.filter((t) => t.attempts > 0)
  const totalQuestions = a.last90Days.reduce((s, d) => s + d.questions, 0)
  const activeDays = a.last90Days.filter((d) => d.xp > 0 || d.questions > 0).length
  const missionRate = a.missions.assigned ? a.missions.completed / a.missions.assigned : null

  return (
    <>
      <PageHeader eyebrow="// analytics" title="Progress"
        description="Built from your own answers and sessions. Mastery is a study aid, not a validated predictor of interview outcomes." />

      <dl className="mb-8 grid grid-cols-2 gap-px overflow-hidden rounded-panel border-2 border-line bg-line md:grid-cols-5">
        <Kpi label="Current streak" value={`${a.streak.current}d`} sub={`longest ${a.streak.longest}d`} />
        <Kpi label="Active days (90)" value={String(activeDays)} sub={`${totalQuestions} questions answered`} />
        <Kpi label="Missions done" value={pct(missionRate)} sub={`${a.missions.completed} of ${a.missions.assigned}`} />
        <Kpi label="Battles won" value={`${a.battles.won}/${a.battles.played}`} sub={`avg score ${a.battles.avg_score}`} />
        <Kpi label="Study time (30d)" value={`${Math.round(a.estimatedStudyMinutes30d / 6) / 10}h`} sub="estimated from missions" />
      </dl>

      <section className="mb-10" aria-labelledby="heat-h">
        <h2 id="heat-h" className="mb-3 text-lg">Daily activity · last 90 days</h2>
        <Heatmap days={a.last90Days} />
      </section>

      {practised.length === 0 ? (
        <EmptyState icon={BarChart3} title="Not enough data yet"
          body="Answer a few questions or finish a mission; topic accuracy and trends appear here as soon as there's something to show." />
      ) : (
        <div className="grid gap-10 xl:grid-cols-2">
          <section aria-labelledby="acc-h">
            <h2 id="acc-h" className="mb-3 text-lg">Accuracy by topic</h2>
            <div className="panel p-4" style={{ height: Math.max(220, practised.length * 30 + 40) }}>
              <ResponsiveContainer>
                <BarChart layout="vertical" data={[...practised].sort((x, y) => (y.accuracy ?? 0) - (x.accuracy ?? 0)).map((t) => ({ name: t.name, accuracy: Math.round((t.accuracy ?? 0) * 100), attempts: t.attempts }))}
                  margin={{ left: 8, right: 40 }}>
                  <CartesianGrid horizontal={false} stroke="var(--line-soft)" />
                  <XAxis type="number" domain={[0, 100]} unit="%" {...AXIS} />
                  <YAxis type="category" dataKey="name" width={150} {...AXIS} tick={{ fill: 'var(--text)' }} />
                  <Tooltip {...TOOLTIP} formatter={(v, _n, p) => [`${v}% of ${p.payload.attempts}`, 'Accuracy']} cursor={{ fill: 'var(--line-soft)' }} />
                  <Bar dataKey="accuracy" fill="var(--brand)" stroke="var(--line)" strokeWidth={2} radius={[0, 3, 3, 0]} barSize={16}>
                    <LabelList dataKey="accuracy" position="right" formatter={(v) => `${v}%`} style={{ fill: 'var(--text)', fontSize: 11, fontFamily: 'JetBrains Mono' }} />
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>

          <div className="space-y-10">
            <section aria-labelledby="missed-h">
              <h2 id="missed-h" className="mb-3 text-lg">Most-missed topics · 30 days</h2>
              {a.mostMissed.length === 0 ? <p className="text-sm text-muted">No misses in the last 30 days.</p> : (
                <ol className="divide-y divide-line rounded-panel border-2 border-line">
                  {a.mostMissed.map((t, i) => (
                    <li key={t.slug} className="flex items-center gap-3 px-4 py-2.5 text-sm">
                      <span className="num w-5 font-mono text-muted">{i + 1}</span>
                      <span className="flex-1">{t.name}</span>
                      <span className="num font-mono text-danger">{t.attempts} wrong</span>
                    </li>
                  ))}
                </ol>
              )}
            </section>

            <section aria-labelledby="diff-h">
              <h2 id="diff-h" className="mb-3 text-lg">Accuracy by difficulty · weekly</h2>
              {a.difficultyByWeek.length < 2 ? <p className="text-sm text-muted">Needs at least two weeks of answers.</p> : (
                <div className="panel h-64 p-4">
                  <ResponsiveContainer>
                    <LineChart data={a.difficultyByWeek.map((w) => ({ week: shortDate(w.weekStart), easy: r(w.easy), medium: r(w.medium), hard: r(w.hard) }))}>
                      <CartesianGrid stroke="var(--line-soft)" />
                      <XAxis dataKey="week" {...AXIS} />
                      <YAxis domain={[0, 100]} unit="%" {...AXIS} />
                      <Tooltip {...TOOLTIP} />
                      <Line dataKey="easy" name="Easy" stroke="var(--success)" strokeWidth={2} dot={{ r: 3 }} connectNulls />
                      <Line dataKey="medium" name="Medium" stroke="var(--warning)" strokeWidth={2} strokeDasharray="6 4" dot={{ r: 3 }} connectNulls />
                      <Line dataKey="hard" name="Hard" stroke="var(--danger)" strokeWidth={2} strokeDasharray="2 4" dot={{ r: 3 }} connectNulls />
                    </LineChart>
                  </ResponsiveContainer>
                  <p className="mt-1 font-mono text-2xs text-muted">solid = easy · dashed = medium · dotted = hard</p>
                </div>
              )}
            </section>
          </div>

          <section aria-labelledby="mastery-h">
            <h2 id="mastery-h" className="mb-3 text-lg">Skill mastery</h2>
            <ul className="grid gap-4 sm:grid-cols-2">
              {a.skills.map((s) => (
                <li key={s.category}>
                  <div className="mb-1 flex justify-between text-sm">
                    <span>{s.label}</span>
                    <span className="num font-mono text-xs text-muted">{s.mastery == null ? '—' : pct(s.mastery)}</span>
                  </div>
                  <Meter value={s.mastery ?? 0} tone="live" label={`${s.label} mastery`} />
                </li>
              ))}
            </ul>
            <details className="mt-4 text-xs text-muted">
              <summary className="cursor-pointer">How mastery is calculated</summary>
              <p className="mt-2 leading-relaxed">
                For each topic, your last 50 attempts are weighted by difficulty (easy 1, medium 1.5, hard 2), recency (21-day half-life),
                context (battle answers 0.8) and independence (a retry within 24h counts half). Mastery = weighted accuracy × confidence,
                where confidence = 1 − e^(−total weight / 5) grows with the amount of evidence.
              </p>
            </details>
          </section>

          <section aria-labelledby="iv-h">
            <h2 id="iv-h" className="mb-3 text-lg">Interview scores (as candidate)</h2>
            {a.interviewScores.length === 0 ? <p className="text-sm text-muted">Complete a peer interview as the candidate to see your trend.</p> : (
              <div className="panel h-56 p-4">
                <ResponsiveContainer>
                  <LineChart data={a.interviewScores.map((s) => ({ date: shortDate(s.at), score: Number(s.overall) }))}>
                    <CartesianGrid stroke="var(--line-soft)" />
                    <XAxis dataKey="date" {...AXIS} />
                    <YAxis domain={[1, 5]} {...AXIS} />
                    <Tooltip {...TOOLTIP} />
                    <Line dataKey="score" name="Overall" stroke="var(--live)" strokeWidth={3} dot={{ r: 4 }} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
          </section>
        </div>
      )}
    </>
  )
}

const r = (v: number | null) => (v == null ? null : Math.round(v * 100))

function Kpi({ label, value, sub }: { label: string; value: string; sub: string }) {
  return (
    <div className="bg-bg px-4 py-4">
      <dt className="eyebrow">{label}</dt>
      <dd className="num mt-1 font-mono text-2xl font-semibold">{value}</dd>
      <dd className="text-xs text-muted">{sub}</dd>
    </div>
  )
}

/** Calendar heatmap: one column per week, intensity by XP, with value in each cell's label. */
function Heatmap({ days }: { days: DayActivity[] }) {
  const max = Math.max(1, ...days.map((d) => d.xp))
  const level = (xp: number) => (xp === 0 ? 0 : Math.min(4, Math.ceil((xp / max) * 4)))
  const shades = ['bg-surface-2', 'bg-brand/30', 'bg-brand/55', 'bg-brand/80', 'bg-brand']
  const firstDow = (new Date(days[0].date + 'T00:00').getDay() + 6) % 7 // Monday = 0
  const cells: (DayActivity | null)[] = [...Array(firstDow).fill(null), ...days]
  const weeks: (DayActivity | null)[][] = []
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7))
  return (
    <div className="panel overflow-x-auto p-4">
      <div className="flex gap-1">
        {weeks.map((w, wi) => (
          <div key={wi} className="flex flex-col gap-1">
            {w.map((d, di) => d ? (
              <span key={di} title={`${d.date}: ${d.xp} XP, ${d.missions} missions, ${d.questions} questions`}
                aria-label={`${d.date}: ${d.xp} XP, ${d.missions} missions`} role="img"
                className={cn('size-3.5 rounded-[2px]', shades[level(d.xp)])} />
            ) : <span key={di} className="size-3.5" />)}
          </div>
        ))}
      </div>
      <div className="mt-3 flex items-center gap-1.5 font-mono text-2xs text-muted">
        less {shades.map((s, i) => <span key={i} className={cn('size-3 rounded-[2px]', s)} aria-hidden />)} more · max {max} XP/day
      </div>
    </div>
  )
}
