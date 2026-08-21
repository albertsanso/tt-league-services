import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import DashboardLayout from './layouts/DashboardLayout.jsx'

const OverviewPage = lazy(() => import('./pages/OverviewPage.jsx'))
const ClubsSearchPage = lazy(() => import('./pages/ClubsSearchPage.jsx'))
const PlayersSearchPage = lazy(() => import('./pages/PlayersSearchPage.jsx'))
const MatchesSearchPage = lazy(() => import('./pages/MatchesSearchPage.jsx'))
const SearchResultsPage = lazy(() => import('./pages/SearchResultsPage.jsx'))
const SettingsPage = lazy(() => import('./pages/SettingsPage.jsx'))

function RouteLoader() {
  return (
    <div className="route-loader" role="status" aria-live="polite">
      Carregant contingut...
    </div>
  )
}

function App() {
  return (
    <Suspense fallback={<RouteLoader />}>
      <Routes>
        <Route element={<DashboardLayout />}>
          <Route index element={<OverviewPage />} />
          <Route path="clubs" element={<ClubsSearchPage />} />
          <Route path="jugadors" element={<PlayersSearchPage />} />
          <Route path="partits" element={<MatchesSearchPage />} />
          <Route path="cerca" element={<SearchResultsPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </Suspense>
  )
}

export default App
