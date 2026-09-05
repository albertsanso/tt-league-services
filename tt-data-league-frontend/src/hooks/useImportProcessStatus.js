import { useEffect, useRef, useState } from 'react'
import { getImportRunStatus } from '../api/importJobs.js'
import { useAuth } from '../context/useAuth.js'
import { normalizeImportProcess } from './useImportProcessResult.js'

const ACTIVE_STATUSES = new Set(['queued', 'running'])
const RUN_STATUSES = new Set(['queued', 'running', 'success', 'empty-result', 'failure'])
const POLL_INTERVAL_MS = 2000

function toNullableNumber(value) {
  return value === null || value === undefined ? null : Number(value)
}

function emptyRunStatus() {
  return { runId: null, data: null, loading: false, error: null }
}

export function normalizeImportRunStatus(payload) {
  const status = payload?.response ?? payload
  if (!status || typeof status !== 'object') {
    throw new Error('La resposta de l’estat de la importació no és vàlida.')
  }

  const normalizedStatus = String(status.status ?? 'failure').toLowerCase()
  return {
    runId: status.runId ?? null,
    importResourceId: status.importResourceId ?? null,
    source: status.source ?? null,
    season: status.season ?? null,
    status: RUN_STATUSES.has(normalizedStatus) ? normalizedStatus : 'failure',
    processed: Number(status.processed ?? 0),
    total: toNullableNumber(status.total),
    percentage: toNullableNumber(status.percentage),
    skipped: Number(status.skipped ?? 0),
    errorCount: Number(status.errorCount ?? 0),
    errorDetail: status.errorDetail ?? null,
    result: status.result ? normalizeImportProcess(status.result) : null,
  }
}

export function isActiveImportRunStatus(status) {
  return ACTIVE_STATUSES.has(status)
}

/**
 * Polls the status of one asynchronous import run while it is queued or running, and stops
 * (without ever restarting the import) once it reaches a terminal state or `runId` becomes falsy.
 * Stale responses from a superseded `runId` are ignored, and the poll timer is always cleared on
 * unmount or `runId` change.
 */
export function useImportProcessStatus(runId) {
  const { token, clearSession } = useAuth()
  const [state, setState] = useState(emptyRunStatus)
  const runIdRef = useRef(runId)

  useEffect(() => {
    runIdRef.current = runId
    if (!runId) {
      return undefined
    }

    const controller = new AbortController()
    let active = true
    let timer

    const poll = async () => {
      try {
        const payload = await getImportRunStatus(token, runId, controller.signal, clearSession)
        if (!active || runIdRef.current !== runId) return
        const normalized = normalizeImportRunStatus(payload)
        setState({ runId, data: normalized, loading: false, error: null })
        if (isActiveImportRunStatus(normalized.status)) {
          timer = window.setTimeout(poll, POLL_INTERVAL_MS)
        }
      } catch (error) {
        if (!active || error.name === 'AbortError' || runIdRef.current !== runId) return
        setState({ runId, data: null, loading: false, error })
      }
    }

    poll()

    return () => {
      active = false
      controller.abort()
      if (timer) window.clearTimeout(timer)
    }
  }, [clearSession, runId, token])

  return {
    ...state,
    loading: Boolean(runId) && (state.runId !== runId || state.loading),
    error: state.runId === runId ? state.error : null,
    data: state.runId === runId ? state.data : null,
  }
}
