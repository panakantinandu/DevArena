import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Flame, UserPlus } from 'lucide-react'
import { useParams } from 'react-router'
import { toast } from 'sonner'
import { Avatar, Badge, Button, ErrorState, Skeleton } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import type { PublicProfile } from '@/lib/types'

const ROLE: Record<string, string> = {
  BACKEND_ENGINEER: 'Backend engineer', FULL_STACK_ENGINEER: 'Full-stack engineer', FRONTEND_ENGINEER: 'Frontend engineer',
  PLATFORM_ENGINEER: 'Platform engineer', AI_ENGINEER: 'AI engineer',
}

export default function UserProfile() {
  const { id } = useParams()
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['profile', id], queryFn: () => api.get<PublicProfile>(`/api/users/${id}/profile`) })
  const add = useMutation({
    mutationFn: () => api.post('/api/friends/requests', { userId: id }),
    onSuccess: () => { toast.success('Request sent'); void qc.invalidateQueries({ queryKey: ['profile', id] }) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  if (q.isLoading) return <Skeleton className="h-64" />
  if (q.error) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const p = q.data!
  return (
    <div className="mx-auto max-w-2xl">
      <section className="panel p-8">
        <div className="flex flex-wrap items-center gap-5">
          <Avatar name={p.displayName} url={p.avatarUrl} size={72} />
          <div className="min-w-0 flex-1">
            <h1 className="text-3xl font-semibold">{p.displayName}</h1>
            <p className="font-mono text-sm text-muted">@{p.username} · {ROLE[p.targetRole] ?? p.targetRole}</p>
          </div>
          {p.friendship === 'NONE' && <Button variant="primary" loading={add.isPending} onClick={() => add.mutate()}><UserPlus className="size-4" aria-hidden /> Add friend</Button>}
          {p.friendship === 'FRIENDS' && <Badge className="border-success/40 text-success">friends</Badge>}
          {p.friendship === 'REQUEST_SENT' && <Badge>request sent</Badge>}
          {p.friendship === 'REQUEST_RECEIVED' && <Badge className="border-live-text/40 text-live-text">wants to be friends</Badge>}
        </div>
        {p.bio && <p className="mt-6 leading-relaxed text-muted">{p.bio}</p>}
        <dl className="mt-8 grid grid-cols-3 gap-px overflow-hidden rounded-ctl border-2 border-line bg-line">
          <div className="bg-bg p-4"><dt className="eyebrow">Level</dt><dd className="mt-1 text-lg font-extrabold">{p.levelTitle}</dd></div>
          <div className="bg-bg p-4"><dt className="eyebrow">Total XP</dt><dd className="num mt-1 font-mono text-xl">{p.totalXp.toLocaleString()}</dd></div>
          <div className="bg-bg p-4"><dt className="eyebrow">Streak</dt><dd className="num mt-1 flex items-center gap-1 font-mono text-xl"><Flame className="size-4 text-warning" aria-hidden />{p.currentStreak}d</dd></div>
        </dl>
      </section>
    </div>
  )
}
