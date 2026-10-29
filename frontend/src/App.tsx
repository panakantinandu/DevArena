import { lazy, Suspense, type ReactNode } from 'react'
import { createBrowserRouter, Navigate, Outlet, RouterProvider, useLocation } from 'react-router'
import { AppShell } from '@/components/AppShell'
import { ErrorState, Spinner } from '@/components/ui'
import { useMe } from '@/lib/auth'

const Login = lazy(() => import('@/pages/auth/Login'))
const Register = lazy(() => import('@/pages/auth/Register'))
const Onboarding = lazy(() => import('@/pages/Onboarding'))
const Dashboard = lazy(() => import('@/pages/Dashboard'))
const Missions = lazy(() => import('@/pages/missions/Missions'))
const MissionRun = lazy(() => import('@/pages/missions/MissionRun'))
const Practice = lazy(() => import('@/pages/practice/Practice'))
const PracticeQuestion = lazy(() => import('@/pages/practice/PracticeQuestion'))
const Arena = lazy(() => import('@/pages/arena/Arena'))
const BattleRoom = lazy(() => import('@/pages/arena/BattleRoom'))
const Interviews = lazy(() => import('@/pages/interviews/Interviews'))
const InterviewRoom = lazy(() => import('@/pages/interviews/InterviewRoom'))
const InterviewReport = lazy(() => import('@/pages/interviews/InterviewReport'))
const CodingList = lazy(() => import('@/pages/coding/CodingList'))
const CodingWorkspace = lazy(() => import('@/pages/coding/CodingWorkspace'))
const DesignList = lazy(() => import('@/pages/design/DesignList'))
const DesignWorkspace = lazy(() => import('@/pages/design/DesignWorkspace'))
const Gym = lazy(() => import('@/pages/gym/Gym'))
const GymRun = lazy(() => import('@/pages/gym/GymRun'))
const Progress = lazy(() => import('@/pages/Progress'))
const Leaderboard = lazy(() => import('@/pages/Leaderboard'))
const Friends = lazy(() => import('@/pages/Friends'))
const UserProfile = lazy(() => import('@/pages/UserProfile'))
const SettingsPage = lazy(() => import('@/pages/Settings'))
const AdminQuestions = lazy(() => import('@/pages/AdminQuestions'))
const NotFound = lazy(() => import('@/pages/NotFound'))

function Splash() {
  return (
    <div className="grid min-h-dvh place-items-center">
      <Spinner label="Loading DevArena" />
    </div>
  )
}

function Lazy({ children }: { children: ReactNode }) {
  return <Suspense fallback={<div className="grid min-h-[50vh] place-items-center"><Spinner /></div>}>{children}</Suspense>
}

function RequireAuth() {
  const { data: me, isLoading, error, refetch } = useMe()
  const location = useLocation()
  if (isLoading) return <Splash />
  if (error) {
    return (
      <div className="mx-auto max-w-md py-24">
        <ErrorState error={error} onRetry={() => void refetch()} />
      </div>
    )
  }
  if (!me) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />
  if (!me.settings.onboarded && location.pathname !== '/welcome') return <Navigate to="/welcome" replace />
  if (location.pathname === '/welcome') return <Lazy><Onboarding me={me} /></Lazy>
  return <AppShell me={me} />
}

function PublicOnly() {
  const { data: me, isLoading } = useMe()
  if (isLoading) return <Splash />
  if (me) return <Navigate to="/" replace />
  return <Lazy><Outlet /></Lazy>
}

const page = (el: ReactNode) => <Lazy>{el}</Lazy>

const router = createBrowserRouter([
  {
    element: <PublicOnly />,
    children: [
      { path: '/login', element: <Login /> },
      { path: '/register', element: <Register /> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      { path: '/welcome', element: null },
      { path: '/', element: page(<Dashboard />) },
      { path: '/missions', element: page(<Missions />) },
      { path: '/missions/:id', element: page(<MissionRun />) },
      { path: '/practice', element: page(<Practice />) },
      { path: '/practice/:id', element: page(<PracticeQuestion />) },
      { path: '/arena', element: page(<Arena />) },
      { path: '/arena/join/:code', element: page(<Arena />) },
      { path: '/arena/:id', element: page(<BattleRoom />) },
      { path: '/interviews', element: page(<Interviews />) },
      { path: '/interviews/join/:code', element: page(<Interviews />) },
      { path: '/interviews/:id', element: page(<InterviewRoom />) },
      { path: '/interviews/:id/report', element: page(<InterviewReport />) },
      { path: '/coding', element: page(<CodingList />) },
      { path: '/coding/:slug', element: page(<CodingWorkspace />) },
      { path: '/design', element: page(<DesignList />) },
      { path: '/design/:slug', element: page(<DesignWorkspace />) },
      { path: '/gym', element: page(<Gym />) },
      { path: '/gym/runs/:id', element: page(<GymRun />) },
      { path: '/progress', element: page(<Progress />) },
      { path: '/leaderboard', element: page(<Leaderboard />) },
      { path: '/friends', element: page(<Friends />) },
      { path: '/users/:id', element: page(<UserProfile />) },
      { path: '/settings', element: page(<SettingsPage />) },
      { path: '/admin/questions', element: page(<AdminQuestions />) },
      { path: '*', element: page(<NotFound />) },
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
