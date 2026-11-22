import { apiClient } from './client'
import type { CreateEventRequest, EventResponse, UpdateEventRequest } from '../types/api'

export const eventApi = {
  list: async () => (await apiClient.get<EventResponse[]>('/api/events')).data,
  get: async (id: string) => (await apiClient.get<EventResponse>(`/api/events/${id}`)).data,
  create: async (request: CreateEventRequest) =>
    (await apiClient.post<EventResponse>('/api/events', request)).data,
  update: async (id: string, request: UpdateEventRequest) =>
    (await apiClient.put<EventResponse>(`/api/events/${id}`, request)).data,
  publish: async (id: string) =>
    (await apiClient.patch<EventResponse>(`/api/events/${id}/publish`)).data,
  remove: async (id: string) =>
    (await apiClient.delete<void>(`/api/events/${id}`)).data,
}
