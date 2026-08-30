import { Navigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/useAuth.js'

function GuardLoader() {
  const { t } = useTranslation()
  return (
    <div className="route-loader" role="status" aria-live="polite">
      {t('shell.checkingSession')}
    </div>
  )
}

export function RequireAuth({ children }) {
  const { loading, authenticated } = useAuth()
  const location = useLocation()

  if (loading) {
    return <GuardLoader />
  }

  if (!authenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: `${location.pathname}${location.search}${location.hash}` }}
      />
    )
  }

  return children
}

export function RequirePermission({ permission, children }) {
  const { authenticated, hasPermission } = useAuth()
  const permissions = Array.isArray(permission) ? permission : [permission]
  if (!authenticated || !permission || permissions.every(hasPermission)) {
    return children
  }
  return <Navigate to="/forbidden" replace />
}

export function RequireRole({ role, children }) {
  const { authenticated, hasRole } = useAuth()
  if (authenticated && hasRole(role)) {
    return children
  }
  return <Navigate to="/forbidden" replace />
}

export function PublicOnly({ children }) {
  const { loading, authenticated } = useAuth()

  if (loading) {
    return <GuardLoader />
  }

  if (authenticated) {
    return <Navigate to="/" replace />
  }

  return children
}
