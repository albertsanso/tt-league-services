import { useCallback, useEffect, useState } from 'react'
import { getImportSources } from '../api/importJobs.js'
import { useAuth } from '../context/useAuth.js'

export function useImportSources(refreshKey = 0) {
  const { token, clearSession } = useAuth()
  const [retryKey, setRetryKey] = useState(0)
  const [state, setState] = useState({ data: [], error: null, key: null })
  const identity = `${refreshKey}-${retryKey}`

  useEffect(() => {
    const controller = new AbortController()
    getImportSources(token, controller.signal, clearSession)
      .then((data) => setState({
        data: Array.isArray(data) ? data : [],
        error: null,
        key: identity,
      }))
      .catch((error) => {
        if (error.name !== 'AbortError') setState({ data: [], error, key: identity })
      })
    return () => controller.abort()
  }, [clearSession, identity, token])

  const retry = useCallback(() => setRetryKey((v) => v + 1), [])
  const current = state.key === identity
  return { data: current ? state.data : [], loading: !current, error: current ? state.error : null, retry }
}
