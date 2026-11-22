import { apiClient } from './client'
import type { AdminParticipantResponse, ParticipantAccessValidationResponse, ParticipantProfileResponse, ParticipantResultResponse, RegisterParticipantResponse } from '../types/api'

export const participantApi = {
  register: async (sessionId: string, email: string) =>
    (await apiClient.post<RegisterParticipantResponse>(
      `/api/war-sessions/${sessionId}/participants/register`, { email },
    )).data,
  requestAccessLink: async (sessionId: string, email: string) =>
    (await apiClient.post<void>(
      `/api/war-sessions/${sessionId}/participants/access-link`, { email },
    )).data,
  profile: async () => (await apiClient.get<ParticipantProfileResponse>('/api/war/participants/me')).data,
  result: async () => (await apiClient.get<ParticipantResultResponse>('/api/war/participants/me/result')).data,
  validateAccess: async (accessToken: string) =>
    (await apiClient.post<ParticipantAccessValidationResponse>('/api/war/participants/validate-access', { accessToken })).data,
  adminRoster: async (sessionId: string) =>
    (await apiClient.get<AdminParticipantResponse[]>(`/api/war-sessions/${sessionId}/participants`)).data,
}
