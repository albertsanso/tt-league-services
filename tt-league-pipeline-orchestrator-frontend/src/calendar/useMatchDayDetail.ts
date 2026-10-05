import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../api/ApiError'
import type { MatchDayDetail } from '../api/types'
import { useApi } from '../api/useApi'
import { useRunEvents } from '../events/useRunEvents'
import { mergeRunEvent } from '../runs/format'

export interface MatchDayDetailResult {
  readonly detail: MatchDayDetail | null
  readonly loading: boolean
  readonly error: string | null
  readonly notFound: boolean
  readonly refetch: () => void
  /** Applies the detail an action answered with, without another request. */
  readonly replace: (detail: MatchDayDetail) => void
}

interface Settled {
  readonly id: string | null
  readonly detail: MatchDayDetail | null
  readonly error: string | null
  readonly notFound: boolean
}

/**
 * Loads one match day and keeps it current: a `match-days` event for this match day, or a recompute event for its
 * source and season, refetches; a `run` event for one of the runs that touched it updates that run; a reconnect
 * refetches. An event during a request triggers one follow-up request.
 */
export function useMatchDayDetail(matchDayId: string): MatchDayDetailResult {
  const api = useApi()
  const [settled, setSettled] = useState<Settled>({ id: null, detail: null, error: null, notFound: false })
  const [refreshing, setRefreshing] = useState(false)
  const inflightRef = useRef<AbortController | null>(null)
  const staleRef = useRef(false)
  const idRef = useRef(matchDayId)
  const detailRef = useRef<MatchDayDetail | null>(null)

  const start = useCallback(
    (id: string) => {
      inflightRef.current?.abort()
      const controller = new AbortController()
      inflightRef.current = controller
      void (async () => {
        try {
          for (;;) {
            staleRef.current = false
            const detail = await api.matchDays.getMatchDay(id, controller.signal)
            if (controller.signal.aborted) {
              return
            }
            if (staleRef.current) {
              continue
            }
            detailRef.current = detail
            setSettled({ id, detail, error: null, notFound: false })
            break
          }
        } catch (failure: unknown) {
          if (controller.signal.aborted) {
            return
          }
          if (failure instanceof ApiError && (failure.status === 404 || failure.status === 400)) {
            detailRef.current = null
            setSettled({ id, detail: null, error: null, notFound: true })
          } else {
            const message = failure instanceof Error ? failure.message : 'Failed to load the match day'
            setSettled((previous) => ({ ...previous, id, error: message, notFound: false }))
          }
        }
        if (inflightRef.current === controller) {
          inflightRef.current = null
          setRefreshing(false)
        }
      })()
    },
    [api],
  )

  const refetch = useCallback(() => {
    if (inflightRef.current !== null) {
      staleRef.current = true
      return
    }
    setRefreshing(true)
    start(idRef.current)
  }, [start])

  const replace = useCallback((detail: MatchDayDetail) => {
    detailRef.current = detail
    if (inflightRef.current !== null) {
      staleRef.current = true
    }
    setSettled({ id: idRef.current, detail, error: null, notFound: false })
  }, [])

  useEffect(() => {
    idRef.current = matchDayId
    detailRef.current = null
    start(matchDayId)
    return () => {
      inflightRef.current?.abort()
      inflightRef.current = null
    }
  }, [matchDayId, start])

  useRunEvents((event) => {
    if (event.type === 'reconnected') {
      refetch()
      return
    }
    const current = detailRef.current
    if (event.type === 'match-days') {
      const { matchDayId: changed, cause, source, season } = event.payload
      const sameDay = changed === idRef.current
      const recomputed =
        cause === 'RECOMPUTED' &&
        current !== null &&
        current.matchDay.source === source &&
        current.matchDay.season === season
      if (sameDay || recomputed) {
        refetch()
      }
      return
    }
    if (event.type === 'run' && current !== null && current.runs.some((run) => run.id === event.payload.id)) {
      const changed = event.payload
      const next = { ...current, runs: current.runs.map((run) => (run.id === changed.id ? mergeRunEvent(run, changed) : run)) }
      detailRef.current = next
      setSettled((previous) => (previous.detail === null ? previous : { ...previous, detail: next }))
      if (inflightRef.current !== null) {
        staleRef.current = true
      }
    }
  })

  const own = settled.id === matchDayId
  return {
    detail: own ? settled.detail : null,
    loading: !own || refreshing,
    error: own ? settled.error : null,
    notFound: own && settled.notFound,
    refetch,
    replace,
  }
}
