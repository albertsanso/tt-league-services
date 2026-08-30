import { useCallback, useEffect, useState } from 'react'
import { getUsers, getRoles } from '../api/users.js'
import { useAuth } from '../context/useAuth.js'

function useRequest(request, enabled, requestIdentity) {
  const { token, clearSession } = useAuth()
  const [state, setState] = useState({ data: null, error: null, key: null })
  const [retryKey, setRetryKey] = useState(0)
  const requestKey = `${requestIdentity}-${enabled}-${retryKey}`

  useEffect(() => {
    if (!enabled) return undefined

    const controller = new AbortController()

    Promise.resolve()
      .then(() => request(token, controller.signal, clearSession))
      .then((data) => {
        if (!controller.signal.aborted) {
          setState({ data, error: null, key: requestKey })
        }
      })
      .catch((error) => {
        if (error.name !== 'AbortError' && !controller.signal.aborted) {
          setState({ data: null, error, key: requestKey })
        }
      })

    return () => controller.abort()
  }, [clearSession, enabled, request, requestKey, token])

  const retry = useCallback(() => setRetryKey((k) => k + 1), [])
  const isCurrentRequest = enabled && state.key === requestKey
  return {
    data: isCurrentRequest ? state.data : null,
    loading: enabled && !isCurrentRequest,
    error: isCurrentRequest ? state.error : null,
    retry,
  }
}

/**
 * Paginated / filtered user list hook.
 *
 * @param {{ search?: string, active?: boolean|null, page?: number, size?: number, refreshKey?: number }} filter
 */
export function useUsers(filter = {}) {
  const {
    search = '',
    active = null,
    page = 0,
    size = 20,
    refreshKey = 0,
  } = filter
  const identity = `users-${search}-${active}-${page}-${size}-${refreshKey}`

  const request = useCallback(
    (token, signal, onUnauthorized) => getUsers({ search: search || null, active, page, size },
      token, signal, onUnauthorized),
    [search, active, page, size],
  )

  return useRequest(request, true, identity)
}

/**
 * Role catalog hook. Fetches the fixed role/permission catalog once.
 */
export function useRoleCatalog() {
  const request = useCallback(
    (token, signal, onUnauthorized) => getRoles(token, signal, onUnauthorized),
    [],
  )

  return useRequest(request, true, 'role-catalog')
}
