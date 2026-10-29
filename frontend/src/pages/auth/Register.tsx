import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { Button, Field, Input } from '@/components/ui'
import { ApiError, errorMessage } from '@/lib/api'
import { useRegister } from '@/lib/auth'
import { AuthLayout } from './AuthLayout'

const schema = z.object({
  displayName: z.string().trim().min(2, 'At least 2 characters').max(60),
  username: z.string().trim().min(3, 'At least 3 characters').max(32)
    .regex(/^[a-zA-Z0-9_]+$/, 'Letters, numbers and underscores only'),
  email: z.email('Enter a valid email'),
  password: z.string().min(10, 'Use at least 10 characters').max(128),
})
type Values = z.infer<typeof schema>

export default function Register() {
  const reg = useRegister()
  const navigate = useNavigate()
  const { register, handleSubmit, setError, formState: { errors } } = useForm<Values>({ resolver: zodResolver(schema) })

  const onSubmit = (v: Values) =>
    reg.mutate(
      { ...v, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone },
      {
        onSuccess: () => navigate('/welcome', { replace: true }),
        onError: (e) => {
          if (e instanceof ApiError) {
            if (e.code === 'EMAIL_TAKEN') setError('email', { message: e.message })
            if (e.code === 'USERNAME_TAKEN') setError('username', { message: e.message })
            for (const [f, m] of Object.entries(e.fieldErrors)) setError(f as keyof Values, { message: m })
          }
        },
      },
    )

  const generic = reg.error instanceof ApiError && !['EMAIL_TAKEN', 'USERNAME_TAKEN', 'VALIDATION_FAILED'].includes(reg.error.code)

  return (
    <AuthLayout title="Create your account" subtitle="Your 90-day plan starts today, in your own time zone.">
      <form noValidate className="space-y-4" onSubmit={handleSubmit(onSubmit)}>
        <Field id="displayName" label="Display name" error={errors.displayName?.message}>
          <Input autoComplete="name" autoFocus {...register('displayName')} />
        </Field>
        <Field id="username" label="Username" hint="Friends find you by this." error={errors.username?.message}>
          <Input autoComplete="username" {...register('username')} />
        </Field>
        <Field id="email" label="Email" error={errors.email?.message}>
          <Input type="email" autoComplete="email" {...register('email')} />
        </Field>
        <Field id="password" label="Password" hint="10 or more characters." error={errors.password?.message}>
          <Input type="password" autoComplete="new-password" {...register('password')} />
        </Field>
        {generic && (
          <p role="alert" className="rounded-ctl border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
            {errorMessage(reg.error)}
          </p>
        )}
        <Button type="submit" variant="primary" className="w-full" loading={reg.isPending}>
          Create account
        </Button>
      </form>
      <p className="mt-6 text-sm text-muted">
        Already have an account?{' '}
        <Link to="/login" className="text-brand-text underline-offset-4 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
