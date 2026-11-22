import { apiClient } from './client'
import type { TriviaQuestionRequest, TriviaQuestionResponse } from '../types/api'

const collectionPath = (sessionId: string) => `/api/war-sessions/${sessionId}/questions`

export const triviaApi = {
  list: async (sessionId: string) =>
    (await apiClient.get<TriviaQuestionResponse[]>(collectionPath(sessionId))).data,
  create: async (sessionId: string, request: TriviaQuestionRequest) =>
    (await apiClient.post<TriviaQuestionResponse>(collectionPath(sessionId), request)).data,
  update: async (sessionId: string, questionId: string, request: TriviaQuestionRequest) =>
    (await apiClient.put<TriviaQuestionResponse>(`${collectionPath(sessionId)}/${questionId}`, request)).data,
  delete: async (sessionId: string, questionId: string) =>
    apiClient.delete(`${collectionPath(sessionId)}/${questionId}`),
}
