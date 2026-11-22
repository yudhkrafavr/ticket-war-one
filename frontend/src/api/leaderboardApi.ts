import { apiClient } from './client'
import type { AdminBatchLeaderboardDetailResponse, AdminLeaderboardResponse } from '../types/api'

export const leaderboardApi = {
  get: async (sessionId: string) =>
    (await apiClient.get<AdminLeaderboardResponse>(`/api/war-sessions/${sessionId}/leaderboard`)).data,
  getBatch: async (sessionId: string, batchId: string) =>
    (await apiClient.get<AdminBatchLeaderboardDetailResponse>(
      `/api/war-sessions/${sessionId}/batches/${batchId}/leaderboard`,
    )).data,
}
