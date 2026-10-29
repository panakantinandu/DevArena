import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Swords, Trophy } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { z } from 'zod'
import { Avatar, Badge, Button, DifficultyTag, EmptyState, ErrorState, Field, Input, PageHeader, Select, Skeleton } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import { CATEGORY_LABEL, type BattleHistoryItem, type BattleView, type Category } from '@/lib/types'
import { cn, relativeTime } from '@/lib/utils'

const schema = z.object({
  category: z.enum(['DSA', 'JAVA', 'SPRING', 'DATABASES', 'DISTRIBUTED', 'SYSTEM_DESIGN', 'AI_CONCEPTS']),
  difficulty: z.enum(['EASY', 'MEDIUM', 'HARD']),
  questionCount: z.coerce.number().int().min(3).max(20),
  secondsPerQuestion: z.coerce.number().int().min(10).max(120),
})
type Values = z.infer<typeof schema>

export default function Arena() {
  const navigate = useNavigate()
  const { code: joinCode } = useParams()
  const [params] = useSearchParams()
  const history = useQuery({ queryKey: ['battles'], queryFn: () => api.get<BattleHistoryItem[]>('/api/battles') })

  const create = useMutation({
    mutationFn: (v: Values) => api.post<BattleView>('/api/battles', v),
    onSuccess: (b) => navigate(`/arena/${b.state.id}`),
    onError: (e) => toast.error(errorMessage(e)),
  })
  const join = useMutation({
    mutationFn: (code: string) => api.post<BattleView>('/api/battles/join', { code }),
    onSuccess: (b) => navigate(`/arena/${b.state.id}`, { replace: true }),
    onError: (e) => toast.error(errorMessage(e)),
  })

  // Deep link /arena/join/CODE joins straight away (once).
  const joined = useRef(false)
  useEffect(() => {
    if (joinCode && !joined.current) {
      joined.current = true
      join.mutate(joinCode)
    }
  }, [joinCode, join])

  const { register, handleSubmit, formState: { errors } } = useForm<z.input<typeof schema>, unknown, Values>({
    resolver: zodResolver(schema),
    defaultValues: { category: 'JAVA', difficulty: 'MEDIUM', questionCount: 7, secondsPerQuestion: 30 },
  })
  const [code, setCode] = useState('')

  return (
    <>
      <PageHeader eyebrow="// arena" title="Battle arena"
        description="Real-time quiz battles. Same question for everyone, a server-side clock, 100 points per correct answer plus up to 25 for speed." />
      <div className="grid gap-8 lg:grid-cols-[1fr_1fr]">
        <section className="brackets panel p-6" aria-labelledby="create-h">
          <h2 id="create-h" className="flex items-center gap-2 text-xl"><Swords className="size-5 text-brand-text" aria-hidden /> Create a battle</h2>
          <form noValidate className="mt-5 grid gap-4 sm:grid-cols-2" onSubmit={handleSubmit((v) => create.mutate(v))}>
            <div className="sm:col-span-2">
              <Field id="category" label="Category" error={errors.category?.message}>
                <Select autoFocus={params.get('create') === '1'} {...register('category')}>
                  {(['DSA', 'JAVA', 'SPRING', 'DATABASES', 'DISTRIBUTED', 'SYSTEM_DESIGN', 'AI_CONCEPTS'] as Category[]).map((c) => (
                    <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>
                  ))}
                </Select>
              </Field>
            </div>
            <Field id="difficulty" label="Difficulty" error={errors.difficulty?.message}>
              <Select {...register('difficulty')}>
                <option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option>
              </Select>
            </Field>
            <Field id="questionCount" label="Questions" error={errors.questionCount?.message}>
              <Input type="number" min={3} max={20} {...register('questionCount')} />
            </Field>
            <Field id="secondsPerQuestion" label="Seconds per question" error={errors.secondsPerQuestion?.message}>
              <Input type="number" min={10} max={120} step={5} {...register('secondsPerQuestion')} />
            </Field>
            <div className="flex items-end">
              <Button type="submit" variant="primary" className="w-full" loading={create.isPending}>Create room</Button>
            </div>
          </form>
        </section>

        <section className="panel p-6" aria-labelledby="join-h">
          <h2 id="join-h" className="text-xl">Join with a code</h2>
          <p className="mt-1 text-sm text-muted">Your friend sees a 6-character code in their lobby.</p>
          <form className="mt-5 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (/^[A-Za-z0-9]{6}$/.test(code.trim())) join.mutate(code.trim().toUpperCase()); else toast.error('Battle codes have 6 characters') }}>
            <Input aria-label="Room code" value={code} onChange={(e) => setCode(e.target.value)} maxLength={6}
              placeholder="K7QX2M" className="font-mono text-lg uppercase tracking-[0.35em]" autoComplete="off" spellCheck={false} />
            <Button type="submit" variant="live" loading={join.isPending}>Join</Button>
          </form>
        </section>
      </div>

      <section className="mt-10" aria-labelledby="hist-h">
        <h2 id="hist-h" className="mb-3 text-xl">Battle history</h2>
        {history.isLoading && <Skeleton className="h-40" />}
        {history.error && <ErrorState error={history.error} onRetry={() => void history.refetch()} />}
        {history.data?.length === 0 && <EmptyState icon={Trophy} title="No battles yet" body="Create a room and send the code to your study partner." />}
        {history.data && history.data.length > 0 && (
          <ul className="divide-y divide-line rounded-panel border-2 border-line">
            {history.data.map((b) => (
              <li key={b.id}>
                <Link to={b.status === 'COMPLETED' ? `/arena/${b.id}` : '#'} className="flex flex-wrap items-center gap-4 px-4 py-3 hover:bg-surface-1">
                  <span className={cn('num w-10 font-mono text-lg font-semibold', b.won ? 'text-warning' : 'text-muted')}>
                    {b.status === 'CANCELLED' ? '—' : `#${b.placement}`}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">{CATEGORY_LABEL[b.category]} <span className="text-muted">· {b.questionCount} questions</span></p>
                    <p className="text-xs text-muted">{b.endedAt ? relativeTime(b.endedAt) : ''} · room {b.roomCode}</p>
                  </div>
                  <DifficultyTag value={b.difficulty} />
                  {b.opponentOrWinner && (
                    <span className="flex items-center gap-2 text-sm text-muted">
                      vs <Avatar name={b.opponentOrWinner.displayName} url={b.opponentOrWinner.avatarUrl} size={22} />
                      {b.opponentOrWinner.displayName}
                    </span>
                  )}
                  {b.status === 'CANCELLED' ? <Badge>cancelled</Badge> : (
                    <span className="num w-20 text-right font-mono">{b.yourScore} <span className="text-2xs text-muted">pts</span></span>
                  )}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
