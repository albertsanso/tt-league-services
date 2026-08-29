import { useCallback, useEffect, useState } from 'react'
import { getPlayerDetails, searchPlayers } from '../api/players.js'
import { useAuth } from '../context/useAuth.js'

function useRequest(request, enabled, identity) {
  const { token, clearSession } = useAuth()
  const [state, setState] = useState({ data: null, error: null, key: null })
  const [retryKey, setRetryKey] = useState(0)
  const requestKey = `${identity}-${retryKey}`

  useEffect(() => {
    if (!enabled) return undefined
    const controller = new AbortController()
    Promise.resolve().then(() => request(token, controller.signal, clearSession))
      .then((data) => {
        if (!controller.signal.aborted) setState({ data, error: null, key: requestKey })
      })
      .catch((error) => {
        if (error.name !== 'AbortError' && !controller.signal.aborted) {
          setState({ data: null, error, key: requestKey })
        }
      })
    return () => controller.abort()
  }, [clearSession, enabled, request, requestKey, token])

  const retry = useCallback(() => setRetryKey((current) => current + 1), [])
  const current = enabled && state.key === requestKey
  return {
    data: current ? state.data : null,
    loading: enabled && !current,
    error: current ? state.error : null,
    retry,
  }
}

export function usePlayerSearch(query, source) {
  const normalizedQuery = query.trim()
  const request = useCallback(
    (token, signal, onUnauthorized) => searchPlayers(normalizedQuery, source, token, signal, onUnauthorized),
    [normalizedQuery, source],
  )
  return useRequest(request, normalizedQuery.length >= 2, `${normalizedQuery}-${source ?? ''}`)
}

export function usePlayerDetails(playerId) {
  const request = useCallback(
    (token, signal, onUnauthorized) => getPlayerDetails(playerId, token, signal, onUnauthorized),
    [playerId],
  )
  return useRequest(request, Boolean(playerId), playerId)
}
