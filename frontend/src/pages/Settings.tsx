import { useQuery } from '@tanstack/react-query'
import { Award, Lock } from 'lucide-react'
import { ProfileForm } from '@/components/ProfileForm'
import { ThemeToggle } from '@/components/ThemeToggle'
import { ErrorState, Meter, PageHeader, Skeleton } from '@/components/ui'
import { api } from '@/lib/api'
import { useMe } from '@/lib/auth'
import type { GamificationMe } from '@/lib/types'
import { cn, shortDate } from '@/lib/utils'

export default function SettingsPage() {
  const { data: me } = useMe()
  const game = useQuery({ queryKey: ['gamification'], queryFn: () => api.get<GamificationMe>('/api/gamification/me') })
  if (!me) return null
  return (
    <>
      <PageHeader eyebrow="// account" title="Profile & settings" description={`Signed in as ${me.email}. Your email is never shown to other users.`}
        actions={<div className="flex items-center gap-3"><span className="eyebrow">Theme</span><ThemeToggle withLabels /></div>} />
      <div className="grid gap-8 xl:grid-cols-[1.4fr_1fr]">
        <section className="panel p-6" aria-labelledby="profile-h">
          <h2 id="profile-h" className="mb-5 text-lg">Profile and study plan</h2>
          <ProfileForm me={me} submitLabel="Save changes" />
        </section>
        <section aria-labelledby="ach-h">
          <h2 id="ach-h" className="mb-3 text-lg">Achievements</h2>
          {game.isLoading && <Skeleton className="h-64" />}
          {game.error && <ErrorState error={game.error} onRetry={() => void game.refetch()} />}
          {game.data && (
            <ul className="divide-y divide-line rounded-panel border-2 border-line">
              {game.data.achievements.map((a) => {
                const unlocked = !!a.unlockedAt
                return (
                  <li key={a.code} className="flex items-center gap-3 px-4 py-3">
                    <span className={cn('grid size-9 shrink-0 place-items-center rounded-[4px] border',
                      unlocked ? 'border-warning/40 text-warning' : 'border-line text-muted')}>
                      {unlocked ? <Award className="size-4" aria-hidden /> : <Lock className="size-4" aria-hidden />}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className={cn('text-sm font-medium', !unlocked && 'text-muted')}>{a.name}</p>
                      <p className="text-xs text-muted">{a.description}</p>
                      {!unlocked && (
                        <Meter className="mt-2" tone="warning" value={a.progress / a.threshold}
                          label={`${a.name} progress`} />
                      )}
                    </div>
                    <span className="num shrink-0 font-mono text-xs text-muted">
                      {unlocked ? shortDate(a.unlockedAt!) : `${Math.min(a.progress, a.threshold)}/${a.threshold}`}
                    </span>
                  </li>
                )
              })}
            </ul>
          )}
        </section>
      </div>
    </>
  )
}
