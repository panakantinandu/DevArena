import { useNavigate } from 'react-router'
import { ProfileForm } from '@/components/ProfileForm'
import type { Me } from '@/lib/types'

const STEPS = [
  ['Daily missions', 'A short, planned set each day, weighted toward what you get wrong.'],
  ['Battles', 'Live quiz rounds against a friend. The server keeps score.'],
  ['Peer interviews', 'Take turns as interviewer, with answer guides and a shared rubric.'],
]

export default function Onboarding({ me }: { me: Me }) {
  const navigate = useNavigate()
  return (
    <div className="grid-bg min-h-dvh">
      <div className="mx-auto grid max-w-6xl gap-10 px-5 py-12 lg:grid-cols-[0.8fr_1.2fr] lg:py-20">
        <aside>
          <p className="eyebrow">// setup · 1 of 1</p>
          <h1 className="mt-2 text-4xl font-semibold">Hi {me.displayName.split(' ')[0]}, let&apos;s tune your plan</h1>
          <p className="mt-3 text-muted">
            These settings shape your daily missions. You can change them any time in settings.
          </p>
          <ol className="mt-8 space-y-5">
            {STEPS.map(([title, body], i) => (
              <li key={title} className="flex gap-4">
                <span className="font-mono text-sm text-live-text">0{i + 1}</span>
                <div>
                  <p className="text-lg font-extrabold">{title}</p>
                  <p className="text-sm text-muted">{body}</p>
                </div>
              </li>
            ))}
          </ol>
        </aside>
        <section className="panel p-6 sm:p-8">
          <ProfileForm me={me} submitLabel="Start day 1" onSaved={() => navigate('/', { replace: true })} />
        </section>
      </div>
    </div>
  )
}
