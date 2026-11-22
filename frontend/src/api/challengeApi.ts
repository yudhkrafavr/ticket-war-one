import { apiClient } from './client'
import type { ChallengeResponse, ChallengeStartResponse, ChallengeSubmissionResponse, PrepareChallengeResponse } from '../types/api'

export const challengeApi = {
  prepare: async () => (await apiClient.post<PrepareChallengeResponse>('/api/war/challenges/prepare')).data,
  start: async () => (await apiClient.post<ChallengeStartResponse>('/api/war/challenges/start')).data,
  get: async (attemptId: string) => (await apiClient.get<ChallengeResponse>(`/api/war/challenges/${attemptId}`)).data,
  submit: async (attemptId: string, answers: { attemptQuestionId: string; selectedOptionId: string }[]) =>
    (await apiClient.post<ChallengeSubmissionResponse>(`/api/war/challenges/${attemptId}/submit`, { answers })).data,
}
