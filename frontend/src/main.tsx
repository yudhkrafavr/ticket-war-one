import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { ParticipantAuthProvider } from './contexts/ParticipantAuthContext'
import { AdminAuthProvider } from './contexts/AdminAuthContext'
import { App } from './App'
import './styles.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 15_000, refetchOnWindowFocus: false, retry: 1 },
    mutations: { retry: false },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ParticipantAuthProvider>
        <AdminAuthProvider><BrowserRouter><App /></BrowserRouter></AdminAuthProvider>
      </ParticipantAuthProvider>
    </QueryClientProvider>
  </React.StrictMode>,
)
