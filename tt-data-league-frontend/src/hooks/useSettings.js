import { useCallback, useEffect, useState } from 'react'
import { getSettings, normalizeSetting } from '../api/settings.js'
import { useAuth } from '../context/useAuth.js'

export function useSettings(filters = {}, refreshKey = 0) {
  const { token, clearSession } = useAuth()
  const { category = '', search = '' } = filters
  const [retryKey, setRetryKey] = useState(0)
  const [state, setState] = useState({ data: [], error: null, key: null })
  const identity = `${category}-${search}-${refreshKey}-${retryKey}`

  useEffect(() => {
    const controller = new AbortController()
    getSettings({ category, search }, token, controller.signal, clearSession)
      .then((data) => setState({
        data: Array.isArray(data) ? data.map(normalizeSetting) : [],
        error: null,
        key: identity,
      }))
      .catch((error) => {
        if (error.name !== 'AbortError') setState({ data: [], error, key: identity })
      })
    return () => controller.abort()
  }, [category, clearSession, identity, search, token])

  const retry = useCallback(() => setRetryKey((value) => value + 1), [])
  const current = state.key === identity
  return { data: current ? state.data : [], loading: !current, error: current ? state.error : null, retry }
}
