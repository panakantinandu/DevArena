import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, Network } from 'lucide-react'
import { Link } from 'react-router'
import { Badge, DifficultyTag, EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui'
import { api } from '@/lib/api'
import type { DesignProblemSummary } from '@/lib/types'

export default function DesignList() {
  const q = useQuery({ queryKey: ['design'], queryFn: () => api.get<DesignProblemSummary[]>('/api/design/problems') })
  return (
    <>
      <PageHeader eyebrow="// system design" title="System design arena"
        description="Sketch an architecture and explain the trade-offs. After you submit, compare against what a strong design covers. Nothing is auto-graded." />
      {q.isLoading && <Skeleton className="h-64" />}
      {q.error && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
      {q.data?.length === 0 && <EmptyState icon={Network} title="No design problems yet" />}
      <ol className="space-y-3">
        {q.data?.map((p, i) => (
          <li key={p.slug}>
            <Link to={`/design/${p.slug}`} className="panel flex items-start gap-5 p-5 hover:border-line-strong hover:bg-surface-2">
              <span className="num font-mono text-2xl text-muted">{String(i + 1).padStart(2, '0')}</span>
              <div className="min-w-0 flex-1">
                <p className="text-lg font-extrabold">{p.title}</p>
                <p className="mt-1 text-sm text-muted">{p.summary}</p>
              </div>
              <div className="flex flex-col items-end gap-2">
                <DifficultyTag value={p.difficulty} />
                {p.submitted && <Badge className="border-success/40 text-success"><CheckCircle2 className="size-3" aria-hidden /> submitted</Badge>}
              </div>
            </Link>
          </li>
        ))}
      </ol>
    </>
  )
}
