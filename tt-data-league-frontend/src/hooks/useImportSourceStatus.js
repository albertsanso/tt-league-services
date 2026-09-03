import { useCallback, useEffect, useState } from 'react'
import { getImportStatus } from '../api/importJobs.js'
import { useAuth } from '../context/useAuth.js'

export const IMPORT_SOURCE_IDS = ['RFETM', 'BCNESA', 'FCTT']

function sourceStatuses(status) {
  return IMPORT_SOURCE_IDS.map((id) => ({ id, label: id, status }))
}

export function normalizeImportStatus(payload) {
  const statusPayload = payload?.response ?? payload
  if (!statusPayload || !Array.isArray(statusPayload.sources)) {
    throw new Error('La resposta de l’estat d’importació no és vàlida.')
  }

  return IMPORT_SOURCE_IDS.map((id) => ({
    id,
    label: id,
    status: statusPayload.sources.some((source) => source?.sourceName === id)
      ? 'available'
      : 'unavailable',
  }))
}

export function useImportSourceStatus() {
  const { token, clearSession } = useAuth()
  const [retryKey, setRetryKey] = useState(0)
  const [state, setState] = useState({
    data: sourceStatuses('loading'),
    loading: true,
    error: null,
  })

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    let inFlight = false

    const refresh = async () => {
      if (!active || inFlight) return
      inFlight = true
      try {
        const payload = await getImportStatus(token, controller.signal, clearSession)
        if (active) {
          setState({
            data: normalizeImportStatus(payload),
            loading: false,
            error: null,
          })
        }
      } catch (error) {
        if (active && error.name !== 'AbortError') {
          setState((current) => ({
            data: current.loading ? sourceStatuses('error') : current.data,
            loading: false,
            error,
          }))
        }
      } finally {
        inFlight = false
      }
    }

    refresh()
    const timer = window.setInterval(refresh, 5000)

    return () => {
      active = false
      controller.abort()
      window.clearInterval(timer)
    }
  }, [clearSession, retryKey, token])

  const retry = useCallback(() => {
    setState({
      data: sourceStatuses('loading'),
      loading: true,
      error: null,
    })
    setRetryKey((value) => value + 1)
  }, [])

  const refresh = useCallback(() => {
    setState((current) => ({ ...current, loading: true, error: null }))
    setRetryKey((value) => value + 1)
  }, [])

  return { ...state, retry, refresh }
}
