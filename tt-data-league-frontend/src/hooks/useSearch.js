import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { searchGlobal } from '../api/search.js'
import { useAuth } from '../context/useAuth.js'

function useRequest(request, enabled, identity) {
  const { token, clearSession } = useAuth()
  const [state, setState] = useState({ data: null, error: null, key: null })
  const requestKey = useMemo(() => `${identity}-${enabled}`, [enabled, identity])
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

  const current = enabled && state.key === requestKey
  return {
    data: enabled ? state.data : null,
    loading: enabled && !current,
    error: current ? state.error : null,
  }
}

export function useGlobalSearch(query) {
  const [debouncedQuery, setDebouncedQuery] = useState('')

  useEffect(() => {
    const timeoutId = window.setTimeout(() => setDebouncedQuery(query.trim()), 300)
    return () => window.clearTimeout(timeoutId)
  }, [query])

  const request = useCallback(
    (token, signal, onUnauthorized) => searchGlobal(debouncedQuery, token, signal, onUnauthorized),
    [debouncedQuery],
  )
  return useRequest(request, debouncedQuery.length >= 2, debouncedQuery)
}
