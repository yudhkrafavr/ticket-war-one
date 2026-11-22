import { AlertCircle, LoaderCircle } from 'lucide-react'
import type { ReactNode } from 'react'

export function LoadingState({ label = 'Loading data…' }: { label?: string }) {
  return <div className="state-card" role="status"><LoaderCircle className="spin" size={18} />{label}</div>
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state-card state-error" role="alert">
      <AlertCircle size={19} />
      <span>{message}</span>
      {onRetry && <button className="button button-secondary button-small" onClick={onRetry}>Try again</button>}
    </div>
  )
}

export function EmptyState({ title, detail }: { title: string; detail?: string }) {
  return <div className="empty-state"><strong>{title}</strong>{detail && <span>{detail}</span>}</div>
}

export function StatusBadge({ status }: { status: string | null | undefined }) {
  const normalized = status?.toLowerCase().replaceAll('_', '-') ?? 'unknown'
  return <span className={`status-badge status-${normalized}`}>{status?.replaceAll('_', ' ') ?? 'Unknown'}</span>
}

export function PageHeader({
  eyebrow,
  title,
  detail,
  action,
}: {
  eyebrow?: string
  title: string
  detail?: string
  action?: ReactNode
}) {
  return (
    <div className="page-header">
      <div>{eyebrow && <div className="eyebrow">{eyebrow}</div>}<h1>{title}</h1>{detail && <p>{detail}</p>}</div>
      {action && <div className="page-header-action">{action}</div>}
    </div>
  )
}
