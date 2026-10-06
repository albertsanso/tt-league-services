import { lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from './auth/RequireAuth'
import { RunEventsProvider } from './events/RunEventsProvider'
import AppLayout from './layout/AppLayout'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'

const CalendarPage = lazy(() => import('./pages/CalendarPage'))
const MatchDayDetailPage = lazy(() => import('./pages/MatchDayDetailPage'))
const PollingPage = lazy(() => import('./pages/PollingPage'))
const RunsPage = lazy(() => import('./pages/RunsPage'))
const RunDetailPage = lazy(() => import('./pages/RunDetailPage'))
const StatisticsPage = lazy(() => import('./pages/StatisticsPage'))

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <RunEventsProvider>
              <AppLayout />
            </RunEventsProvider>
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/runs" replace />} />
        <Route path="calendar" element={<CalendarPage />} />
        <Route path="calendar/match-days/:matchDayId" element={<MatchDayDetailPage />} />
        <Route path="runs" element={<RunsPage />} />
        <Route path="runs/:runId" element={<RunDetailPage />} />
        <Route path="polling" element={<PollingPage />} />
        <Route path="statistics" element={<StatisticsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
