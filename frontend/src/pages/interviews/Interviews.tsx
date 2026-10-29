import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { MessagesSquare } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { Avatar, Badge, Button, DifficultyTag, EmptyState, ErrorState, Field, Input, PageHeader, Select, Skeleton } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import type { InterviewHistoryItem, SessionView } from '@/lib/types'
import { relativeTime } from '@/lib/utils'

const schema = z.object({
  type: z.string().min(1),
  difficulty: z.enum(['EASY', 'MEDIUM', 'HARD']),
  durationMinutes: z.coerce.number().int().min(10).max(120),
  questionCount: z.coerce.number().int().min(1).max(6),
})
type Values = z.infer<typeof schema>

export default function Interviews() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { code: joinCode } = useParams()
  const types = useQuery({ queryKey: ['interview-types'], queryFn: () => api.get<{ value: string; label: string }[]>('/api/interviews/types'), staleTime: Infinity })
  const history = useQuery({ queryKey: ['interviews'], queryFn: () => api.get<InterviewHistoryItem[]>('/api/interviews') })

  const create = useMutation({
    mutationFn: (v: Values) => api.post<SessionView>('/api/interviews', v),
    onSuccess: (s) => navigate(`/interviews/${s.id}`),
    onError: (e) => toast.error(errorMessage(e)),
  })
  const join = useMutation({
    mutationFn: (code: string) => api.post<SessionView>('/api/interviews/join', { code }),
    onSuccess: (s) => navigate(`/interviews/${s.id}`, { replace: true }),
    onError: (e) => toast.error(errorMessage(e)),
  })
  const joined = useRef(false)
  useEffect(() => {
    if (joinCode && !joined.current) { joined.current = true; join.mutate(joinCode) }
  }, [joinCode, join])

  const { register, handleSubmit, formState: { errors } } = useForm<z.input<typeof schema>, unknown, Values>({
    resolver: zodResolver(schema),
    defaultValues: { type: 'JAVA_BACKEND', difficulty: 'MEDIUM', durationMinutes: 45, questionCount: 3 },
  })
  const [code, setCode] = useState('')

  return (
    <>
      <PageHeader eyebrow="// interviews" title="Interview rooms"
        description="One of you interviews with the answer guide and rubric; the other answers. Swap roles next time." />
      <div className="grid gap-8 lg:grid-cols-[1.3fr_1fr]">
        <section className="panel p-6" aria-labelledby="new-h">
          <h2 id="new-h" className="text-xl">Run an interview <span className="text-sm font-normal text-muted">(you&apos;re the interviewer)</span></h2>
          <form noValidate className="mt-5 grid gap-4 sm:grid-cols-2" onSubmit={handleSubmit((v) => create.mutate(v))}>
            <div className="sm:col-span-2">
              <Field id="type" label="Interview type" error={errors.type?.message}>
                <Select autoFocus={params.get('create') === '1'} {...register('type')}>
                  {types.data?.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                </Select>
              </Field>
            </div>
            <Field id="difficulty" label="Difficulty">
              <Select {...register('difficulty')}>
                <option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option>
              </Select>
            </Field>
            <Field id="durationMinutes" label="Duration (minutes)" error={errors.durationMinutes?.message}>
              <Input type="number" min={10} max={120} step={5} {...register('durationMinutes')} />
            </Field>
            <Field id="questionCount" label="Questions" error={errors.questionCount?.message}>
              <Input type="number" min={1} max={6} {...register('questionCount')} />
            </Field>
            <div className="flex items-end">
              <Button type="submit" variant="primary" className="w-full" loading={create.isPending}>Create private room</Button>
            </div>
          </form>
        </section>
        <section className="panel p-6" aria-labelledby="join-h">
          <h2 id="join-h" className="text-xl">Join as candidate</h2>
          <p className="mt-1 text-sm text-muted">Enter the 8-character code from your interviewer.</p>
          <form className="mt-5 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (/^[A-Za-z0-9]{8}$/.test(code.trim())) join.mutate(code.trim().toUpperCase()); else toast.error('Interview codes have 8 characters') }}>
            <Input aria-label="Interview code" value={code} onChange={(e) => setCode(e.target.value)} maxLength={8}
              className="font-mono text-lg uppercase tracking-[0.25em]" autoComplete="off" spellCheck={false} />
            <Button type="submit" variant="live" loading={join.isPending}>Join</Button>
          </form>
        </section>
      </div>

      <section className="mt-10" aria-labelledby="ih-h">
        <h2 id="ih-h" className="mb-3 text-xl">History</h2>
        {history.isLoading && <Skeleton className="h-40" />}
        {history.error && <ErrorState error={history.error} onRetry={() => void history.refetch()} />}
        {history.data?.length === 0 && <EmptyState icon={MessagesSquare} title="No interviews yet" body="Create a room and send the code to your partner." />}
        {history.data && history.data.length > 0 && (
          <ul className="divide-y divide-line rounded-panel border-2 border-line">
            {history.data.map((h) => (
              <li key={h.id}>
                <Link to={h.status === 'COMPLETED' ? `/interviews/${h.id}/report` : `/interviews/${h.id}`}
                  className="flex flex-wrap items-center gap-4 px-4 py-3 hover:bg-surface-1">
                  <Badge className={h.yourRole === 'INTERVIEWER' ? 'border-live-text/40 text-live-text' : 'border-brand-text/40 text-brand-text'}>
                    {h.yourRole === 'INTERVIEWER' ? 'interviewer' : 'candidate'}
                  </Badge>
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">{h.typeLabel}</p>
                    <p className="text-xs text-muted">{relativeTime(h.createdAt)}</p>
                  </div>
                  <DifficultyTag value={h.difficulty} />
                  {h.otherPerson && (
                    <span className="flex items-center gap-2 text-sm text-muted">
                      <Avatar name={h.otherPerson.displayName} url={h.otherPerson.avatarUrl} size={22} /> {h.otherPerson.displayName}
                    </span>
                  )}
                  <span className="num w-24 text-right font-mono text-sm">
                    {h.overall != null ? <>{Number(h.overall).toFixed(2)}<span className="text-2xs text-muted"> / 5</span></> : <span className="text-muted">{h.status.toLowerCase()}</span>}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
