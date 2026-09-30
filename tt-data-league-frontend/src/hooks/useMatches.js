import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { getCalendarRange, getMatchDetails, getSeasonCalendar } from '../api/matches.js'
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
    data: enabled ? state.data : null,
    loading: enabled && !current,
    error: current ? state.error : null,
    retry,
  }
}

export function useMatchSummary(matchId) {
  const request = useCallback(
    (token, signal, onUnauthorized) => getMatchDetails(matchId, token, signal, onUnauthorized),
    [matchId],
  )
  return useRequest(request, Boolean(matchId), matchId)
}

export function useSeasonCalendar(filters) {
  const source = filters?.source ?? ''
  const season = filters?.season ?? ''
  const competition = filters?.competition ?? ''
  const group = filters?.group ?? ''
  const round = filters?.round ?? ''
  const enabled = Boolean(source && season && competition)
  const identity = `${source}|${season}|${competition}|${group}|${round}`
  const request = useCallback(
    (token, signal, onUnauthorized) => getSeasonCalendar(
      { source, season, competition, group, round }, token, signal, onUnauthorized,
    ),
    [source, season, competition, group, round],
  )
  return useRequest(request, enabled, identity)
}

export function useCalendarRange(filters) {
  const source = filters?.source ?? ''
  const season = filters?.season ?? ''
  const from = filters?.from ?? ''
  const to = filters?.to ?? ''
  const competition = filters?.competition ?? ''
  const group = filters?.group ?? ''
  const team = filters?.team ?? ''
  const enabled = Boolean(source && season && from && to)
  const identity = `${source}|${season}|${from}|${to}|${competition}|${group}|${team}`
  const request = useCallback(
    (token, signal, onUnauthorized) => getCalendarRange(
      { source, season, from, to, competition, group, team }, token, signal, onUnauthorized,
    ),
    [source, season, from, to, competition, group, team],
  )
  return useRequest(request, enabled, identity)
}
