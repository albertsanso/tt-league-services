import { useCallback, useEffect, useRef, useState } from 'react'
import type { RunListQuery } from '../api/runs'
import type { Page, RunSummary } from '../api/types'
import { useApi } from '../api/useApi'
import { useRunEvents } from '../events/useRunEvents'
import { mergeRunEvent, mergeStepSummary, upsertUnit } from './format'

const REFETCH_DEBOUNCE_MS = 300

export interface RunListResult {
  readonly page: Page<RunSummary> | null
  readonly loading: boolean
  readonly error: string | null
  /** A run that is not on this (later) page was created or changed; going to the first page shows it. */
  readonly newRunsAvailable: boolean
  readonly refetch: () => void
}

interface Settled {
  readonly key: string | null
  readonly page: Page<RunSummary> | null
  readonly error: string | null
}

/**
 * Loads one page of runs and keeps it current from the event stream. A `null` query sends no request and keeps the
 * last result on screen. The previous page stays visible while a refetch is in flight.
 */
export function useRunList(query: RunListQuery | null): RunListResult {
  const api = useApi()
  const key = query === null ? null : JSON.stringify(query)
  const [settled, setSettled] = useState<Settled>({ key: null, page: null, error: null })
  const [refreshing, setRefreshing] = useState(false)
  const [newRunsAvailable, setNewRunsAvailable] = useState(false)

  const queryRef = useRef<{ key: string; query: RunListQuery } | null>(null)
  const pageRef = useRef<Page<RunSummary> | null>(null)
  const inflightRef = useRef<AbortController | null>(null)
  const staleRef = useRef(false)
  const timerRef = useRef<number | null>(null)

  useEffect(() => {
    pageRef.current = settled.page
  }, [settled.page])

  const start = useCallback(
    (requestKey: string, requestQuery: RunListQuery) => {
      inflightRef.current?.abort()
      const controller = new AbortController()
      inflightRef.current = controller
      void (async () => {
        try {
          for (;;) {
            staleRef.current = false
            const page = await api.runs.listRuns(requestQuery, controller.signal)
            if (controller.signal.aborted) {
              return
            }
            if (staleRef.current) {
              // An event arrived while this request was in flight: its answer may predate it.
              continue
            }
            pageRef.current = page
            setSettled({ key: requestKey, page, error: null })
            setNewRunsAvailable(false)
            break
          }
        } catch (failure: unknown) {
          if (controller.signal.aborted) {
            return
          }
          const message = failure instanceof Error ? failure.message : 'Failed to load runs'
          setSettled((previous) => ({ ...previous, key: requestKey, error: message }))
        }
        if (inflightRef.current === controller) {
          inflightRef.current = null
          setRefreshing(false)
        }
      })()
    },
    [api],
  )

  /** Refetches the current query now, or after the request already in flight settles. */
  const requestLoad = useCallback(() => {
    const current = queryRef.current
    if (current === null) {
      return
    }
    if (inflightRef.current !== null) {
      staleRef.current = true
      return
    }
    setRefreshing(true)
    start(current.key, current.query)
  }, [start])

  useEffect(() => {
    if (key === null || query === null) {
      queryRef.current = null
      return undefined
    }
    queryRef.current = { key, query }
    start(key, query)
    return () => {
      inflightRef.current?.abort()
      inflightRef.current = null
    }
    // `query` is represented by `key`.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, start])

  useEffect(
    () => () => {
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current)
      }
    },
    [],
  )

  const markStale = () => {
    if (inflightRef.current !== null) {
      staleRef.current = true
    }
  }

  useRunEvents((event) => {
    if (queryRef.current === null) {
      return
    }
    if (event.type === 'reconnected') {
      requestLoad()
      return
    }
    if (event.type === 'step') {
      const step = event.payload
      if (pageRef.current?.items.some((row) => row.id === step.runId)) {
        setSettled((previous) => patchRow(previous, step.runId, (row) => ({ ...row, steps: mergeStepSummary(row.steps, step) })))
        markStale()
      }
      return
    }
    if (event.type === 'unit') {
      const unit = event.payload
      if (pageRef.current?.items.some((row) => row.id === unit.runId)) {
        setSettled((previous) => patchRow(previous, unit.runId, (row) => ({ ...row, units: upsertUnit(row.units, unit) })))
        markStale()
      }
      return
    }
    if (event.type === 'run') {
      const changed = event.payload
      if (pageRef.current?.items.some((row) => row.id === changed.id)) {
        setSettled((previous) => patchRow(previous, changed.id, (row) => mergeRunEvent(row, changed)))
        markStale()
      } else if ((queryRef.current.query.page ?? 0) === 0) {
        if (timerRef.current !== null) {
          window.clearTimeout(timerRef.current)
        }
        timerRef.current = window.setTimeout(() => {
          timerRef.current = null
          requestLoad()
        }, REFETCH_DEBOUNCE_MS)
      } else {
        setNewRunsAvailable(true)
      }
    }
  })

  return {
    page: settled.page,
    loading: key !== null && (settled.key !== key || refreshing),
    error: settled.error,
    newRunsAvailable,
    refetch: requestLoad,
  }
}

function patchRow(previous: Settled, id: string, patch: (row: RunSummary) => RunSummary): Settled {
  if (previous.page === null) {
    return previous
  }
  return {
    ...previous,
    page: { ...previous.page, items: previous.page.items.map((row) => (row.id === id ? patch(row) : row)) },
  }
}
