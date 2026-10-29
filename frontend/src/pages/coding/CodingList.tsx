import { useQuery } from '@tanstack/react-query'
import { Code2, Info } from 'lucide-react'
import { Link } from 'react-router'
import { DifficultyTag, EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui'
import { api } from '@/lib/api'
import type { CodingProblemSummary } from '@/lib/types'

export default function CodingList() {
  const q = useQuery({ queryKey: ['coding'], queryFn: () => api.get<CodingProblemSummary[]>('/api/coding/problems') })
  const exec = useQuery({ queryKey: ['execution'], queryFn: () => api.get<{ available: boolean; reason: string }>('/api/coding/execution'), staleTime: Infinity })
  return (
    <>
      <PageHeader eyebrow="// workspace" title="Coding workspace" description="Write solutions in a real editor. Your first submission to a problem earns 15 XP and unlocks the reference outline." />
      {exec.data && !exec.data.available && (
        <p className="mb-6 flex items-start gap-2 rounded-ctl border-2 border-line bg-surface-1 px-4 py-3 text-sm text-muted">
          <Info className="mt-0.5 size-4 shrink-0 text-live-text" aria-hidden />
          <span><span className="text-text">Automated test execution: not available yet.</span> {exec.data.reason}</span>
        </p>
      )}
      {q.isLoading && <Skeleton className="h-64" />}
      {q.error && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
      {q.data?.length === 0 && <EmptyState icon={Code2} title="No problems yet" />}
      {q.data && q.data.length > 0 && (
        <ul className="grid gap-px overflow-hidden rounded-panel border-2 border-line bg-line sm:grid-cols-2">
          {q.data.map((p) => (
            <li key={p.slug} className="bg-bg">
              <Link to={`/coding/${p.slug}`} className="flex h-full items-center gap-4 px-5 py-4 hover:bg-surface-1">
                <Code2 className="size-5 text-brand-text" aria-hidden />
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{p.title}</p>
                  <p className="text-xs text-muted">{p.topic}{p.submissions > 0 && ` · ${p.submissions} submission${p.submissions === 1 ? '' : 's'}`}</p>
                </div>
                <DifficultyTag value={p.difficulty} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
