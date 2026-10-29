import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QuestionCard } from '@/components/QuestionCard'
import { ErrorState, Field, Input } from '@/components/ui'
import { ApiError } from '@/lib/api'
import type { QuestionView } from '@/lib/types'
import Login from '@/pages/auth/Login'
import Register from '@/pages/auth/Register'

const question: QuestionView = {
  id: 'q1', slug: 'q', difficulty: 'EASY', prompt: 'Which structure is LIFO?', codeSnippet: null, bookmarked: false,
  topic: { id: 't', slug: 'stacks-queues', name: 'Stacks & queues', category: 'DSA' },
  options: [{ id: 'a', body: 'Queue' }, { id: 'b', body: 'Stack' }, { id: 'c', body: 'Heap' }],
}

function renderWithProviders(ui: React.ReactElement, path = '/') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter([{ path: '*', element: ui }], { initialEntries: [path] })
  return render(<QueryClientProvider client={qc}><RouterProvider router={router} /></QueryClientProvider>)
}

afterEach(() => {
  vi.restoreAllMocks()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('QuestionCard', () => {
  it('exposes options as a radio group and reports selection', async () => {
    const onSelect = vi.fn()
    render(<QuestionCard question={question} selected={null} onSelect={onSelect} />)
    expect(screen.getByRole('radiogroup')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('radio', { name: /Stack/ }))
    expect(onSelect).toHaveBeenCalledWith('b')
  })

  it('marks the correct and the wrong answer with text, not colour alone, and locks input', () => {
    const onSelect = vi.fn()
    render(<QuestionCard question={question} selected="a" correctOptionId="b" onSelect={onSelect} />)
    expect(screen.getByText('(correct answer)')).toBeInTheDocument()
    expect(screen.getByText('(your answer, incorrect)')).toBeInTheDocument()
    for (const r of screen.getAllByRole('radio')) expect(r).toBeDisabled()
  })

  it('never renders correctness before an answer is revealed', () => {
    render(<QuestionCard question={question} selected="b" />)
    expect(screen.queryByText('(correct answer)')).not.toBeInTheDocument()
  })
})

describe('Field', () => {
  it('links label, hint and error for screen readers', () => {
    render(<Field id="email" label="Email" hint="We never share it" error="Enter a valid email"><Input /></Field>)
    const input = screen.getByLabelText('Email')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input.getAttribute('aria-describedby')).toContain('email-error')
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a valid email')
  })
})

describe('ErrorState', () => {
  it('explains an unreachable server and offers a retry', async () => {
    const retry = vi.fn()
    render(<ErrorState error={new ApiError(0, 'NETWORK', 'The server may be waking up')} onRetry={retry} />)
    expect(screen.getByText('Server unavailable')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /try again/i }))
    expect(retry).toHaveBeenCalled()
  })
})

describe('Auth forms', () => {
  it('validates the login form before calling the API', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    renderWithProviders(<Login />, '/login')
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Enter your username or email')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('shows the server message on bad credentials', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=test-token'
        return new Response(null, { status: 204 })
      }
      return new Response(JSON.stringify({ code: 'BAD_CREDENTIALS', message: 'Incorrect username or password' }), { status: 401 })
    })
    renderWithProviders(<Login />, '/login')
    await userEvent.type(await screen.findByLabelText('Username or email'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Incorrect username or password')).toBeInTheDocument()
  })

  it('sends the CSRF token header on login', async () => {
    const calls: RequestInit[] = []
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      if (String(input).endsWith('/api/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=abc123'
        return new Response(null, { status: 204 })
      }
      calls.push(init ?? {})
      return new Response(JSON.stringify({ code: 'BAD_CREDENTIALS', message: 'nope' }), { status: 401 })
    })
    renderWithProviders(<Login />, '/login')
    await userEvent.type(await screen.findByLabelText('Username or email'), 'bob')
    await userEvent.type(screen.getByLabelText('Password'), 'something')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(calls.length).toBe(1))
    expect((calls[0].headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('abc123')
    expect(calls[0].credentials).toBe('include')
  })

  it('enforces username and password rules on registration', async () => {
    renderWithProviders(<Register />, '/register')
    await userEvent.type(await screen.findByLabelText('Username'), 'bad name!')
    await userEvent.type(screen.getByLabelText('Password'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('Letters, numbers and underscores only')).toBeInTheDocument()
    expect(screen.getByText('Use at least 10 characters')).toBeInTheDocument()
  })
})
