import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react'
import { appStateContext } from './appStateContext.js'

const MOBILE_BREAKPOINT = 768
const DESKTOP_BREAKPOINT = 1280

function getViewport() {
  if (typeof window === 'undefined') {
    return 'desktop'
  }

  if (window.innerWidth < MOBILE_BREAKPOINT) {
    return 'mobile'
  }

  if (window.innerWidth < DESKTOP_BREAKPOINT) {
    return 'tablet'
  }

  return 'desktop'
}

export function AppStateProvider({ children }) {
  const [viewport, setViewport] = useState(getViewport)
  const [isSidebarCollapsed, setSidebarCollapsed] = useState(
    () => getViewport() === 'tablet',
  )
  const [isMobileDrawerOpen, setMobileDrawerOpen] = useState(false)
  const [isUserDropdownOpen, setUserDropdownOpen] = useState(false)
  const [notificationCount, setNotificationCount] = useState(3)

  const isMobile = viewport === 'mobile'
  const isDesktop = viewport === 'desktop'

  useEffect(() => {
    function onResize() {
      const nextViewport = getViewport()
      setViewport(nextViewport)

      if (nextViewport === 'tablet') {
        setSidebarCollapsed(true)
      }

      if (nextViewport === 'desktop') {
        setSidebarCollapsed(false)
      }

      if (nextViewport !== 'mobile') {
        setMobileDrawerOpen(false)
      }
    }

    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  const toggleSidebar = useCallback(() => {
    if (viewport === 'mobile') {
      setMobileDrawerOpen((current) => !current)
      return
    }

    if (viewport === 'desktop') {
      setSidebarCollapsed((current) => !current)
    }
  }, [viewport])

  const openMobileDrawer = useCallback(() => {
    setMobileDrawerOpen(true)
  }, [])

  const closeMobileDrawer = useCallback(() => {
    setMobileDrawerOpen(false)
  }, [])

  const toggleUserDropdown = useCallback(() => {
    setUserDropdownOpen((current) => !current)
  }, [])

  const closeUserDropdown = useCallback(() => {
    setUserDropdownOpen(false)
  }, [])

  const acknowledgeNotifications = useCallback(() => {
    setNotificationCount(0)
  }, [])

  useEffect(() => {
    function onEscape(event) {
      if (event.key !== 'Escape') {
        return
      }

      setMobileDrawerOpen(false)
      setUserDropdownOpen(false)
    }

    window.addEventListener('keydown', onEscape)
    return () => window.removeEventListener('keydown', onEscape)
  }, [])

  const value = useMemo(
    () => ({
      viewport,
      isMobile,
      isDesktop,
      isSidebarCollapsed,
      isMobileDrawerOpen,
      isUserDropdownOpen,
      notificationCount,
      toggleSidebar,
      openMobileDrawer,
      closeMobileDrawer,
      toggleUserDropdown,
      closeUserDropdown,
      acknowledgeNotifications,
    }),
    [
      viewport,
      isMobile,
      isDesktop,
      isSidebarCollapsed,
      isMobileDrawerOpen,
      isUserDropdownOpen,
      notificationCount,
      toggleSidebar,
      openMobileDrawer,
      closeMobileDrawer,
      toggleUserDropdown,
      closeUserDropdown,
      acknowledgeNotifications,
    ],
  )

  return <appStateContext.Provider value={value}>{children}</appStateContext.Provider>
}
