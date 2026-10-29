import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bookmark, BookmarkCheck, Check, ChevronLeft, ChevronRight, Search, SearchX, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Button, DifficultyTag, EmptyState, ErrorState, Input, PageHeader, Select, Skeleton } from '@/components/ui'
import { api } from '@/lib/api'
import { CATEGORY_LABEL, type Category, type Page, type QuestionSummary, type TopicSummary } from '@/lib/types'
import { cn } from '@/lib/utils'

export default function Practice() {
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState(params.get('search') ?? '')
  const filters = {
    category: params.get('category') ?? '',
    topic: params.get('topic') ?? '',
    difficulty: params.get('difficulty') ?? '',
    search: params.get('search') ?? '',
    bookmarked: params.get('bookmarked') === 'true',
    mistakes: params.get('mistakes') === 'true',
    page: Number(params.get('page') ?? 0),
  }
  const set = (k: string, v: string | null) => {
    const next = new URLSearchParams(params)
    if (v) next.set(k, v); else next.delete(k)
    if (k !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  const topics = useQuery({ queryKey: ['topics'], queryFn: () => api.get<TopicSummary[]>('/api/topics') })
  const qs = new URLSearchParams()
  Object.entries(filters).forEach(([k, v]) => { if (v !== '' && v !== false) qs.set(k, String(v)) })
  const list = useQuery({
    queryKey: ['questions', qs.toString()],
    queryFn: () => api.get<Page<QuestionSummary>>(`/api/questions?${qs.toString()}`),
    placeholderData: keepPreviousData,
  })

  const grouped = useMemo(() => {
    const m = new Map<Category, TopicSummary[]>()
    topics.data?.forEach((t) => m.set(t.category, [...(m.get(t.category) ?? []), t]))
    return [...m.entries()]
  }, [topics.data])

  return (
    <>
      <PageHeader eyebrow="// library" title="Practice library"
        description="Every question has a written explanation. Your first correct answer to each question earns 5 XP." />
      <div className="grid gap-8 lg:grid-cols-[240px_1fr]">
        <nav aria-label="Topics" className="lg:sticky lg:top-6 lg:max-h-[calc(100dvh-3rem)] lg:overflow-y-auto">
          <button type="button" onClick={() => { set('topic', null); set('category', null) }}
            className={cn('mb-3 min-h-9 w-full cursor-pointer rounded-ctl px-2 text-left text-sm',
              !filters.topic && !filters.category ? 'bg-surface-2 text-text' : 'text-muted hover:text-text')}>
            All topics
          </button>
          {topics.isLoading && <Skeleton className="h-96" />}
          {grouped.map(([cat, ts]) => (
            <div key={cat} className="mb-4">
              <button type="button" onClick={() => { const n = new URLSearchParams(params); n.set('category', cat); n.delete('topic'); n.delete('page'); setParams(n, { replace: true }) }}
                className={cn('eyebrow mb-1 cursor-pointer hover:text-text', filters.category === cat && !filters.topic && 'text-live-text')}>
                {CATEGORY_LABEL[cat]}
              </button>
              <ul>
                {ts.map((t) => (
                  <li key={t.slug}>
                    <button type="button" onClick={() => { const n = new URLSearchParams(params); n.set('topic', t.slug); n.delete('category'); n.delete('page'); setParams(n, { replace: true }) }}
                      aria-current={filters.topic === t.slug ? 'true' : undefined}
                      className={cn('flex min-h-9 w-full cursor-pointer items-center justify-between gap-2 rounded-ctl px-2 text-left text-sm',
                        filters.topic === t.slug ? 'bg-surface-2 text-text' : 'text-muted hover:text-text')}>
                      <span className="truncate">{t.name}</span>
                      <span className="num font-mono text-2xs">
                        {t.mastery != null ? `${Math.round(t.mastery * 100)}%` : t.questionCount}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>

        <section aria-label="Questions">
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <form className="relative min-w-56 flex-1" onSubmit={(e) => { e.preventDefault(); set('search', search.trim() || null) }} role="search">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" aria-hidden />
              <Input aria-label="Search questions" placeholder="Search questions…" value={search} onChange={(e) => setSearch(e.target.value)} className="pl-9" />
            </form>
            <Select aria-label="Difficulty" className="w-36" value={filters.difficulty} onChange={(e) => set('difficulty', e.target.value || null)}>
              <option value="">Any difficulty</option>
              <option value="EASY">Easy</option>
              <option value="MEDIUM">Medium</option>
              <option value="HARD">Hard</option>
            </Select>
            <Toggle on={filters.bookmarked} onClick={() => set('bookmarked', filters.bookmarked ? null : 'true')} label="Bookmarked" />
            <Toggle on={filters.mistakes} onClick={() => set('mistakes', filters.mistakes ? null : 'true')} label="My mistakes" />
          </div>

          {list.isLoading && <Skeleton className="h-96" />}
          {list.error && <ErrorState error={list.error} onRetry={() => void list.refetch()} />}
          {list.data && list.data.items.length === 0 && (
            <EmptyState icon={SearchX} title="No questions match"
              body={filters.mistakes ? 'No open mistakes. Questions you got wrong (and haven\'t fixed since) appear here.' :
                filters.bookmarked ? 'Bookmark questions to build your own revision list.' : 'Try a different filter or search.'} />
          )}
          {list.data && list.data.items.length > 0 && (
            <>
              <div className="overflow-hidden rounded-panel border-2 border-line">
                <table className="w-full text-sm">
                  <caption className="sr-only">Questions</caption>
                  <thead className="bg-surface-1 text-left">
                    <tr className="border-b-2 border-line">
                      <th scope="col" className="w-10 px-3 py-2.5"><span className="sr-only">Last result</span></th>
                      <th scope="col" className="px-3 py-2.5 font-mono text-2xs font-normal uppercase tracking-wider text-muted">Question</th>
                      <th scope="col" className="hidden px-3 py-2.5 font-mono text-2xs font-normal uppercase tracking-wider text-muted md:table-cell">Topic</th>
                      <th scope="col" className="px-3 py-2.5 font-mono text-2xs font-normal uppercase tracking-wider text-muted">Level</th>
                      <th scope="col" className="w-12 px-3 py-2.5"><span className="sr-only">Bookmark</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {list.data.items.map((q) => <Row key={q.id} q={q} />)}
                  </tbody>
                </table>
              </div>
              <div className="mt-4 flex items-center justify-between text-sm text-muted">
                <span className="num font-mono">{list.data.totalItems} questions</span>
                <div className="flex items-center gap-2">
                  <Button size="icon" variant="ghost" aria-label="Previous page" disabled={filters.page === 0}
                    onClick={() => set('page', String(filters.page - 1))}><ChevronLeft className="size-4" /></Button>
                  <span className="num font-mono">{filters.page + 1} / {Math.max(1, list.data.totalPages)}</span>
                  <Button size="icon" variant="ghost" aria-label="Next page" disabled={filters.page + 1 >= list.data.totalPages}
                    onClick={() => set('page', String(filters.page + 1))}><ChevronRight className="size-4" /></Button>
                </div>
              </div>
            </>
          )}
        </section>
      </div>
    </>
  )
}

function Toggle({ on, onClick, label }: { on: boolean; onClick: () => void; label: string }) {
  return (
    <button type="button" aria-pressed={on} onClick={onClick}
      className={cn('min-h-11 cursor-pointer rounded-ctl border px-3 text-sm',
        on ? 'border-live-text/60 bg-live/15 text-text' : 'border-line text-muted hover:text-text')}>
      {label}
    </button>
  )
}

function Row({ q }: { q: QuestionSummary }) {
  const qc = useQueryClient()
  const toggle = useMutation({
    mutationFn: () => api.post<{ bookmarked: boolean }>(`/api/questions/${q.id}/bookmark`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['questions'] }),
  })
  return (
    <tr className="border-b-2 border-line last:border-0 hover:bg-surface-1">
      <td className="px-3 py-3">
        {q.lastAttemptCorrect === true && <Check className="size-4 text-success" aria-label="Last answer correct" />}
        {q.lastAttemptCorrect === false && <X className="size-4 text-danger" aria-label="Last answer wrong" />}
      </td>
      <td className="px-3 py-3">
        <Link to={`/practice/${q.id}`} className="line-clamp-2 hover:text-brand-text">{q.prompt}</Link>
      </td>
      <td className="hidden px-3 py-3 text-muted md:table-cell">{q.topic.name}</td>
      <td className="px-3 py-3"><DifficultyTag value={q.difficulty} /></td>
      <td className="px-1 py-1">
        <Button size="icon" variant="ghost" aria-label={q.bookmarked ? 'Remove bookmark' : 'Bookmark'} aria-pressed={q.bookmarked}
          loading={toggle.isPending} onClick={() => toggle.mutate()}>
          {!toggle.isPending && (q.bookmarked ? <BookmarkCheck className="size-4 text-warning" /> : <Bookmark className="size-4" />)}
        </Button>
      </td>
    </tr>
  )
}
