import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'
import { Button, Field, Input } from '@/components/ui'
import { errorMessage } from '@/lib/api'
import { useLogin } from '@/lib/auth'
import { AuthLayout } from './AuthLayout'

const schema = z.object({
  login: z.string().trim().min(1, 'Enter your username or email'),
  password: z.string().min(1, 'Enter your password'),
})
type Values = z.infer<typeof schema>

export default function Login() {
  const login = useLogin()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { register, handleSubmit, formState: { errors } } = useForm<Values>({ resolver: zodResolver(schema) })

  const next = params.get('next')
  const safeNext = next && next.startsWith('/') && !next.startsWith('//') ? next : '/'

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to pick up today's missions.">
      <form
        noValidate
        className="space-y-4"
        onSubmit={handleSubmit((v) => login.mutate(v, { onSuccess: () => navigate(safeNext, { replace: true }) }))}
      >
        <Field id="login" label="Username or email" error={errors.login?.message}>
          <Input autoComplete="username" autoFocus {...register('login')} />
        </Field>
        <Field id="password" label="Password" error={errors.password?.message}>
          <Input type="password" autoComplete="current-password" {...register('password')} />
        </Field>
        {login.isError && (
          <p role="alert" className="rounded-ctl border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
            {errorMessage(login.error)}
          </p>
        )}
        <Button type="submit" variant="primary" className="w-full" loading={login.isPending}>
          Sign in
        </Button>
      </form>
      <p className="mt-6 text-sm text-muted">
        New here?{' '}
        <Link to="/register" className="text-brand-text underline-offset-4 hover:underline">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  )
}
