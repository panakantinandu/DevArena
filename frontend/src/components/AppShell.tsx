import {
  Brain, Code2, LayoutDashboard, Library, LineChart, ListChecks, LogOut, Menu, MessagesSquare, Network, PanelLeftClose,
  PanelLeftOpen, Settings, ShieldCheck, Swords, Trophy, UserPlus, WifiOff, X,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { useLogout } from '@/lib/auth'
import { realtime, useConnection } from '@/lib/realtime'
import type { Me, PublicUser } from '@/lib/types'
import { cn } from '@/lib/utils'
import { ThemeToggle } from './ThemeToggle'
import { Avatar, Button } from './ui'

const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/missions', label: 'Daily missions', icon: ListChecks },
  { to: '/practice', label: 'Practice library', icon: Library },
  { to: '/gym', label: 'Brain gym', icon: Brain },
  { to: '/arena', label: 'Battle arena', icon: Swords },
  { to: '/interviews', label: 'Interview rooms', icon: MessagesSquare },
  { to: '/coding', label: 'Coding workspace', icon: Code2 },
  { to: '/design', label: 'System design', icon: Network },
  { to: '/progress', label: 'Progress', icon: LineChart },
  { to: '/leaderboard', label: 'Leaderboard', icon: Trophy },
  { to: '/friends', label: 'Friends', icon: UserPlus },
  { to: '/settings', label: 'Profile & settings', icon: Settings },
]

export function AppShell({ me }: { me: Me }) {
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem('nav-collapsed') === '1')
  const [mobileOpen, setMobileOpen] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => localStorage.setItem('nav-collapsed', collapsed ? '1' : '0'), [collapsed])
  useEffect(() => setMobileOpen(false), [location.pathname])

  useEffect(() => {
    realtime.start()
    return realtime.onGlobal((msg) => {
      const data = msg.data as { from?: PublicUser; by?: PublicUser; roomCode?: string; sessionId?: string; typeLabel?: string }
      if (msg.type === 'INTERVIEW_INVITE' && data.from) {
        toast(`${data.from.displayName} invited you to a ${data.typeLabel ?? ''} interview`, {
          action: { label: 'Open', onClick: () => navigate(`/interviews/join/${data.roomCode}`) },
          duration: 20_000,
        })
      } else if (msg.type === 'FRIEND_REQUEST' && data.from) {
        toast(`${data.from.displayName} sent you a friend request`, {
          action: { label: 'View', onClick: () => navigate('/friends') },
        })
      } else if (msg.type === 'FRIEND_ACCEPTED' && data.by) {
        toast.success(`${data.by.displayName} accepted your friend request`)
      }
    })
  }, [navigate])

  const items = me.role === 'ADMIN' ? [...NAV, { to: '/admin/questions', label: 'Question admin', icon: ShieldCheck }] : NAV

  return (
    <div className="min-h-dvh">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-50 focus:bg-surface-2 focus:px-3 focus:py-2">
        Skip to content
      </a>

      {/* Mobile top bar */}
      <div className="sticky top-0 z-30 flex h-14 items-center justify-between border-b-2 border-line bg-bg px-3 lg:hidden">
        <Button variant="ghost" size="icon" aria-label="Open navigation" onClick={() => setMobileOpen(true)}>
          <Menu className="size-5" />
        </Button>
        <Wordmark />
        <ThemeToggle className="scale-90" />
      </div>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 bg-black/60 lg:hidden" onClick={() => setMobileOpen(false)} aria-hidden />
      )}

      <aside
        aria-label="Main navigation"
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex flex-col border-r-2 border-line bg-surface-1 transition-[width,transform] duration-200',
          collapsed ? 'lg:w-16' : 'lg:w-60',
          'w-72 lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className={cn('flex h-16 items-center border-b-2 border-line px-4', collapsed ? 'lg:justify-center lg:px-0' : 'justify-between')}>
          <span className={cn(collapsed && 'lg:hidden')}><Wordmark /></span>
          <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Close navigation" onClick={() => setMobileOpen(false)}>
            <X className="size-5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="hidden lg:inline-flex"
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            onClick={() => setCollapsed((c) => !c)}
          >
            {collapsed ? <PanelLeftOpen className="size-4" /> : <PanelLeftClose className="size-4" />}
          </Button>
        </div>
        <nav className="flex-1 overflow-y-auto px-2 py-3">
          <ul className="space-y-0.5">
            {items.map(({ to, label, icon: Icon, end }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  end={end}
                  title={collapsed ? label : undefined}
                  className={({ isActive }) =>
                    cn(
                      'group relative flex min-h-11 items-center gap-3 rounded-ctl border-2 px-3 text-sm font-semibold transition-colors',
                      collapsed && 'lg:justify-center lg:px-0',
                      isActive ? 'border-line bg-brand text-ink shadow-[3px_3px_0_0_var(--shadow)]' : 'border-transparent text-muted hover:border-line hover:text-text',
                    )
                  }
                >
                  <Icon className="size-[18px] shrink-0" aria-hidden />
                  <span className={cn(collapsed && 'lg:sr-only')}>{label}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <UserFooter me={me} collapsed={collapsed} />
      </aside>

      <div className={cn('transition-[padding] duration-200', collapsed ? 'lg:pl-16' : 'lg:pl-60')}>
        <ConnectionBanner />
        <main id="main" className="mx-auto w-full max-w-[1280px] px-4 py-6 sm:px-6 lg:px-10 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function Wordmark() {
  return (
    <span className="flex items-center gap-1.5 font-display text-[0.95rem] font-extrabold tracking-[-0.04em]">
      <img src="/favicon.svg" alt="" className="size-7" />
      DEV<span className="rounded-[4px] border-2 border-line bg-live px-1 text-ink">ARENA</span>
    </span>
  )
}

function UserFooter({ me, collapsed }: { me: Me; collapsed: boolean }) {
  const logout = useLogout()
  return (
    <div className="border-t-2 border-line p-3">
      <ThemeToggle className={cn('mb-3 w-full justify-between', collapsed && 'lg:hidden')} />
      <div className={cn('flex items-center gap-2', collapsed && 'lg:flex-col')}>
      <Avatar name={me.displayName} url={me.avatarUrl} />
      <div className={cn('min-w-0 flex-1', collapsed && 'lg:hidden')}>
        <p className="truncate text-sm font-medium">{me.displayName}</p>
        <p className="truncate font-mono text-2xs text-muted">@{me.username}</p>
      </div>
      <Button variant="ghost" size="icon" aria-label="Sign out" loading={logout.isPending} onClick={() => logout.mutate()}>
        <LogOut className="size-4" />
      </Button>
      </div>
    </div>
  )
}

/** Visible whenever the live connection drops, e.g. while a free-tier backend wakes up. */
function ConnectionBanner() {
  const { state, attempt } = useConnection()
  if (state !== 'reconnecting') return null
  return (
    <div role="status" className="flex items-center justify-center gap-2 border-b-2 border-line bg-brand px-4 py-2 text-sm font-bold text-ink">
      <WifiOff className="size-4" aria-hidden />
      Live connection lost. Reconnecting{attempt > 2 ? '; the server may be waking up' : ''}…
    </div>
  )
}
