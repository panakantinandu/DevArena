import { Link } from 'react-router'
import { Button } from '@/components/ui'

export default function NotFound() {
  return (
    <div className="py-24">
      <p className="eyebrow">// 404</p>
      <h1 className="mt-2 text-4xl font-semibold">Nothing at this address</h1>
      <p className="mt-2 text-muted">The page moved, or the link was mistyped.</p>
      <Link to="/" className="mt-6 inline-block"><Button variant="primary">Back to dashboard</Button></Link>
    </div>
  )
}
