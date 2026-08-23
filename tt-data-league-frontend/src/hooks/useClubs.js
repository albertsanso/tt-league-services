import { useCallback, useEffect, useState } from 'react'
import {
  getClubCompetitionDetails,
  getClubDetails,
  searchClubs,
} from '../api/clubs.js'
import { useAuth } from '../context/useAuth.js'

function useRequest(request, enabled, requestIdentity) {
  const { token, clearSession } = useAuth()
  const [state, setState] = useState({
    data: null,
    error: null,
    key: null,
  })
  const [retryKey, setRetryKey] = useState(0)
  const requestKey = `${requestIdentity}-${enabled}-${retryKey}`

  useEffect(() => {
    if (!enabled) {
      return undefined
    }

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

  const retry = useCallback(() => setRetryKey((current) => current + 1), [])
  const isCurrentRequest = enabled && state.key === requestKey
  return {
    data: isCurrentRequest ? state.data : null,
    loading: enabled && !isCurrentRequest,
    error: isCurrentRequest ? state.error : null,
    retry,
  }
}

export function useClubSearch(query) {
  const normalizedQuery = query.trim()
  const request = useCallback(
    (token, signal, onUnauthorized) => searchClubs(
      normalizedQuery,
      token,
      signal,
      onUnauthorized,
    ),
    [normalizedQuery],
  )

  return useRequest(request, normalizedQuery.length >= 2, normalizedQuery)
}

export function useClubDetails(clubId) {
  const request = useCallback(
    (token, signal, onUnauthorized) => getClubDetails(
      clubId,
      token,
      signal,
      onUnauthorized,
    ),
    [clubId],
  )

  return useRequest(request, Boolean(clubId), clubId)
}

export function useClubCompetitionDetails(clubId, season, competition) {
  const request = useCallback(
    (token, signal, onUnauthorized) => getClubCompetitionDetails(
      clubId,
      season,
      competition,
      token,
      signal,
      onUnauthorized,
    ),
    [clubId, competition, season],
  )

  const enabled = Boolean(clubId && season && competition)
  return useRequest(request, enabled, `${clubId}-${season}-${competition}`)
}
