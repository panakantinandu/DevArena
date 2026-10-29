import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, History, RotateCcw, Save } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { CodeEditor } from '@/components/CodeEditor'
import { Button, Dialog, DifficultyTag, ErrorState, Skeleton } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import type { CodeSubmission, CodingProblemDetail } from '@/lib/types'
import { relativeTime } from '@/lib/utils'

export default function CodingWorkspace() {
  const { slug = '' } = useParams()
  const qc = useQueryClient()
  const problem = useQuery({ queryKey: ['coding', slug], queryFn: () => api.get<CodingProblemDetail>(`/api/coding/problems/${slug}`) })
  const subs = useQuery({ queryKey: ['coding', slug, 'subs'], queryFn: () => api.get<CodeSubmission[]>(`/api/coding/problems/${slug}/submissions`) })
  const draftKey = `code-draft-${slug}`
  const [code, setCode] = useState<string | null>(null)
  const [editorKey, setEditorKey] = useState(0)
  const [confirmReset, setConfirmReset] = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const restoredToast = useRef(false)

  useEffect(() => {
    if (!problem.data || code != null) return
    const draft = localStorage.getItem(draftKey)
    if (draft && draft !== problem.data.starterCode && !restoredToast.current) {
      restoredToast.current = true
      toast('Restored your unsaved draft')
    }
    setCode(draft ?? problem.data.starterCode)
  }, [problem.data, code, draftKey])

  const submit = useMutation({
    mutationFn: () => api.post<{ xpAwarded: number; solutionOutline: string }>(`/api/coding/problems/${slug}/submissions`, { code, language: 'JAVA' }),
    onSuccess: (r) => {
      toast.success(r.xpAwarded ? `Saved · +${r.xpAwarded} XP` : 'Submission saved')
      void qc.invalidateQueries({ queryKey: ['coding', slug] })
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  if (problem.isLoading) return <Skeleton className="h-[75vh]" />
  if (problem.error) return <ErrorState error={problem.error} onRetry={() => void problem.refetch()} />
  const p = problem.data!

  return (
    <div className="flex h-[calc(100dvh-7.5rem)] min-h-[560px] flex-col">
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <Link to="/coding" className="inline-flex min-h-11 items-center gap-1.5 text-sm text-muted hover:text-text">
          <ArrowLeft className="size-4" aria-hidden /> Problems
        </Link>
        <h1 className="text-xl font-semibold">{p.title}</h1>
        <DifficultyTag value={p.difficulty} />
      </div>
      <div className="grid min-h-0 flex-1 gap-3 lg:grid-cols-[minmax(300px,0.8fr)_1.5fr]">
        <section className="panel overflow-y-auto p-5 text-sm leading-relaxed">
          <p className="eyebrow">{p.topic}</p>
          <p className="mt-2">{p.statement}</p>
          <p className="eyebrow mt-5 mb-1">Examples</p>
          <pre className="overflow-x-auto rounded-ctl border-2 border-line bg-bg p-3 font-mono text-xs leading-5">{p.examples}</pre>
          <p className="eyebrow mt-5 mb-1">Constraints</p>
          <pre className="whitespace-pre-wrap font-mono text-xs text-muted">{p.constraints}</pre>
          {p.solutionOutline ? (
            <details className="mt-6 rounded-ctl border-2 border-line p-3">
              <summary className="cursor-pointer font-medium">Reference approach</summary>
              <p className="mt-2 text-muted">{p.solutionOutline}</p>
            </details>
          ) : (
            <p className="mt-6 text-xs text-muted">Submit once to unlock the reference approach.</p>
          )}
        </section>
        <section className="panel flex min-h-0 flex-col overflow-hidden">
          <div className="min-h-0 flex-1">
            {code != null && (
              <CodeEditor key={editorKey} value={code}
                onChange={(v) => { setCode(v); localStorage.setItem(draftKey, v) }} />
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2 border-t-2 border-line px-3 py-2.5">
            <Button size="sm" variant="ghost" onClick={() => setConfirmReset(true)}><RotateCcw className="size-3.5" aria-hidden /> Reset</Button>
            <Button size="sm" variant="ghost" onClick={() => setShowHistory(true)}>
              <History className="size-3.5" aria-hidden /> History ({subs.data?.length ?? 0})
            </Button>
            <span className="ml-auto hidden text-xs text-muted md:inline">Not executed. Tests aren&apos;t run on the server.</span>
            <Button size="sm" variant="primary" loading={submit.isPending} onClick={() => submit.mutate()}>
              <Save className="size-3.5" aria-hidden /> Submit
            </Button>
          </div>
        </section>
      </div>

      <Dialog open={confirmReset} onOpenChange={setConfirmReset} title="Reset to starter code?" description="Your current draft will be discarded. Saved submissions are kept.">
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setConfirmReset(false)}>Keep my code</Button>
          <Button variant="danger" onClick={() => {
            localStorage.removeItem(draftKey)
            setCode(p.starterCode)
            setEditorKey((k) => k + 1)
            setConfirmReset(false)
          }}>Reset</Button>
        </div>
      </Dialog>
      <Dialog open={showHistory} onOpenChange={setShowHistory} title="Submission history" wide>
        {subs.data?.length === 0 && <p className="text-sm text-muted">No submissions yet.</p>}
        <ul className="space-y-3">
          {subs.data?.map((s) => (
            <li key={s.id} className="rounded-ctl border-2 border-line">
              <div className="flex items-center justify-between border-b-2 border-line px-3 py-2 text-xs text-muted">
                <span>{relativeTime(s.createdAt)} · {s.language.toLowerCase()}</span>
                <Button size="sm" variant="ghost" onClick={() => {
                  setCode(s.code); localStorage.setItem(draftKey, s.code); setEditorKey((k) => k + 1); setShowHistory(false)
                }}>Load into editor</Button>
              </div>
              <pre className="max-h-48 overflow-auto p-3 font-mono text-xs">{s.code}</pre>
            </li>
          ))}
        </ul>
      </Dialog>
    </div>
  )
}
