import { apiClient } from './client'
import type { WarBatchResponse } from '../types/api'

const collectionPath = (sessionId: string) => `/api/war-sessions/${sessionId}/batches`

export const batchApi = {
  list: async (sessionId: string) => (await apiClient.get<WarBatchResponse[]>(collectionPath(sessionId))).data,
}
