import { Bell } from 'lucide-react'
import { useAppState } from '../../context/useAppState.js'
import { useTranslation } from 'react-i18next'

function NotificationBell() {
  const { notificationCount, acknowledgeNotifications } = useAppState()
  const { t } = useTranslation()
  const hasNotifications = notificationCount > 0
  const label = hasNotifications
    ? t('notification.pending', { count: notificationCount })
    : t('notification.none')

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
