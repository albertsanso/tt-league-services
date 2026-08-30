import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from '../components/sidebar/Sidebar.jsx'
import TopBar from '../components/topbar/TopBar.jsx'
import { useAppState } from '../context/useAppState.js'
import { useTranslation } from 'react-i18next'

function DashboardLayout() {
  const location = useLocation()
  const { closeMobileDrawer, closeUserDropdown, isMobile, isMobileDrawerOpen } =
    useAppState()
  const { t } = useTranslation()

  useEffect(() => {
    closeMobileDrawer()
    closeUserDropdown()
  }, [location.pathname, location.search, closeMobileDrawer, closeUserDropdown])

  useEffect(() => {
    if (!isMobile) {
      document.body.style.overflow = ''
      return undefined
    }

    document.body.style.overflow = isMobileDrawerOpen ? 'hidden' : ''

    return () => {
      document.body.style.overflow = ''
    }
  }, [isMobile, isMobileDrawerOpen])

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        {t('shell.skip')}
      </a>
      <Sidebar variant="desktop" />
      <Sidebar variant="mobile" />

      <div className="app-panel">
        <TopBar />
        <main id="main-content" className="app-content" tabIndex={-1}>
          <div className="app-content-inner">
            <Outlet />
          </div>
        </main>
        <footer className="app-footer">
          <p>{t('shell.footer')}</p>
        </footer>
      </div>

      {isMobileDrawerOpen ? (
        <button
          type="button"
          aria-label={t('shell.closeNavigation')}
          className="mobile-backdrop"
          onClick={closeMobileDrawer}
        />
      ) : null}
    </div>
  )
}

export default DashboardLayout
