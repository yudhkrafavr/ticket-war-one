import { apiClient } from './client'
import type { WaitingRoomResponse } from '../types/api'

export const waitingRoomApi = {
  get: async () => (await apiClient.get<WaitingRoomResponse>('/api/war/participants/me/waiting-room')).data,
  enter: async () => (await apiClient.post<WaitingRoomResponse>('/api/war/participants/me/enter-waiting-room')).data,
}
