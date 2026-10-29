import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Bookmark, BookmarkCheck } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Explanation, QuestionCard } from '@/components/QuestionCard'
import { Button, ErrorState, Skeleton } from '@/components/ui'
import { api, errorMessage } from '@/lib/api'
import type { AnswerResult, QuestionView } from '@/lib/types'

export default function PracticeQuestion() {
  const { id } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const q = useQuery({ queryKey: ['question', id], queryFn: () => api.get<QuestionView>(`/api/questions/${id}`) })
  const [selected, setSelected] = useState<string | null>(null)
  const answer = useMutation({
    mutationFn: () => api.post<AnswerResult>(`/api/questions/${id}/answer`, { optionId: selected }),
    onSuccess: (r) => {
      void qc.invalidateQueries({ queryKey: ['questions'] })
      if (r.xpAwarded) toast.success(`+${r.xpAwarded} XP`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const bookmark = useMutation({
    mutationFn: () => api.post<{ bookmarked: boolean }>(`/api/questions/${id}/bookmark`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['question', id] }),
  })

  if (q.isLoading) return <Skeleton className="h-96" />
  if (q.error) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const question = q.data!
  const result = answer.data

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-4 flex items-center justify-between">
        <button type="button" onClick={() => navigate(-1)} className="inline-flex min-h-11 cursor-pointer items-center gap-1.5 text-sm text-muted hover:text-text">
          <ArrowLeft className="size-4" aria-hidden /> Back to library
        </button>
        <Button variant="ghost" size="icon" aria-pressed={question.bookmarked}
          aria-label={question.bookmarked ? 'Remove bookmark' : 'Bookmark question'} onClick={() => bookmark.mutate()}>
          {question.bookmarked ? <BookmarkCheck className="size-4 text-warning" /> : <Bookmark className="size-4" />}
        </Button>
      </div>
      <section className="panel p-6 sm:p-8" aria-live="polite">
        <QuestionCard question={question} selected={selected} onSelect={setSelected} correctOptionId={result?.correctOptionId} />
        {result && <Explanation correct={result.correct} text={result.explanation} />}
        <div className="mt-6 flex gap-3">
          {!result ? (
            <Button variant="primary" disabled={!selected} loading={answer.isPending} onClick={() => answer.mutate()}>Check answer</Button>
          ) : (
            <>
              <Button onClick={() => { setSelected(null); answer.reset() }}>Try again</Button>
              <Link to={`/practice?topic=${question.topic.slug}`}><Button variant="primary">More {question.topic.name}</Button></Link>
            </>
          )}
        </div>
      </section>
    </div>
  )
}
