import { Menu } from 'lucide-react'
import { useAppState } from '../../context/useAppState.js'
import Breadcrumb from './Breadcrumb.jsx'
import NotificationBell from './NotificationBell.jsx'
import UserDropdown from './UserDropdown.jsx'

function TopBar() {
  const { isMobile, toggleSidebar, isMobileDrawerOpen } = useAppState()

  return (
    <header className="topbar">
      <div className="topbar-left">
        <button
          type="button"
          className="icon-button"
          aria-label={
            isMobile && isMobileDrawerOpen
              ? 'Tancar menú principal'
              : isMobile
                ? 'Obrir menú principal'
                : 'Alternar barra lateral'
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
