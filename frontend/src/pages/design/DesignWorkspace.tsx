import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Save, Send } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { DesignCanvas } from '@/components/DesignCanvas'
import { Button, DifficultyTag, ErrorState, Field, Skeleton, Textarea } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import type { DesignDocument, DesignProblemDetail, Diagram } from '@/lib/types'

export default function DesignWorkspace() {
  const { slug = '' } = useParams()
  const qc = useQueryClient()
  const problem = useQuery({ queryKey: ['design', slug], queryFn: () => api.get<DesignProblemDetail>(`/api/design/problems/${slug}`) })
  const pid = problem.data?.id
  const doc = useQuery({
    queryKey: ['design-doc', pid, null],
    queryFn: () => api.get<DesignDocument | undefined>(`/api/design/documents?problemId=${pid}`),
    enabled: !!pid,
  })
  const [diagram, setDiagram] = useState<Diagram | null>(null)
  const [notes, setNotes] = useState('')
  const [dirty, setDirty] = useState(false)

  useEffect(() => {
    if (doc.isSuccess && diagram == null) {
      setDiagram(doc.data?.diagram ?? { nodes: [], edges: [] })
      setNotes(doc.data?.notes ?? '')
    }
  }, [doc.isSuccess, doc.data, diagram])

  const save = useMutation({
    mutationFn: () => api.put<DesignDocument>('/api/design/documents', { problemId: pid, diagram, notes }),
    onSuccess: () => { setDirty(false); toast.success('Design saved') },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const submit = useMutation({
    mutationFn: async () => {
      await api.put('/api/design/documents', { problemId: pid, diagram, notes })
      return api.post<{ xpAwarded: number }>(`/api/design/documents/submit?problemId=${pid}`)
    },
    onSuccess: (r) => {
      setDirty(false)
      toast.success(r.xpAwarded ? `Submitted · +${r.xpAwarded} XP` : 'Submitted')
      void qc.invalidateQueries({ queryKey: ['design'] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  if (problem.isLoading) return <Skeleton className="h-[75vh]" />
  if (problem.error) return <ErrorState error={problem.error} onRetry={() => void problem.refetch()} />
  const p = problem.data!

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <Link to="/design" className="inline-flex min-h-11 items-center gap-1.5 text-sm text-muted hover:text-text">
          <ArrowLeft className="size-4" aria-hidden /> Problems
        </Link>
        <h1 className="text-xl font-semibold">{p.title}</h1>
        <DifficultyTag value={p.difficulty} />
        <div className="ml-auto flex gap-2">
          <Button size="sm" loading={save.isPending} disabled={!dirty} onClick={() => save.mutate()}><Save className="size-3.5" aria-hidden /> Save</Button>
          <Button size="sm" variant="primary" loading={submit.isPending} onClick={() => submit.mutate()}><Send className="size-3.5" aria-hidden /> Submit</Button>
        </div>
      </div>
      <div className="grid gap-4 xl:grid-cols-[320px_1fr]">
        <aside className="panel space-y-4 p-5 text-sm">
          <p>{p.summary}</p>
          <Req title="Functional requirements" items={p.functionalReqs} />
          <Req title="Non-functional requirements" items={p.nonFunctionalReqs} />
          {p.discussionTopics ? (
            <>
              <Req title="What a strong design covers" items={p.discussionTopics} />
              <Req title="Follow-up questions" items={p.followUps ?? []} />
            </>
          ) : <p className="text-xs text-muted">Submit your design to compare it with the discussion guide.</p>}
        </aside>
        <div className="space-y-4">
          <section className="panel h-[520px] overflow-hidden">
            {diagram ? <DesignCanvas value={diagram} onChange={(d) => { setDiagram(d); setDirty(true) }} /> : <Skeleton className="h-full" />}
          </section>
          <Field id="design-notes" label="Notes & trade-offs" hint="Explain your choices: data model, scaling, consistency, failure handling.">
            <Textarea rows={6} value={notes} onChange={(e) => { setNotes(e.target.value); setDirty(true) }} />
          </Field>
        </div>
      </div>
    </div>
  )
}

function Req({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <p className="eyebrow mb-1.5">{title}</p>
      <ul className="space-y-1">
        {items.map((x) => <li key={x} className="flex gap-2"><span aria-hidden className="text-live-text">›</span>{x}</li>)}
      </ul>
    </div>
  )
}
