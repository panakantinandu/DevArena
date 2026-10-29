import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, ShieldAlert } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button, DifficultyTag, EmptyState, Field, Input, PageHeader, Select, Skeleton, Textarea } from '@/components/ui'
import { api, ApiError, errorMessage } from '@/lib/api'
import { useMe } from '@/lib/auth'
import type { Page, QuestionSummary, TopicSummary } from '@/lib/types'

interface AdminOption { body: string; correct: boolean }
interface AdminQuestion {
  id?: string; slug: string; topicSlug: string; difficulty: string; prompt: string; codeSnippet: string | null
  explanation: string; status: 'ACTIVE' | 'DRAFT' | 'ARCHIVED'; options: AdminOption[]
}

const blank = (): AdminQuestion => ({
  slug: '', topicSlug: 'arrays', difficulty: 'MEDIUM', prompt: '', codeSnippet: '', explanation: '', status: 'DRAFT',
  options: [{ body: '', correct: true }, { body: '', correct: false }, { body: '', correct: false }, { body: '', correct: false }],
})

export default function AdminQuestions() {
  const { data: me } = useMe()
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<AdminQuestion | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const topics = useQuery({ queryKey: ['topics'], queryFn: () => api.get<TopicSummary[]>('/api/topics') })
  const list = useQuery({
    queryKey: ['admin-questions', search],
    queryFn: () => api.get<Page<QuestionSummary>>(`/api/questions?size=50&search=${encodeURIComponent(search)}`),
    enabled: me?.role === 'ADMIN',
  })
  const load = useMutation({
    mutationFn: (id: string) => api.get<AdminQuestion>(`/api/admin/questions/${id}`),
    onSuccess: (q) => { setEditing(q); setErrors({}) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const save = useMutation({
    mutationFn: (q: AdminQuestion) => q.id ? api.put<AdminQuestion>(`/api/admin/questions/${q.id}`, q) : api.post<AdminQuestion>('/api/admin/questions', q),
    onSuccess: (q) => {
      toast.success('Question saved')
      setEditing(q)
      void qc.invalidateQueries({ queryKey: ['admin-questions'] })
      void qc.invalidateQueries({ queryKey: ['questions'] })
    },
    onError: (e) => {
      if (e instanceof ApiError) setErrors(e.fieldErrors)
      toast.error(errorMessage(e))
    },
  })

  if (me?.role !== 'ADMIN') {
    return <EmptyState icon={ShieldAlert} title="Admins only" body="Question authoring is limited to admin accounts. See the README for how to promote a user." />
  }

  return (
    <>
      <PageHeader eyebrow="// admin" title="Question bank" description="Seeded questions are updated by the seed files on deploy; prefer new slugs for your own questions."
        actions={<Button variant="primary" onClick={() => { setEditing(blank()); setErrors({}) }}><Plus className="size-4" aria-hidden /> New question</Button>} />
      <div className="grid gap-6 xl:grid-cols-[1fr_1.2fr]">
        <section>
          <Input aria-label="Search" placeholder="Search prompts…" value={search} onChange={(e) => setSearch(e.target.value)} className="mb-3" />
          {list.isLoading && <Skeleton className="h-96" />}
          <ul className="divide-y divide-line rounded-panel border-2 border-line">
            {list.data?.items.map((q) => (
              <li key={q.id}>
                <button type="button" onClick={() => load.mutate(q.id)} className="flex w-full cursor-pointer items-center gap-3 px-4 py-3 text-left text-sm hover:bg-surface-1">
                  <span className="line-clamp-2 flex-1">{q.prompt}</span>
                  <DifficultyTag value={q.difficulty} />
                </button>
              </li>
            ))}
          </ul>
        </section>
        {editing && (
          <section className="panel space-y-4 p-6" aria-label="Question editor">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="q-slug" label="Slug" hint="lowercase-with-dashes" error={errors.slug}>
                <Input value={editing.slug} onChange={(e) => setEditing({ ...editing, slug: e.target.value })} />
              </Field>
              <Field id="q-topic" label="Topic" error={errors.topicSlug}>
                <Select value={editing.topicSlug} onChange={(e) => setEditing({ ...editing, topicSlug: e.target.value })}>
                  {topics.data?.map((t) => <option key={t.slug} value={t.slug}>{t.categoryLabel} · {t.name}</option>)}
                </Select>
              </Field>
              <Field id="q-diff" label="Difficulty">
                <Select value={editing.difficulty} onChange={(e) => setEditing({ ...editing, difficulty: e.target.value })}>
                  <option>EASY</option><option>MEDIUM</option><option>HARD</option>
                </Select>
              </Field>
              <Field id="q-status" label="Status" hint="Only ACTIVE questions are shown to users.">
                <Select value={editing.status} onChange={(e) => setEditing({ ...editing, status: e.target.value as AdminQuestion['status'] })}>
                  <option>DRAFT</option><option>ACTIVE</option><option>ARCHIVED</option>
                </Select>
              </Field>
            </div>
            <Field id="q-prompt" label="Prompt" error={errors.prompt}>
              <Textarea rows={3} value={editing.prompt} onChange={(e) => setEditing({ ...editing, prompt: e.target.value })} />
            </Field>
            <Field id="q-code" label="Code snippet (optional)">
              <Textarea rows={3} className="font-mono text-xs" value={editing.codeSnippet ?? ''} onChange={(e) => setEditing({ ...editing, codeSnippet: e.target.value })} />
            </Field>
            <fieldset>
              <legend className="mb-2 text-sm font-medium">Options <span className="font-normal text-muted">(select the correct one)</span></legend>
              <div className="space-y-2">
                {editing.options.map((o, i) => (
                  <div key={i} className="flex items-center gap-2">
                    <input type="radio" name="correct" aria-label={`Option ${i + 1} is correct`} checked={o.correct}
                      className="size-4 cursor-pointer accent-[var(--brand)]"
                      onChange={() => setEditing({ ...editing, options: editing.options.map((x, j) => ({ ...x, correct: j === i })) })} />
                    <Input aria-label={`Option ${i + 1}`} value={o.body}
                      onChange={(e) => setEditing({ ...editing, options: editing.options.map((x, j) => (j === i ? { ...x, body: e.target.value } : x)) })} />
                  </div>
                ))}
              </div>
            </fieldset>
            <Field id="q-expl" label="Explanation" hint="Explain why the answer is right. Stick to verifiable facts." error={errors.explanation}>
              <Textarea rows={4} value={editing.explanation} onChange={(e) => setEditing({ ...editing, explanation: e.target.value })} />
            </Field>
            <div className="flex gap-2">
              <Button variant="primary" loading={save.isPending} onClick={() => save.mutate({ ...editing, codeSnippet: editing.codeSnippet || null })}>Save</Button>
              <Button variant="ghost" onClick={() => setEditing(null)}>Close</Button>
            </div>
          </section>
        )}
      </div>
    </>
  )
}
