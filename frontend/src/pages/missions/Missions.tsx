import { useQuery } from '@tanstack/react-query'
import { CalendarCheck2 } from 'lucide-react'
import { MissionRow } from '@/components/MissionRow'
import { EmptyState, ErrorState, Meter, PageHeader, Skeleton } from '@/components/ui'
import { api } from '@/lib/api'
import type { TodayResponse } from '@/lib/types'

const PHASES = [
  { name: 'Fundamentals', range: [1, 30], focus: 'Arrays, hashing, windows, trees, graphs · Java collections & OOP · Spring basics · SQL' },
  { name: 'Advanced engineering', range: [31, 60], focus: 'DP, graph algorithms · concurrency & JVM · transactions, indexing, caching · distributed systems' },
  { name: 'Interview readiness', range: [61, 90], focus: 'Timed mixed sets · system design · behavioral · peer mock interviews · weak-area revision' },
]

export default function Missions() {
  const q = useQuery({ queryKey: ['missions', 'today'], queryFn: () => api.get<TodayResponse>('/api/missions/today') })
  const t = q.data
  return (
    <>
      <PageHeader eyebrow={t ? `// ${t.date}` : '// today'} title="Daily missions"
        description="Planned each morning from your roadmap day, weak topics and recent mistakes. XP is awarded once per mission." />
      {q.isLoading && <Skeleton className="h-80" />}
      {q.error && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
      {t && (
        <div className="grid gap-10 lg:grid-cols-[1.5fr_1fr]">
          <section aria-label="Today's missions">
            {t.missions.length === 0
              ? <EmptyState icon={CalendarCheck2} title="Nothing planned today" body="The question bank appears to be empty." />
              : <ol>{t.missions.map((m, i) => <MissionRow key={m.id} m={m} last={i === t.missions.length - 1} />)}</ol>}
          </section>
          <aside className="space-y-6">
            <div className="panel p-5">
              <p className="eyebrow">Roadmap</p>
              <p className="num mt-1 font-mono text-3xl font-semibold">
                {String(t.roadmapDay).padStart(2, '0')}<span className="text-lg text-muted"> / 90</span>
              </p>
              <Meter className="mt-3" value={t.roadmapDay / 90} label="Roadmap progress" />
              <p className="mt-3 text-sm text-muted">
                Streak target: <span className="text-text">{t.dailyMinimum} missions</span> per day,
                {' '}<span className="num font-mono">{t.completedCount}</span> done today.
              </p>
            </div>
            <ol className="space-y-4">
              {PHASES.map((p) => {
                const active = t.roadmapDay >= p.range[0] && t.roadmapDay <= p.range[1]
                return (
                  <li key={p.name} className={active ? '' : 'opacity-60'}>
                    <p className="font-mono text-2xs text-muted">days {p.range[0]}–{p.range[1]}</p>
                    <p className={active ? 'text-lg font-extrabold text-live-text' : 'text-lg font-extrabold'}>{p.name}</p>
                    <p className="text-sm text-muted">{p.focus}</p>
                  </li>
                )
              })}
            </ol>
          </aside>
        </div>
      )}
    </>
  )
}
