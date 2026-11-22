import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { apiClient, setAdminAuthorizationHeader } from '../api/client'

interface AdminAuthValue {
  isAuthenticated: boolean
  isChecking: boolean
  signIn: (username: string, password: string) => Promise<void>
  signOut: () => void
}

const AdminAuthContext = createContext<AdminAuthValue | null>(null)
const ADMIN_AUTH_STORAGE_KEY = 'ticket-war.admin-authorization'

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [authorization, setAuthorization] = useState<string | null>(null)
  const [isChecking, setIsChecking] = useState(true)

  const signOut = useCallback(() => {
    window.sessionStorage.removeItem(ADMIN_AUTH_STORAGE_KEY)
    setAdminAuthorizationHeader(null)
    setAuthorization(null)
    setIsChecking(false)
  }, [])

  useEffect(() => {
    const onExpired = () => signOut()
    window.addEventListener('ticket-war:admin-auth-expired', onExpired)
    return () => window.removeEventListener('ticket-war:admin-auth-expired', onExpired)
  }, [signOut])

  useEffect(() => {
    const savedAuthorization = window.sessionStorage.getItem(ADMIN_AUTH_STORAGE_KEY)
    if (!savedAuthorization) {
      setIsChecking(false)
      return
    }

    let active = true
    setAdminAuthorizationHeader(savedAuthorization)
    apiClient.get('/api/admin/auth/session')
      .then(() => {
        if (active) setAuthorization(savedAuthorization)
      })
      .catch(() => {
        if (active) signOut()
      })
      .finally(() => {
        if (active) setIsChecking(false)
      })

    return () => { active = false }
  }, [signOut])

  const signIn = useCallback(async (username: string, password: string) => {
    const bytes = new TextEncoder().encode(`${username.trim()}:${password}`)
    const encoded = btoa(Array.from(bytes, (byte) => String.fromCharCode(byte)).join(''))
    const nextAuthorization = `Basic ${encoded}`
    await apiClient.get('/api/admin/auth/session', { headers: { Authorization: nextAuthorization } })
    window.sessionStorage.setItem(ADMIN_AUTH_STORAGE_KEY, nextAuthorization)
    setAdminAuthorizationHeader(nextAuthorization)
    setAuthorization(nextAuthorization)
    setIsChecking(false)
  }, [])

  const value = useMemo(() => ({
    isAuthenticated: Boolean(authorization),
    isChecking,
    signIn,
    signOut,
  }), [authorization, isChecking, signIn, signOut])

  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>
}

export function useAdminAuth() {
  const value = useContext(AdminAuthContext)
  if (!value) throw new Error('useAdminAuth must be used within AdminAuthProvider')
  return value
}
