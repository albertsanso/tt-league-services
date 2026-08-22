import { ChevronLeft, X } from 'lucide-react'
import { useLocation } from 'react-router-dom'
import { navigationSections } from '../../config/navigation.js'
import { getRouteMeta } from '../../config/routes.js'
import { useAppState } from '../../context/useAppState.js'
import { useAuth } from '../../context/useAuth.js'
import SidebarFooter from './SidebarFooter.jsx'
import SidebarItem from './SidebarItem.jsx'
import SidebarSectionLabel from './SidebarSectionLabel.jsx'

function isRouteActive(pathname, itemPath) {
  if (itemPath === '/') {
    return pathname === '/'
  }

  return pathname.startsWith(itemPath)
}

function Sidebar({ variant }) {
  const location = useLocation()
  const {
    isDesktop,
    isMobile,
    isMobileDrawerOpen,
    isSidebarCollapsed,
    closeMobileDrawer,
    toggleSidebar,
  } = useAppState()
  const { hasPermission } = useAuth()
  const isMobileVariant = variant === 'mobile'
  const collapsed = isMobileVariant ? false : isSidebarCollapsed

  if (isMobileVariant && !isMobile) {
    return null
  }

  const className = [
    'sidebar',
    isMobileVariant ? 'sidebar-mobile' : 'sidebar-desktop',
    collapsed ? 'is-collapsed' : '',
    isMobileVariant && isMobileDrawerOpen ? 'is-open' : '',
  ]
    .join(' ')
    .trim()

  return (
    <aside
      className={className}
      aria-label="Barra lateral"
      id={isMobileVariant ? 'mobile-sidebar' : 'desktop-sidebar'}
    >
      <div className="sidebar-header">
        <div className="sidebar-brand">
          <h1 className="sidebar-brand-title">
            <span>TT</span>
            {collapsed ? null : <span className="sidebar-brand-light">League</span>}
          </h1>
          {collapsed ? null : <span className="sidebar-brand-line" />}
        </div>

        {isMobileVariant ? (
          <button
            type="button"
            className="sidebar-control"
            aria-label="Tancar menú"
            onClick={closeMobileDrawer}
          >
            <X size={18} strokeWidth={1.5} />
          </button>
        ) : null}

        {!isMobileVariant && isDesktop ? (
          <button
            type="button"
            className="sidebar-control"
            aria-label={collapsed ? 'Expandir barra lateral' : 'Col·lapsar barra lateral'}
            onClick={toggleSidebar}
          >
            <ChevronLeft
              size={18}
              strokeWidth={1.5}
              style={{ transform: collapsed ? 'rotate(180deg)' : 'none' }}
            />
          </button>
        ) : null}
      </div>

      <nav className="sidebar-nav" aria-label="Navegació principal">
        {navigationSections.map((section) => (
          <div key={section.id} className="sidebar-group">
            <SidebarSectionLabel collapsed={collapsed} label={section.label} />
            {section.items.filter((item) => (
              item.disabled
              || !getRouteMeta(item.path).permission
              || hasPermission(getRouteMeta(item.path).permission)
            )).map((item) => (
              <SidebarItem
                key={item.id}
                item={item}
                collapsed={collapsed}
                isActive={isRouteActive(location.pathname, item.path)}
                onSelect={isMobileVariant ? closeMobileDrawer : undefined}
              />
            ))}
          </div>
        ))}
      </nav>

      <SidebarFooter collapsed={collapsed} />
    </aside>
  )
}

export default Sidebar
