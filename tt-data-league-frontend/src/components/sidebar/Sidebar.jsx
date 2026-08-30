import { ChevronLeft, X } from 'lucide-react'
import { useLocation } from 'react-router-dom'
import { navigationSections } from '../../config/navigation.js'
import { getRouteMeta } from '../../config/routes.js'
import { useAppState } from '../../context/useAppState.js'
import { useAuth } from '../../context/useAuth.js'
import { useTranslation } from 'react-i18next'
import { isRouteActive } from './routeMatching.js'
import SidebarFooter from './SidebarFooter.jsx'
import SidebarItem from './SidebarItem.jsx'
import SidebarSectionLabel from './SidebarSectionLabel.jsx'

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
  const { t } = useTranslation()
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
      aria-label={t('navigation.sidebar')}
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
            aria-label={t('navigation.close')}
            onClick={closeMobileDrawer}
          >
            <X size={18} strokeWidth={1.5} />
          </button>
        ) : null}

        {!isMobileVariant && isDesktop ? (
          <button
            type="button"
            className="sidebar-control"
            aria-label={collapsed ? t('navigation.expand') : t('navigation.collapse')}
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

      <nav className="sidebar-nav" aria-label={t('navigation.main')}>
        {navigationSections.map((section) => (
          <div key={section.id} className="sidebar-group">
            <SidebarSectionLabel collapsed={collapsed} label={t(section.labelKey)} />
            {section.items.filter((item) => (
              item.disabled
              || !getRouteMeta(item.path).permission
              || hasPermission(getRouteMeta(item.path).permission)
            )).map((item) => (
              <SidebarItem
                key={item.id}
                item={{ ...item, label: t(item.labelKey), badge: item.badgeKey ? t(item.badgeKey) : item.badge }}
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
