import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { getApiErrorMessage } from '../../api/client'
import { ErrorState, LoadingState } from '../../components/ui/Feedback'
import { useAdminAuth } from '../../contexts/AdminAuthContext'

export function AdminLoginPage() {
  const navigate = useNavigate()
  const { isAuthenticated, isChecking, signIn } = useAdminAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [isPending, setIsPending] = useState(false)

  if (isChecking) return <LoadingState label="Checking admin sign-in…" />
  if (isAuthenticated) return <Navigate to="/admin" replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsPending(true)
    try {
      await signIn(username, password)
      navigate('/admin', { replace: true })
    } catch (loginError) {
      setError(loginError)
    } finally {
      setIsPending(false)
    }
  }

  return (
    <main className="admin-login-shell">
      <section className="admin-login-card">
        <div className="brand-mark">🎟</div>
        <h1>Ticket War Admin</h1>
        <p>Sign in to manage events and competitions.</p>
        {error && <ErrorState message={getApiErrorMessage(error)} />}
        <form onSubmit={submit}>
          <div className="field"><label htmlFor="admin-username">Username</label><input id="admin-username" autoComplete="username" required value={username} onChange={(event) => setUsername(event.target.value)} /></div>
          <div className="field"><label htmlFor="admin-password">Password</label><input id="admin-password" type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} /></div>
          <button className="button" type="submit" disabled={isPending}>{isPending ? 'Signing in…' : 'Sign in'}</button>
        </form>
      </section>
    </main>
  )
}
