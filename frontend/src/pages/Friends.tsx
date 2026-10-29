import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, Swords, UserMinus, UserPlus, Users } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Avatar, Button, EmptyState, ErrorState, Input, PageHeader, Skeleton } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import type { FriendsOverview, PublicUser } from '@/lib/types'
import { relativeTime } from '@/lib/utils'

export default function Friends() {
  const qc = useQueryClient()
  const overview = useQuery({ queryKey: ['friends'], queryFn: () => api.get<FriendsOverview>('/api/friends') })
  const [term, setTerm] = useState('')
  const [submitted, setSubmitted] = useState('')
  const search = useQuery({
    queryKey: ['user-search', submitted],
    queryFn: () => api.get<PublicUser[]>(`/api/users/search?q=${encodeURIComponent(submitted)}`),
    enabled: submitted.length >= 2,
  })
  const refresh = () => void qc.invalidateQueries({ queryKey: ['friends'] })
  const onErr = (e: unknown) => toast.error(errorMessage(e))
  const request = useMutation({ mutationFn: (userId: string) => api.post('/api/friends/requests', { userId }), onSuccess: () => { toast.success('Request sent'); refresh() }, onError: onErr })
  const accept = useMutation({ mutationFn: (id: string) => api.post(`/api/friends/requests/${id}/accept`), onSuccess: refresh, onError: onErr })
  const decline = useMutation({ mutationFn: (id: string) => api.delete(`/api/friends/requests/${id}`), onSuccess: refresh, onError: onErr })
  const remove = useMutation({ mutationFn: (userId: string) => api.delete(`/api/friends/${userId}`), onSuccess: refresh, onError: onErr })
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null)

  const known = new Set([
    ...(overview.data?.friends.map((f) => f.user.id) ?? []),
    ...(overview.data?.outgoing.map((f) => f.user.id) ?? []),
    ...(overview.data?.incoming.map((f) => f.user.id) ?? []),
  ])

  return (
    <>
      <PageHeader eyebrow="// people" title="Friends" description="Friends can invite you to interviews and see your public activity. Private notes never leave the interview." />
      <div className="grid gap-8 lg:grid-cols-[1.3fr_1fr]">
        <div className="space-y-8">
          {overview.isLoading && <Skeleton className="h-48" />}
          {overview.error && <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />}
          {overview.data && overview.data.incoming.length > 0 && (
            <section aria-labelledby="in-h">
              <h2 id="in-h" className="mb-3 text-lg">Requests for you</h2>
              <ul className="space-y-2">
                {overview.data.incoming.map((r) => (
                  <li key={r.friendshipId} className="panel flex items-center gap-3 px-4 py-3">
                    <Avatar name={r.user.displayName} url={r.user.avatarUrl} />
                    <div className="min-w-0 flex-1"><p className="font-medium">{r.user.displayName}</p><p className="text-xs text-muted">@{r.user.username} · {relativeTime(r.sentAt)}</p></div>
                    <Button size="sm" variant="live" onClick={() => accept.mutate(r.friendshipId)}>Accept</Button>
                    <Button size="sm" variant="ghost" onClick={() => decline.mutate(r.friendshipId)}>Decline</Button>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {overview.data && (
            <section aria-labelledby="fr-h">
              <h2 id="fr-h" className="mb-3 text-lg">Your friends</h2>
              {overview.data.friends.length === 0 ? (
                <EmptyState icon={Users} title="No friends yet" body="Search for your study partner by username and send a request." />
              ) : (
                <ul className="divide-y divide-line rounded-panel border-2 border-line">
                  {overview.data.friends.map((f) => (
                    <li key={f.user.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                      <Avatar name={f.user.displayName} url={f.user.avatarUrl} />
                      <Link to={`/users/${f.user.id}`} className="min-w-0 flex-1 hover:underline">
                        <p className="font-medium">{f.user.displayName}</p><p className="text-xs text-muted">@{f.user.username}</p>
                      </Link>
                      <Link to="/arena?create=1"><Button size="sm"><Swords className="size-3.5" aria-hidden /> Challenge</Button></Link>
                      <Link to="/interviews?create=1"><Button size="sm">Interview</Button></Link>
                      {confirmRemove === f.user.id ? (
                        <Button size="sm" variant="danger" onClick={() => { remove.mutate(f.user.id); setConfirmRemove(null) }}>Confirm remove</Button>
                      ) : (
                        <Button size="icon" variant="ghost" aria-label={`Remove ${f.user.displayName}`} onClick={() => setConfirmRemove(f.user.id)}>
                          <UserMinus className="size-4" />
                        </Button>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}
          {overview.data && overview.data.outgoing.length > 0 && (
            <section aria-labelledby="out-h">
              <h2 id="out-h" className="mb-3 text-lg">Sent requests</h2>
              <ul className="space-y-2">
                {overview.data.outgoing.map((r) => (
                  <li key={r.friendshipId} className="flex items-center gap-3 text-sm">
                    <Avatar name={r.user.displayName} url={r.user.avatarUrl} size={26} />
                    <span className="flex-1">{r.user.displayName} <span className="text-muted">· pending</span></span>
                    <Button size="sm" variant="ghost" onClick={() => decline.mutate(r.friendshipId)}>Withdraw</Button>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
        <section className="panel h-fit p-5" aria-labelledby="find-h">
          <h2 id="find-h" className="text-lg">Find people</h2>
          <form role="search" className="mt-3 flex gap-2" onSubmit={(e) => { e.preventDefault(); setSubmitted(term.trim()) }}>
            <Input aria-label="Search by username or name" placeholder="username or name" value={term} onChange={(e) => setTerm(e.target.value)} />
            <Button type="submit" size="icon" aria-label="Search"><Search className="size-4" /></Button>
          </form>
          {submitted.length > 0 && submitted.length < 2 && <p className="mt-2 text-xs text-muted">Type at least 2 characters.</p>}
          {search.isLoading && <Skeleton className="mt-4 h-20" />}
          {search.data && (search.data.length === 0 ? <p className="mt-4 text-sm text-muted">Nobody found.</p> : (
            <ul className="mt-4 space-y-2">
              {search.data.map((u) => (
                <li key={u.id} className="flex items-center gap-3">
                  <Avatar name={u.displayName} url={u.avatarUrl} size={28} />
                  <Link to={`/users/${u.id}`} className="min-w-0 flex-1 text-sm hover:underline">{u.displayName} <span className="text-muted">@{u.username}</span></Link>
                  {!known.has(u.id) && (
                    <Button size="sm" onClick={() => request.mutate(u.id)} loading={request.isPending && request.variables === u.id}>
                      <UserPlus className="size-3.5" aria-hidden /> Add
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          ))}
        </section>
      </div>
    </>
  )
}
