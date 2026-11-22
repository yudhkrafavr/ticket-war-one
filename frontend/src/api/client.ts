import axios from 'axios'

const PARTICIPANT_TOKEN_KEY = 'ticket-war.participant-access-token'
let adminAuthorizationHeader: string | null = null
const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL
const localGatewayUrl = 'http://localhost:8080'
const useLocalDevProxy = import.meta.env.DEV && (!configuredBaseUrl || configuredBaseUrl.replace(/\/$/, '') === localGatewayUrl)

function isParticipantRegistration(url: string, method: string | undefined) {
  return method?.toLowerCase() === 'post' && /^\/api\/war-sessions\/[^/]+\/participants\/register$/.test(url)
}

function isPublicScheduledSessionList(url: string, method?: string, params?: unknown) {
  if (url !== '/api/war-sessions' || method?.toLowerCase() !== 'get' || !params || typeof params !== 'object') {
    return false
  }
  const status = (params as Record<string, unknown>).status
  return typeof status === 'string' && status.toUpperCase() === 'SCHEDULED'
}

function isAdminApiRequest(url: string, method?: string, params?: unknown) {
  if (isParticipantRegistration(url, method)) return false
  if (isPublicScheduledSessionList(url, method, params)) return false
  if (url === '/api/admin/auth/session') return true

  const isWrite = ['post', 'put', 'patch', 'delete'].includes(method?.toLowerCase() ?? '')
  const isEventPath = url === '/api/events' || url.startsWith('/api/events/')
  if (isEventPath) return isWrite

  const isWarSessionPath = url === '/api/war-sessions' || url.startsWith('/api/war-sessions/')
  const isPublicSessionDetail = method?.toLowerCase() === 'get' && /^\/api\/war-sessions\/[^/]+$/.test(url)
  return isWarSessionPath && !isPublicSessionDetail
}

export function setAdminAuthorizationHeader(value: string | null) {
  adminAuthorizationHeader = value
}

export const apiClient = axios.create({
  baseURL: useLocalDevProxy ? '' : configuredBaseUrl || localGatewayUrl,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

apiClient.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = window.sessionStorage.getItem(PARTICIPANT_TOKEN_KEY)
    if (token) config.headers.set('X-Participant-Access-Token', token)

    const url = config.url?.split('?')[0] ?? ''
    if (isAdminApiRequest(url, config.method, config.params) && adminAuthorizationHeader) {
      config.headers.set('Authorization', adminAuthorizationHeader)
    }
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401 && typeof window !== 'undefined') {
      const url = error.config?.url?.split('?')[0] ?? ''
      if (isAdminApiRequest(url, error.config?.method, error.config?.params)) {
        setAdminAuthorizationHeader(null)
        window.dispatchEvent(new Event('ticket-war:admin-auth-expired'))
      } else if (url.startsWith('/api/war/') || url.startsWith('/api/bookings/')) {
        window.sessionStorage.removeItem(PARTICIPANT_TOKEN_KEY)
        window.dispatchEvent(new Event('ticket-war:participant-auth-expired'))
      }
    }
    return Promise.reject(error)
  },
)

export function getApiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    if (!error.response) return 'The API Gateway could not be reached. Check the connection and try again.'
    if (status === 500) return 'The server could not complete the request. Try again later.'
    if (status === 401) return 'Sign in again to continue.'
    if (status === 503) return 'Admin sign-in is not configured on the server.'
    if (status === 403) return 'You do not have permission to perform this action.'
    const message = (error.response.data as { message?: unknown } | undefined)?.message
    if (typeof message === 'string' && message.trim()) return message
    if (status === 404) return 'The requested API endpoint or resource was not found.'
    if (status === 409) return 'This action conflicts with the current session state.'
    if (status === 400) return 'Some of the submitted information is invalid.'
  }
  return 'Something went wrong. Please try again.'
}

export const participantTokenStorageKey = PARTICIPANT_TOKEN_KEY
