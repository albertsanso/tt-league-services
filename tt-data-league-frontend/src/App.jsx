import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import {
  PublicOnly,
  RequireAuth,
  RequirePermission,
  RequireRole,
} from './components/auth/RequireAuth.jsx'
import { getRouteMeta } from './config/routes.js'
import DashboardLayout from './layouts/DashboardLayout.jsx'

const OverviewPage = lazy(() => import('./pages/OverviewPage.jsx'))
const ClubsSearchPage = lazy(() => import('./pages/ClubsSearchPage.jsx'))
const ClubDetailPage = lazy(() => import('./pages/ClubDetailPage.jsx'))
const ClubCompetitionDetailPage = lazy(() => import('./pages/ClubCompetitionDetailPage.jsx'))
const ClubEditPage = lazy(() => import('./pages/ClubEditPage.jsx'))
const PlayersSearchPage = lazy(() => import('./pages/PlayersSearchPage.jsx'))
const PlayerDetailPage = lazy(() => import('./pages/PlayerDetailPage.jsx'))
const MatchesSearchPage = lazy(() => import('./pages/MatchesSearchPage.jsx'))
const SearchResultsPage = lazy(() => import('./pages/SearchResultsPage.jsx'))
const SettingsPage = lazy(() => import('./pages/SettingsPage.jsx'))
const LoginPage = lazy(() => import('./pages/LoginPage.jsx'))
const RegisterPage = lazy(() => import('./pages/RegisterPage.jsx'))
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage.jsx'))
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage.jsx'))
const ForbiddenPage = lazy(() => import('./pages/ForbiddenPage.jsx'))

function RouteLoader() {
  return (
    <div className="route-loader" role="status" aria-live="polite">
      Carregant contingut...
    </div>
  )
}

function ProtectedPage({ children }) {
  const location = useLocation()
  const { permission } = getRouteMeta(location.pathname)
  return <RequirePermission permission={permission}>{children}</RequirePermission>
}

function App() {
  return (
    <Suspense fallback={<RouteLoader />}>
      <Routes>
        <Route path="/login" element={<PublicOnly><LoginPage /></PublicOnly>} />
        <Route path="/register" element={<PublicOnly><RegisterPage /></PublicOnly>} />
        <Route path="/forgot-password" element={<PublicOnly><ForgotPasswordPage /></PublicOnly>} />
        <Route path="/reset-password" element={<PublicOnly><ResetPasswordPage /></PublicOnly>} />
        <Route path="/forbidden" element={<RequireAuth><ForbiddenPage /></RequireAuth>} />
        <Route element={<RequireAuth><DashboardLayout /></RequireAuth>}>
          <Route index element={<ProtectedPage><OverviewPage /></ProtectedPage>} />
          <Route path="clubs" element={<ProtectedPage><ClubsSearchPage /></ProtectedPage>} />
          <Route
            path="clubs/:clubId/edit"
            element={(
              <ProtectedPage>
                <RequireRole role="ADMIN">
                  <ClubEditPage />
                </RequireRole>
              </ProtectedPage>
            )}
          />
          <Route path="clubs/:clubId" element={<ProtectedPage><ClubDetailPage /></ProtectedPage>} />
          <Route
            path="clubs/:clubId/competition/:season/:competition"
            element={<ProtectedPage><ClubCompetitionDetailPage /></ProtectedPage>}
          />
          <Route path="jugadors" element={<ProtectedPage><PlayersSearchPage /></ProtectedPage>} />
          <Route path="jugadors/:playerId" element={<ProtectedPage><PlayerDetailPage /></ProtectedPage>} />
          <Route path="partits" element={<ProtectedPage><MatchesSearchPage /></ProtectedPage>} />
          <Route path="cerca" element={<ProtectedPage><SearchResultsPage /></ProtectedPage>} />
          <Route path="settings" element={<ProtectedPage><SettingsPage /></ProtectedPage>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </Suspense>
  )
}

export default App
