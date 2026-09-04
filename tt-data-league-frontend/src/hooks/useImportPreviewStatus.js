import { useCallback, useEffect, useState } from 'react'
import { getImportPreviewStatus } from '../api/importJobs.js'
import { useAuth } from '../context/useAuth.js'

const PREVIEW_STATUSES = new Set(['loading', 'success', 'empty-result', 'failure'])

function emptyPreview(importResourceId = '') {
  return { importResourceId, data: null, loading: false, error: null }
}

export function normalizeImportPreview(payload) {
  const preview = payload?.response ?? payload
  if (!preview || typeof preview !== 'object') {
    throw new Error('La resposta de la previsualització no és vàlida.')
  }

  const status = String(preview.status ?? 'failure').toLowerCase()
  return {
    importResourceId: preview.importResourceId ?? preview.id ?? null,
    source: preview.source ?? null,
    season: preview.season ?? null,
    resourceType: preview.resourceType ?? null,
    status: PREVIEW_STATUSES.has(status) ? status : 'failure',
    validationFindings: Array.isArray(preview.validationFindings) ? preview.validationFindings : [],
    processingErrors: Array.isArray(preview.processingErrors) ? preview.processingErrors : [],
    filesSeen: Number(preview.filesSeen ?? 0),
    itemsDispatched: Number(preview.itemsDispatched ?? 0),
    skipped: Number(preview.skipped ?? 0),
    processorFailures: Number(preview.processorFailures ?? 0),
  }
}

export function useImportPreviewStatus(importResourceId, refreshKey = 0) {
  const { token, clearSession } = useAuth()
  const [retryKey, setRetryKey] = useState(0)
  const [state, setState] = useState(() => emptyPreview())

  useEffect(() => {
    if (!importResourceId) {
      return undefined
    }

    const controller = new AbortController()
    let active = true

    getImportPreviewStatus(token, importResourceId, controller.signal, clearSession)
      .then((payload) => {
        if (active) setState({ importResourceId, data: normalizeImportPreview(payload), loading: false, error: null })
      })
      .catch((error) => {
        if (active && error.name !== 'AbortError') setState({ importResourceId, data: null, loading: false, error })
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [clearSession, importResourceId, refreshKey, retryKey, token])

  const retry = useCallback(() => {
    setState((current) => ({ ...current, importResourceId, data: null, loading: true, error: null }))
    setRetryKey((value) => value + 1)
  }, [importResourceId])

  return {
    ...state,
    loading: Boolean(importResourceId) && (state.importResourceId !== importResourceId || state.loading),
    error: state.importResourceId === importResourceId ? state.error : null,
    data: state.importResourceId === importResourceId ? state.data : null,
    retry,
  }
}
