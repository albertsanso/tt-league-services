import { ChevronDown } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { useAppState } from '../../context/useAppState.js'
import { useAuth } from '../../context/useAuth.js'

function UserDropdown() {
  const {
    isUserDropdownOpen,
    toggleUserDropdown,
    closeUserDropdown,
    acknowledgeNotifications,
  } = useAppState()
  const { user, logout } = useAuth()
  const rootRef = useRef(null)
  const menuRef = useRef(null)

  useEffect(() => {
    if (!isUserDropdownOpen) {
      return undefined
    }

    function onPointerDown(event) {
      if (rootRef.current && !rootRef.current.contains(event.target)) {
        closeUserDropdown()
      }
    }

    window.addEventListener('mousedown', onPointerDown)
    window.addEventListener('touchstart', onPointerDown)
    return () => {
      window.removeEventListener('mousedown', onPointerDown)
      window.removeEventListener('touchstart', onPointerDown)
    }
  }, [isUserDropdownOpen, closeUserDropdown])

  useEffect(() => {
    const menuNode = menuRef.current

    if (!isUserDropdownOpen || !menuNode) {
      return undefined
    }

    const focusable = menuNode.querySelectorAll('button')
    focusable[0]?.focus()

    function onMenuKeyDown(event) {
      if (event.key === 'Escape') {
        event.preventDefault()
        closeUserDropdown()
        return
      }

      if (event.key !== 'Tab' || focusable.length < 2) {
        return
      }

      const first = focusable[0]
      const last = focusable[focusable.length - 1]

      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      }

      if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    menuNode.addEventListener('keydown', onMenuKeyDown)
    return () => {
      menuNode.removeEventListener('keydown', onMenuKeyDown)
    }
  }, [isUserDropdownOpen, closeUserDropdown])

  return (
    <div className="user-dropdown" ref={rootRef}>
      <button
        type="button"
        className="user-trigger"
        aria-expanded={isUserDropdownOpen}
        aria-haspopup="menu"
        aria-controls="user-menu"
        onClick={toggleUserDropdown}
      >
        <span className="user-avatar" aria-hidden="true">
          {(user?.username ?? 'U').slice(0, 2).toUpperCase()}
        </span>
        <span className="user-name">{user?.username ?? 'Usuari'}</span>
        <ChevronDown size={16} strokeWidth={1.5} aria-hidden="true" />
      </button>

      {isUserDropdownOpen ? (
        <div id="user-menu" className="user-menu" role="menu" ref={menuRef}>
          <div className="user-menu-header">
            <p className="user-menu-title">{user?.username ?? 'Usuari'}</p>
            <p className="user-menu-subtitle">{user?.email ?? ''}</p>
          </div>

          <button type="button" className="user-menu-item" role="menuitem">
            El meu perfil
          </button>
          <button type="button" className="user-menu-item" role="menuitem">
            Preferències
          </button>
          <div className="user-menu-divider" />
          <button
            type="button"
            className="user-menu-item logout"
            role="menuitem"
            onClick={async () => {
              acknowledgeNotifications()
              closeUserDropdown()
              await logout()
            }}
          >
            Tancar sessió
          </button>
        </div>
      ) : null}
    </div>
  )
}

export default UserDropdown
