import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Toaster } from 'sonner'
import App from './App'
import { ApiError } from './lib/api'
import { useTheme } from './lib/theme'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Retry only when the server couldn't be reached (e.g. a sleeping free-tier instance),
      // with bounded exponential backoff. Real 4xx answers are not retried.
      retry: (count, err) => err instanceof ApiError && err.isNetwork && count < 5,
      retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 15_000),
      refetchOnWindowFocus: false,
    },
  },
  mutationCache: new MutationCache({
    onError: (err) => {
      // Session expired mid-action: send the user back to sign in.
      if (err instanceof ApiError && err.status === 401) {
        queryClient.setQueryData(['me'], null)
      }
    },
  }),
})

function ThemedToaster() {
  const resolved = useTheme((t) => t.resolved)
  return (
    <Toaster
      theme={resolved}
      position="bottom-right"
      toastOptions={{
        style: {
          background: 'var(--surface-1)',
          border: '2px solid var(--line)',
          boxShadow: '4px 4px 0 0 var(--shadow)',
          color: 'var(--text)',
          fontFamily: '"Public Sans", sans-serif',
          fontWeight: 600,
          borderRadius: '6px',
        },
      }}
    />
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
      <ThemedToaster />
    </QueryClientProvider>
  </StrictMode>,
)
