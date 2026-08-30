import { Menu } from 'lucide-react'
import { useAppState } from '../../context/useAppState.js'
import Breadcrumb from './Breadcrumb.jsx'
import NotificationBell from './NotificationBell.jsx'
import UserDropdown from './UserDropdown.jsx'
import { useTranslation } from 'react-i18next'

function TopBar() {
  const { isMobile, toggleSidebar, isMobileDrawerOpen } = useAppState()
  const { t } = useTranslation()

  return (
    <header className="topbar">
      <div className="topbar-left">
        <button
          type="button"
          className="icon-button"
          aria-label={
            isMobile && isMobileDrawerOpen
              ? t('navigation.closeMain')
              : isMobile
                ? t('navigation.openMain')
                : t('navigation.toggle')
          }
          aria-expanded={isMobile ? isMobileDrawerOpen : undefined}
          aria-controls={isMobile ? 'mobile-sidebar' : undefined}
          onClick={toggleSidebar}
        >
          <Menu size={20} strokeWidth={1.5} aria-hidden="true" />
        </button>
        <Breadcrumb />
      </div>

      <div className="topbar-right">
        <NotificationBell />
        <UserDropdown />
      </div>
    </header>
  )
}

export default TopBar
