import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/useAuth.js'

function GuardLoader() {
  return (
    <div className="route-loader" role="status" aria-live="polite">
      Comprovant la sessió...
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
  if (!authenticated || !permission || hasPermission(permission)) {
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
