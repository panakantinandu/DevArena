import { useQuery } from '@tanstack/react-query'
import { Trophy } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { Avatar, EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui'
import { api } from '@/lib/api'
import type { Leaderboard as Board } from '@/lib/types'
import { cn } from '@/lib/utils'

const METRICS = [
  ['XP', 'XP', 'xp'], ['QUIZ_WINS', 'Quiz wins', 'wins'], ['INTERVIEWS', 'Interviews', 'done'], ['CODING', 'Coding problems', 'solved'],
] as const
const PERIODS = [['WEEK', 'This week'], ['MONTH', 'This month'], ['ALL', 'All time']] as const

export default function Leaderboard() {
  const [metric, setMetric] = useState<string>('XP')
  const [period, setPeriod] = useState<string>('WEEK')
  const [scope, setScope] = useState<'friends' | 'all'>('friends')
  const q = useQuery({
    queryKey: ['leaderboard', metric, period, scope],
    queryFn: () => api.get<Board>(`/api/gamification/leaderboard?metric=${metric}&period=${period}&scope=${scope}`),
  })
  const unit = METRICS.find((m) => m[0] === metric)?.[2]
  return (
    <>
      <PageHeader eyebrow="// rankings" title="Leaderboard"
        description="Only totals are shown. Interview notes and individual scores are never published." />
      <div className="mb-5 flex flex-wrap gap-4">
        <Segmented label="Metric" value={metric} onChange={setMetric} options={METRICS.map(([v, l]) => [v, l])} />
        <Segmented label="Period" value={period} onChange={setPeriod} options={PERIODS.map(([v, l]) => [v, l])} />
        <Segmented label="Scope" value={scope} onChange={(v) => setScope(v as 'friends' | 'all')} options={[['friends', 'Friends'], ['all', 'Everyone']]} />
      </div>
      {q.isLoading && <Skeleton className="h-72" />}
      {q.error && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
      {q.data && q.data.rows.length <= 1 && scope === 'friends' && (
        <div className="mb-4"><EmptyState icon={Trophy} title="Just you so far" body="Add friends to compete on this board." action={<Link to="/friends" className="text-sm text-brand-text hover:underline">Find friends</Link>} /></div>
      )}
      {q.data && q.data.rows.length > 0 && (
        <ol className="divide-y divide-line overflow-hidden rounded-panel border-2 border-line" aria-label="Rankings">
          {q.data.rows.map((r) => (
            <li key={r.user.id} className={cn('flex items-center gap-4 px-4 py-3', r.you && 'bg-brand/10')}>
              <span className={cn('num w-8 font-mono text-lg font-semibold', r.rank === 1 ? 'text-warning' : 'text-muted')}>{r.rank}</span>
              <Avatar name={r.user.displayName} url={r.user.avatarUrl} />
              <Link to={`/users/${r.user.id}`} className="min-w-0 flex-1 truncate hover:underline">
                {r.user.displayName} {r.you && <span className="text-muted">(you)</span>}
              </Link>
              <span className="num font-mono text-lg">{r.value.toLocaleString()} <span className="text-2xs text-muted">{unit}</span></span>
            </li>
          ))}
        </ol>
      )}
      <p className="mt-4 text-xs text-muted">Live coding battles aren&apos;t built yet, so &quot;Coding problems&quot; counts distinct practice problems you&apos;ve submitted.</p>
    </>
  )
}

function Segmented({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: readonly (readonly [string, string])[] }) {
  return (
    <div role="radiogroup" aria-label={label} className="flex rounded-ctl border-2 border-line p-0.5">
      {options.map(([v, l]) => (
        <button key={v} type="button" role="radio" aria-checked={value === v} onClick={() => onChange(v)}
          className={cn('min-h-10 cursor-pointer rounded-[3px] px-3 text-sm', value === v ? 'bg-surface-3 text-text' : 'text-muted hover:text-text')}>
          {l}
        </button>
      ))}
    </div>
  )
}
