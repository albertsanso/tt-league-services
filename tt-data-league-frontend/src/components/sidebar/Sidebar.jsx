import { ChevronLeft, X } from 'lucide-react'
import { useEffect, useState } from 'react'
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
  const { hasPermission, hasRole } = useAuth()
  const { t } = useTranslation()
  const [expandedItems, setExpandedItems] = useState({})
  const isMobileVariant = variant === 'mobile'
  const collapsed = isMobileVariant ? false : isSidebarCollapsed

  useEffect(() => {
    navigationSections
      .flatMap((section) => section.items)
      .filter((item) => item.children && isRouteActive(location.pathname, item.path))
      .forEach((item) => {
        setExpandedItems((current) => ({ ...current, [item.id]: true }))
      })
  }, [location.pathname])

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
        {navigationSections.map((section) => {
          const visibleItems = section.items.filter((item) => (
            (item.disabled || !item.role || hasRole(item.role))
              && (item.disabled
                || !getRouteMeta(item.path).permission
                || hasPermission(getRouteMeta(item.path).permission))
          ))

          if (!visibleItems.length) return null

          return (
            <div key={section.id} className="sidebar-group">
              <SidebarSectionLabel collapsed={collapsed} label={t(section.labelKey)} />
              {visibleItems.map((item) => (
              <div key={item.id}>
                <SidebarItem
                  item={{
                    ...item,
                    label: t(item.labelKey),
                    ariaLabel: item.ariaLabelKey ? t(item.ariaLabelKey) : undefined,
                    badge: item.badgeKey ? t(item.badgeKey) : item.badge,
                  }}
                  collapsed={collapsed}
                  isActive={isRouteActive(location.pathname, item.path)}
                  onSelect={isMobileVariant ? closeMobileDrawer : undefined}
                  expanded={expandedItems[item.id] ?? false}
                  onToggle={() => setExpandedItems((current) => ({
                    ...current,
                    [item.id]: !current[item.id],
                  }))}
                />
                {item.children && expandedItems[item.id] ? (
                  <div className="sidebar-subitems">
                    {item.children.map((child) => (
                      <SidebarItem
                        key={child.id}
                        item={{ ...child, label: t(child.labelKey) }}
                        collapsed={collapsed}
                        isActive={isRouteActive(location.pathname, child.path)}
                        onSelect={isMobileVariant ? closeMobileDrawer : undefined}
                      />
                    ))}
                  </div>
                ) : null}
              </div>
              ))}
            </div>
          )
        })}
      </nav>

      <SidebarFooter collapsed={collapsed} />
    </aside>
  )
}

export default Sidebar
