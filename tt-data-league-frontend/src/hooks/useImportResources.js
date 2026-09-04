import { useCallback, useEffect, useState } from 'react'
import { getImportResourcesBySource } from '../api/importJobs.js'
import { useAuth } from '../context/useAuth.js'

function emptyState() {
  return { source: '', data: [], loading: false, error: null }
}

export function normalizeImportResources(payload) {
  const resourcePayload = payload?.response ?? payload
  const resources = Array.isArray(resourcePayload)
    ? resourcePayload
    : resourcePayload?.items ?? resourcePayload?.content

  if (!Array.isArray(resources)) throw new Error('La resposta dels recursos d’importació no és vàlida.')

  return resources.map((resource) => ({
    id: resource.importResourceId ?? resource.id,
    filename: resource.filename ?? resource.name ?? resource.resourceName ?? null,
    season: resource.season,
    status: resource.status,
    createdDate: resource.createdDate,
    lastProcessedDate: resource.lastProcessedDate,
    resourceType: resource.resourceType,
  }))
}

export function useImportResources(source) {
  const { token, clearSession } = useAuth()
  const [retryKey, setRetryKey] = useState(0)
  const [state, setState] = useState(emptyState)

  useEffect(() => {
    if (!source) {
      return undefined
    }

    const controller = new AbortController()
    let active = true

    getImportResourcesBySource(token, source, controller.signal, clearSession)
      .then((payload) => {
        if (active) setState({ source, data: normalizeImportResources(payload), loading: false, error: null })
      })
      .catch((error) => {
        if (active && error.name !== 'AbortError') setState({ source, data: [], loading: false, error })
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [clearSession, retryKey, source, token])

  const retry = useCallback(() => {
    setState((current) => ({ ...current, source, data: [], loading: true, error: null }))
    setRetryKey((value) => value + 1)
  }, [source])
  const refresh = useCallback(() => {
    setState((current) => ({ ...current, source, data: [], loading: true, error: null }))
    setRetryKey((value) => value + 1)
  }, [source])

  return {
    ...state,
    data: state.source === source ? state.data : [],
    loading: Boolean(source) && (state.source !== source || state.loading),
    error: state.source === source ? state.error : null,
    retry,
    refresh,
  }
}
