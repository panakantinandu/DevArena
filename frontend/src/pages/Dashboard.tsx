import { useQuery } from '@tanstack/react-query'
import { Brain, DoorOpen, Flame, LineChart, MessagesSquare, Play, Sparkles, Swords, Users } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { JoinRoomDialog } from '@/components/JoinRoomDialog'
import { MissionRow } from '@/components/MissionRow'
import { Avatar, Button, EmptyState, ErrorState, Meter, Skeleton } from '@/components/ui'
import { api } from '@/lib/api'
import type { Dashboard as DashboardData, FeedItem, TodayResponse } from '@/lib/types'
import { cn, relativeTime } from '@/lib/utils'

function greeting() {
  const h = new Date().getHours()
  return h < 5 ? 'Late session' : h < 12 ? 'Morning' : h < 18 ? 'Afternoon' : 'Evening'
}

export default function Dashboard() {
  const dash = useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<DashboardData>('/api/dashboard') })
  const today = useQuery({ queryKey: ['missions', 'today'], queryFn: () => api.get<TodayResponse>('/api/missions/today') })
  const [joinOpen, setJoinOpen] = useState(false)

  if (dash.error) return <ErrorState error={dash.error} onRetry={() => void dash.refetch()} />
  const w = dash.data?.welcome

  return (
    <div className="space-y-8">
      {/* Welcome strip */}
      <section className="flex flex-wrap items-end justify-between gap-6 border-b-2 border-line pb-6">
        <div>
          {w ? (
            <p className="eyebrow">// day {String(w.roadmapDay).padStart(2, '0')} / 90 · {w.phase}</p>
          ) : (
            <Skeleton className="h-3 w-48" />
          )}
          <h1 className="mt-2 text-[2.1rem] font-semibold">
            {greeting()}, {w ? w.displayName.split(' ')[0] : '…'}
          </h1>
          {w && (
            <p className="mt-1 text-sm text-muted">
              {w.completedToday >= w.dailyMinimum
                ? `Daily target met: ${w.completedToday} missions done. Everything else today is extra credit.`
                : `${w.dailyMinimum - w.completedToday} more mission${w.dailyMinimum - w.completedToday === 1 ? '' : 's'} to keep your streak.`}
            </p>
          )}
        </div>
        {w ? (
          <dl className="flex flex-wrap gap-x-8 gap-y-4">
            <Stat label="Streak" value={`${w.currentStreak}d`} icon={<Flame className={cn('size-4', w.currentStreak > 0 ? 'text-warning' : 'text-muted')} aria-hidden />}
              sub={`best ${w.longestStreak}d`} />
            <Stat label="Total XP" value={w.totalXp.toLocaleString()} sub={`level ${w.level.level}`} />
            <div className="min-w-44">
              <dt className="eyebrow">Rank</dt>
              <dd className="mt-1 text-lg font-extrabold">{w.level.title}</dd>
              {w.level.nextLevelXp != null ? (
                <>
                  <Meter className="mt-1.5" value={(w.totalXp - w.level.currentLevelXp) / (w.level.nextLevelXp - w.level.currentLevelXp)}
                    label="Progress to next level" />
                  <dd className="num mt-1 font-mono text-2xs text-muted">{w.level.nextLevelXp - w.totalXp} xp to next</dd>
                </>
              ) : <dd className="font-mono text-2xs text-muted">max level</dd>}
            </div>
          </dl>
        ) : <Skeleton className="h-14 w-full max-w-96" />}
      </section>

      <div className="grid gap-10 xl:grid-cols-[1.55fr_1fr]">
        {/* Today's missions */}
        <section aria-labelledby="today-h">
          <div className="mb-4 flex items-baseline justify-between">
            <h2 id="today-h" className="text-xl">Today&apos;s missions</h2>
            {today.data && (
              <span className="num font-mono text-sm text-muted">
                {today.data.completedCount}/{today.data.missions.length} done
              </span>
            )}
          </div>
          {today.isLoading && <div className="space-y-3">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-16" />)}</div>}
          {today.error && <ErrorState error={today.error} onRetry={() => void today.refetch()} />}
          {today.data && (
            today.data.missions.length === 0 ? (
              <EmptyState icon={Sparkles} title="No missions planned" body="There's no content to plan from yet. Ask an admin to seed the question bank." />
            ) : (
              <ol>{today.data.missions.map((m, i) => <MissionRow key={m.id} m={m} last={i === today.data.missions.length - 1} />)}</ol>
            )
          )}

          <div className="mt-6 grid grid-cols-2 gap-2 sm:grid-cols-3">
            <QuickAction to="/practice" icon={Play} label="Start practice" />
            <QuickAction to="/arena?create=1" icon={Swords} label="Create quiz battle" accent />
            <QuickAction to="/interviews?create=1" icon={MessagesSquare} label="Create interview room" />
            <button type="button" onClick={() => setJoinOpen(true)}
              className="flex min-h-12 cursor-pointer items-center gap-2.5 bg-live text-left text-ink rounded-ctl border-2 border-line px-3 text-sm font-bold shadow-[3px_3px_0_0_var(--shadow)] transition-[transform,box-shadow] duration-100 hover:-translate-x-px hover:-translate-y-px hover:shadow-[4px_4px_0_0_var(--shadow)] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none">
              <DoorOpen className="size-4" aria-hidden /> Join room
            </button>
            <QuickAction to="/gym" icon={Brain} label="Daily brain gym" />
            <QuickAction to="/progress" icon={LineChart} label="View progress" />
          </div>
        </section>

        {/* Rail */}
        <aside className="space-y-8">
          <section aria-labelledby="week-h">
            <h2 id="week-h" className="mb-3 text-lg">This week</h2>
            {dash.data ? <WeekBars days={dash.data.week} /> : <Skeleton className="h-28" />}
          </section>

          <section aria-labelledby="skills-h">
            <div className="mb-3 flex items-baseline justify-between">
              <h2 id="skills-h" className="text-lg">Skill overview</h2>
              <Link to="/progress" className="text-xs text-muted hover:text-text">Details</Link>
            </div>
            {dash.data ? (
              <ul className="space-y-3">
                {dash.data.skills.map((s) => (
                  <li key={s.category}>
                    <div className="mb-1 flex justify-between text-sm">
                      <span>{s.label}</span>
                      <span className="num font-mono text-xs text-muted">
                        {s.mastery == null ? 'not started' : `${Math.round(s.mastery * 100)}%`}
                      </span>
                    </div>
                    <Meter value={s.mastery ?? 0} tone="live" label={`${s.label} mastery`} />
                  </li>
                ))}
              </ul>
            ) : <Skeleton className="h-60" />}
            <p className="mt-3 text-2xs text-muted">
              Mastery is a study aid calculated from your answers (accuracy, difficulty, recency). It is not a hiring prediction.
            </p>
          </section>

          <section aria-labelledby="feed-h">
            <h2 id="feed-h" className="mb-3 text-lg">Friend activity</h2>
            {dash.data && (dash.data.friendActivity.length === 0 ? (
              <EmptyState icon={Users} title="Quiet in here" body="Add your study partner to see their progress."
                action={<Link to="/friends"><Button size="sm">Find friends</Button></Link>} />
            ) : (
              <ul className="space-y-3">{dash.data.friendActivity.map((f, i) => <FeedRow key={i} item={f} />)}</ul>
            ))}
          </section>
        </aside>
      </div>
      <JoinRoomDialog open={joinOpen} onOpenChange={setJoinOpen} />
    </div>
  )
}

function Stat({ label, value, sub, icon }: { label: string; value: string; sub?: string; icon?: React.ReactNode }) {
  return (
    <div>
      <dt className="eyebrow">{label}</dt>
      <dd className="num mt-1 flex items-center gap-1.5 font-mono text-2xl font-semibold">{icon}{value}</dd>
      {sub && <dd className="font-mono text-2xs text-muted">{sub}</dd>}
    </div>
  )
}

function QuickAction({ to, icon: Icon, label, accent }: { to: string; icon: React.ComponentType<{ className?: string }>; label: string; accent?: boolean }) {
  return (
    <Link to={to}
      className={cn('flex min-h-12 items-center gap-2.5 rounded-ctl border-2 border-line px-3 text-sm font-bold shadow-[3px_3px_0_0_var(--shadow)] transition-[transform,box-shadow] duration-100 hover:-translate-x-px hover:-translate-y-px hover:shadow-[4px_4px_0_0_var(--shadow)] active:translate-x-[3px] active:translate-y-[3px] active:shadow-none',
        accent ? 'bg-brand text-ink' : 'bg-surface-1')}>
      <Icon className="size-4" aria-hidden /> {label}
    </Link>
  )
}

function WeekBars({ days }: { days: DashboardData['week'] }) {
  const max = Math.max(1, ...days.map((d) => d.xp))
  return (
    <div className="panel p-4">
      <div className="flex h-24 items-end gap-2" role="img"
        aria-label={`XP per day this week: ${days.map((d) => `${new Date(d.date + 'T00:00').toLocaleDateString(undefined, { weekday: 'short' })} ${d.xp}`).join(', ')}`}>
        {days.map((d) => (
          <div key={d.date} className="flex flex-1 flex-col items-center gap-1.5">
            <span className="num font-mono text-2xs text-muted">{d.xp || ''}</span>
            <div className={cn('w-full rounded-t-[3px] border-2 border-b-0 border-line', d.missions > 0 ? 'bg-brand' : d.xp > 0 ? 'bg-live' : 'bg-surface-2')}
              style={{ height: `${Math.max(4, (d.xp / max) * 64)}px` }} />
          </div>
        ))}
      </div>
      <div className="mt-2 flex gap-2">
        {days.map((d) => (
          <span key={d.date} className="flex-1 text-center font-mono text-2xs uppercase text-muted">
            {new Date(d.date + 'T00:00').toLocaleDateString(undefined, { weekday: 'narrow' })}
          </span>
        ))}
      </div>
    </div>
  )
}

const FEED_TEXT: Record<string, (t: string) => string> = {
  MISSION: (t) => `finished "${t}"`,
  BATTLE_WIN: (t) => `won a ${t.toLowerCase().replace('_', ' ')} battle`,
  ACHIEVEMENT: (t) => `unlocked "${t}"`,
  INTERVIEW: () => 'completed a mock interview',
}

function FeedRow({ item }: { item: FeedItem }) {
  if (!item.user) return null
  return (
    <li className="flex items-start gap-3 text-sm">
      <Avatar name={item.user.displayName} url={item.user.avatarUrl} size={28} />
      <p className="min-w-0 flex-1">
        <Link to={`/users/${item.user.id}`} className="font-medium hover:underline">{item.user.displayName}</Link>{' '}
        <span className="text-muted">{(FEED_TEXT[item.kind] ?? ((t: string) => t))(item.text)}</span>
        <span className="block font-mono text-2xs text-muted">{relativeTime(item.at)}</span>
      </p>
    </li>
  )
}
