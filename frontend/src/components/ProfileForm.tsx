import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { api, ApiError, errorMessage } from '@/lib/api'
import { meKey } from '@/lib/auth'
import type { Me } from '@/lib/types'
import { Button, Field, Input, Select, Textarea } from './ui'

const schema = z.object({
  displayName: z.string().trim().min(2, 'At least 2 characters').max(60),
  avatarUrl: z.string().trim().max(500).refine((v) => v === '' || v.startsWith('https://'), 'Use an https:// image URL'),
  bio: z.string().max(500, 'Keep it under 500 characters'),
  preferredLanguage: z.enum(['JAVA', 'PYTHON', 'JAVASCRIPT', 'TYPESCRIPT', 'GO', 'CPP', 'KOTLIN']),
  targetRole: z.enum(['BACKEND_ENGINEER', 'FULL_STACK_ENGINEER', 'FRONTEND_ENGINEER', 'PLATFORM_ENGINEER', 'AI_ENGINEER']),
  prepLevel: z.enum(['BEGINNER', 'INTERMEDIATE', 'ADVANCED']),
  dailyStudyMinutes: z.coerce.number().int().min(15, 'At least 15 minutes').max(480, 'At most 8 hours'),
  dailyMissionMinimum: z.coerce.number().int().min(1).max(7),
  timeZone: z.string().min(1),
})
type Values = z.infer<typeof schema>

const ZONES = (() => {
  try {
    return (Intl as unknown as { supportedValuesOf(k: string): string[] }).supportedValuesOf('timeZone')
  } catch {
    return ['UTC']
  }
})()

export function ProfileForm({ me, submitLabel, onSaved }: { me: Me; submitLabel: string; onSaved?: () => void }) {
  const qc = useQueryClient()
  const { register, handleSubmit, setError, formState: { errors, isDirty } } = useForm<z.input<typeof schema>, unknown, Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      displayName: me.displayName,
      avatarUrl: me.avatarUrl ?? '',
      bio: me.bio ?? '',
      preferredLanguage: me.settings.preferredLanguage as Values['preferredLanguage'],
      targetRole: me.settings.targetRole as Values['targetRole'],
      prepLevel: me.settings.prepLevel as Values['prepLevel'],
      dailyStudyMinutes: me.settings.dailyStudyMinutes,
      dailyMissionMinimum: me.settings.dailyMissionMinimum,
      timeZone: me.settings.timeZone,
    },
  })

  const save = useMutation({
    mutationFn: (v: Values) => api.put<Me>('/api/users/me', v),
    onSuccess: (updated) => {
      qc.setQueryData(meKey, updated)
      void qc.invalidateQueries({ queryKey: ['missions'] })
      toast.success('Profile saved')
      onSaved?.()
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        for (const [f, m] of Object.entries(e.fieldErrors)) setError(f as keyof Values, { message: m })
      }
      toast.error(errorMessage(e))
    },
  })

  return (
    <form noValidate onSubmit={handleSubmit((v) => save.mutate(v))} className="grid gap-5 md:grid-cols-2">
      <Field id="displayName" label="Display name" error={errors.displayName?.message}>
        <Input {...register('displayName')} />
      </Field>
      <Field id="avatarUrl" label="Avatar URL" hint="Optional https image link." error={errors.avatarUrl?.message}>
        <Input inputMode="url" placeholder="https://…" {...register('avatarUrl')} />
      </Field>
      <div className="md:col-span-2">
        <Field id="bio" label="Bio" hint="Visible to other users." error={errors.bio?.message}>
          <Textarea rows={3} {...register('bio')} />
        </Field>
      </div>
      <Field id="targetRole" label="Target role" error={errors.targetRole?.message}>
        <Select {...register('targetRole')}>
          <option value="BACKEND_ENGINEER">Backend engineer</option>
          <option value="FULL_STACK_ENGINEER">Full-stack engineer</option>
          <option value="FRONTEND_ENGINEER">Frontend engineer</option>
          <option value="PLATFORM_ENGINEER">Platform engineer</option>
          <option value="AI_ENGINEER">AI engineer</option>
        </Select>
      </Field>
      <Field id="prepLevel" label="Where are you now?" error={errors.prepLevel?.message}>
        <Select {...register('prepLevel')}>
          <option value="BEGINNER">Getting started</option>
          <option value="INTERMEDIATE">Comfortable with the basics</option>
          <option value="ADVANCED">Interview-ready, sharpening</option>
        </Select>
      </Field>
      <Field id="preferredLanguage" label="Preferred language" error={errors.preferredLanguage?.message}>
        <Select {...register('preferredLanguage')}>
          {['JAVA', 'KOTLIN', 'PYTHON', 'JAVASCRIPT', 'TYPESCRIPT', 'GO', 'CPP'].map((l) => (
            <option key={l} value={l}>{l === 'CPP' ? 'C++' : l.charAt(0) + l.slice(1).toLowerCase()}</option>
          ))}
        </Select>
      </Field>
      <Field id="timeZone" label="Time zone" hint="Streaks and daily missions follow this." error={errors.timeZone?.message}>
        <Select {...register('timeZone')}>
          {ZONES.map((z) => <option key={z} value={z}>{z}</option>)}
        </Select>
      </Field>
      <Field id="dailyStudyMinutes" label="Daily study time (minutes)" hint="Missions are planned to fit this." error={errors.dailyStudyMinutes?.message}>
        <Input type="number" min={15} max={480} step={5} {...register('dailyStudyMinutes')} />
      </Field>
      <Field id="dailyMissionMinimum" label="Missions per day for your streak" error={errors.dailyMissionMinimum?.message}>
        <Select {...register('dailyMissionMinimum')}>
          {[1, 2, 3, 4, 5, 6, 7].map((n) => <option key={n} value={n}>{n}</option>)}
        </Select>
      </Field>
      <div className="md:col-span-2">
        <Button type="submit" variant="primary" loading={save.isPending} disabled={!isDirty && me.settings.onboarded}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
