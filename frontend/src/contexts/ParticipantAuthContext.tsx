import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { apiClient, participantTokenStorageKey } from '../api/client'
import type { ParticipantProfileResponse } from '../types/api'

interface ParticipantAuthValue {
  token: string | null
  profile: ParticipantProfileResponse | null
  isValidating: boolean
  setToken: (token: string) => void
  clearToken: () => void
}

const ParticipantAuthContext = createContext<ParticipantAuthValue | null>(null)

export function ParticipantAuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenValue] = useState<string | null>(() => window.sessionStorage.getItem(participantTokenStorageKey))
  const [profile, setProfile] = useState<ParticipantProfileResponse | null>(null)
  const [isValidating, setIsValidating] = useState(Boolean(token))

  const clearToken = useCallback(() => {
    window.sessionStorage.removeItem(participantTokenStorageKey)
    setTokenValue(null)
    setProfile(null)
    setIsValidating(false)
  }, [])

  const setToken = useCallback((nextToken: string) => {
    window.sessionStorage.setItem(participantTokenStorageKey, nextToken)
    setTokenValue(nextToken)
    setProfile(null)
    setIsValidating(true)
  }, [])

  useEffect(() => {
    const onExpired = () => clearToken()
    window.addEventListener('ticket-war:participant-auth-expired', onExpired)
    return () => window.removeEventListener('ticket-war:participant-auth-expired', onExpired)
  }, [clearToken])

  useEffect(() => {
    if (!token) {
      setIsValidating(false)
      setProfile(null)
      return
    }

    let active = true
    setIsValidating(true)
    apiClient.get<ParticipantProfileResponse>('/api/war/participants/me')
      .then(({ data }) => {
        if (active) setProfile(data)
      })
      .catch((error: unknown) => {
        const status = (error as { response?: { status?: number } })?.response?.status
        if (status === 401 && active) clearToken()
      })
      .finally(() => {
        if (active) setIsValidating(false)
      })

    return () => { active = false }
  }, [token, clearToken])

  const value = useMemo(
    () => ({ token, profile, isValidating, setToken, clearToken }),
    [token, profile, isValidating, setToken, clearToken],
  )

  return <ParticipantAuthContext.Provider value={value}>{children}</ParticipantAuthContext.Provider>
}

export function useParticipantAuth() {
  const value = useContext(ParticipantAuthContext)
  if (!value) throw new Error('useParticipantAuth must be used within ParticipantAuthProvider')
  return value
}
