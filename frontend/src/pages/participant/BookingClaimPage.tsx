import { useEffect, useRef, useState } from 'react'
import { bookingApi } from '../../api/bookingApi'
import { getApiErrorMessage } from '../../api/client'
import { ErrorState, LoadingState } from '../../components/ui/Feedback'
import type { BookingStatusResponse } from '../../types/api'

export function BookingClaimPage() {
  const started = useRef(false)
  const [booking, setBooking] = useState<BookingStatusResponse | null>(null)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    if (started.current) return
    started.current = true

    const token = new URLSearchParams(window.location.hash.slice(1)).get('token')
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`)
    if (!token) {
      setErrorMessage('This claim link is missing its token. Open the latest ticket invitation email.')
      return
    }

    bookingApi.claimFromEmail(token)
      .then(setBooking)
      .catch((error: unknown) => setErrorMessage(getApiErrorMessage(error)))
  }, [])

  return <section className="participant-card result-card">
    <div className="eyebrow">Ticket invitation</div>
    {!booking && !errorMessage && <><h1>Checking your invitation…</h1><LoadingState label="Claiming your ticket securely…" /></>}
    {errorMessage && <><h1>Your ticket could not be claimed.</h1><ErrorState message={errorMessage} /></>}
    {booking?.status === 'CLAIMED' && <><h1>Your ticket is confirmed!</h1><p>{booking.message}</p><div className="notice notice-info">Batch {booking.batchNumber} · Ticket {booking.ticketNumber}</div></>}
    {booking?.status === 'EXPIRED' && <><h1>This invitation has expired.</h1><div className="notice notice-warning">The ticket claim window is over.</div></>}
  </section>
}
