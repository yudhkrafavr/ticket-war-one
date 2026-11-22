import { apiClient } from './client'
import type { BookingStatusResponse } from '../types/api'

export const bookingApi = {
  getMyStatus: async () =>
    (await apiClient.get<BookingStatusResponse>('/api/bookings/me')).data,
  claim: async () =>
    (await apiClient.post<BookingStatusResponse>('/api/bookings/me/claim')).data,
  claimFromEmail: async (token: string) =>
    (await apiClient.post<BookingStatusResponse>('/api/bookings/me/claim-invitation', { token })).data,
}
