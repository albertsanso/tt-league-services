import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { getPlayerDetails, searchPlayers } from '../api/players.js'
import { useAuth } from '../context/useAuth.js'

function useRequest(request, enabled, identity) {
  const { token, clearSession } = useAuth()
  const [state, setState] = useState({ data: null, error: null, key: null })
  const [retryKey, setRetryKey] = useState(0)
  const requestKey = useMemo(
    () => ({ enabled, identity, retryKey, token }),
    [enabled, identity, retryKey, token],
  )
  const requestRef = useRef(0)

  useEffect(() => {
    if (!enabled) return undefined
    const controller = new AbortController()
    const requestId = ++requestRef.current
    Promise.resolve().then(() => request(token, controller.signal, clearSession))
      .then((data) => {
        if (!controller.signal.aborted && requestRef.current === requestId) {
          setState({ data, error: null, key: requestKey })
        }
      })
      .catch((error) => {
        if (error.name !== 'AbortError' && !controller.signal.aborted && requestRef.current === requestId) {
          setState({ data: null, error, key: requestKey })
        }
      })
    return () => {
      controller.abort()
      if (requestRef.current === requestId) requestRef.current += 1
    }
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

export function usePlayerDetails(playerId, source, season, competition) {
  const request = useCallback(
    (token, signal, onUnauthorized) => getPlayerDetails(
      playerId, source, season, competition, token, signal, onUnauthorized,
    ),
    [competition, playerId, season, source],
  )
  return useRequest(request, Boolean(playerId), `${playerId}-${source ?? ''}-${season ?? ''}-${competition ?? ''}`)
}
