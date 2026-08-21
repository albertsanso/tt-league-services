import { Bell } from 'lucide-react'
import { useAppState } from '../../context/useAppState.js'

function NotificationBell() {
  const { notificationCount, acknowledgeNotifications } = useAppState()
  const hasNotifications = notificationCount > 0
  const label = hasNotifications
    ? `${notificationCount} notificacions pendents`
    : 'Sense notificacions pendents'

  return (
    <div className="notification-wrap">
      <button
        type="button"
        className="icon-button"
        aria-label={label}
        onClick={acknowledgeNotifications}
      >
        <Bell size={18} strokeWidth={1.5} aria-hidden="true" />
      </button>
      {hasNotifications ? <span className="notification-dot" aria-hidden="true" /> : null}
    </div>
  )
}

export default NotificationBell
