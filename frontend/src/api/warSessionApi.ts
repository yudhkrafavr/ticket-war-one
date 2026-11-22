import { apiClient } from './client'
import type { WarSessionRequest, WarSessionResponse, WarSessionStatus } from '../types/api'

export const warSessionApi = {
  list: async (status?: WarSessionStatus) =>
    (await apiClient.get<WarSessionResponse[]>('/api/war-sessions', { params: status ? { status } : undefined })).data,
  get: async (id: string) => (await apiClient.get<WarSessionResponse>(`/api/war-sessions/${id}`)).data,
  create: async (request: WarSessionRequest) =>
    (await apiClient.post<WarSessionResponse>('/api/war-sessions', request)).data,
  update: async (id: string, request: WarSessionRequest) =>
    (await apiClient.put<WarSessionResponse>(`/api/war-sessions/${id}`, request)).data,
  publish: async (id: string) => (await apiClient.post<WarSessionResponse>(`/api/war-sessions/${id}/publish`)).data,
}
