import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowLeft, Repeat } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { CodeEditor } from '@/components/CodeEditor'
import { Avatar, Button, ErrorState, Meter, PageHeader, Skeleton } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import type { InterviewReport as Report, SessionView } from '@/lib/types'
import { shortDate } from '@/lib/utils'

const CRITERIA: [keyof Report['evaluation'], string, number][] = [
  ['technical', 'Technical correctness', 0.3],
  ['depth', 'Depth of understanding', 0.2],
  ['problemSolving', 'Problem-solving approach', 0.2],
  ['communication', 'Communication', 0.15],
  ['followUpHandling', 'Handling follow-ups', 0.15],
]

export default function InterviewReport() {
  const { id } = useParams()
  const navigate = useNavigate()
  const q = useQuery({ queryKey: ['interview-report', id], queryFn: () => api.get<Report>(`/api/interviews/${id}/report`) })
  const rematch = useMutation({
    mutationFn: () => api.post<SessionView>(`/api/interviews/${id}/rematch`),
    onSuccess: (s) => { toast.success('New room created with roles swapped'); navigate(`/interviews/${s.id}`) },
    onError: (e) => toast.error(errorMessage(e)),
  })
  if (q.isLoading) return <Skeleton className="h-[70vh]" />
  if (q.error) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const { session: s, evaluation: e } = q.data!

  return (
    <>
      <Link to="/interviews" className="mb-4 inline-flex min-h-11 items-center gap-1.5 text-sm text-muted hover:text-text">
        <ArrowLeft className="size-4" aria-hidden /> Interviews
      </Link>
      <PageHeader eyebrow={`// report · ${s.endedAt ? shortDate(s.endedAt) : ''}`} title={`${s.typeLabel} interview`}
        description={`${s.candidate?.displayName ?? 'Candidate'} interviewed by ${s.interviewer.displayName}. Scores are the interviewer's own; the overall is a fixed weighted average.`}
        actions={<Button loading={rematch.isPending} onClick={() => rematch.mutate()}><Repeat className="size-4" aria-hidden /> Rematch, swap roles</Button>} />

      <div className="grid gap-6 lg:grid-cols-[320px_1fr]">
        <section className="panel p-6">
          <p className="eyebrow">Overall</p>
          <p className="num mt-1 font-mono text-5xl font-semibold">{Number(e.overall).toFixed(2)}<span className="text-xl text-muted"> / 5</span></p>
          <p className="mt-1 text-lg font-extrabold text-live-text">{e.band}</p>
          <ul className="mt-6 space-y-4">
            {CRITERIA.map(([k, label, w]) => (
              <li key={k}>
                <div className="mb-1 flex justify-between text-sm">
                  <span>{label}</span>
                  <span className="num font-mono text-xs text-muted">{e[k] as number}/5 · {Math.round(w * 100)}%</span>
                </div>
                <Meter value={(e[k] as number) / 5} label={label} />
              </li>
            ))}
          </ul>
          {e.evaluator && (
            <p className="mt-6 flex items-center gap-2 text-xs text-muted">
              <Avatar name={e.evaluator.displayName} url={e.evaluator.avatarUrl} size={20} /> Scored by {e.evaluator.displayName}
            </p>
          )}
        </section>
        <div className="space-y-6">
          <section className="grid gap-4 md:grid-cols-2">
            <div className="panel border-l-2 border-l-success p-5">
              <p className="eyebrow mb-2">Strengths</p>
              <p className="whitespace-pre-wrap text-sm leading-relaxed">{e.strengths}</p>
            </div>
            <div className="panel border-l-2 border-l-warning p-5">
              <p className="eyebrow mb-2">To improve</p>
              <p className="whitespace-pre-wrap text-sm leading-relaxed">{e.improvements}</p>
            </div>
            {e.summary && (
              <div className="panel p-5 md:col-span-2">
                <p className="eyebrow mb-2">Summary</p>
                <p className="whitespace-pre-wrap text-sm leading-relaxed">{e.summary}</p>
              </div>
            )}
          </section>
          <section aria-labelledby="qs-h">
            <h2 id="qs-h" className="mb-3 text-lg">Questions asked</h2>
            <ol className="space-y-3">
              {q.data!.questions.map((x) => (
                <li key={x.position} className="panel p-4">
                  <p className="font-medium"><span className="num mr-2 font-mono text-sm text-muted">Q{x.position + 1}</span>{x.title}</p>
                  <p className="mt-1 text-sm text-muted">{x.prompt}</p>
                  <p className="eyebrow mt-3">What a strong answer covers</p>
                  <p className="mt-1 text-sm">{x.expectedConcepts.join(' · ')}</p>
                </li>
              ))}
            </ol>
          </section>
          {q.data!.finalCode.trim() && (
            <section aria-labelledby="code-h">
              <h2 id="code-h" className="mb-3 text-lg">Final code <span className="font-mono text-xs text-muted">· {q.data!.codeSubmissions} submission(s)</span></h2>
              <div className="panel h-80 overflow-hidden"><CodeEditor value={q.data!.finalCode} readOnly /></div>
            </section>
          )}
        </div>
      </div>
    </>
  )
}
