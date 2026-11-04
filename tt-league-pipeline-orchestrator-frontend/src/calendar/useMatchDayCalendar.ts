import { useCallback, useEffect, useRef, useState } from 'react'
import type { MatchDaySummary } from '../api/types'
import { useApi } from '../api/useApi'
import { useRunEvents } from '../events/useRunEvents'
import { toRangeQuery, toUndatedQuery } from './calendarFilters'
import type { CalendarFilterValues } from './calendarFilters'

/** Ten pages of 200 match days; more than that asks the user to narrow the filters. */
export const MAX_PAGES = 10
export const LIVE_DEBOUNCE_MS = 500

export interface MatchDayCalendarResult {
  /** Dated match days of the visible period, in API order. */
  readonly days: readonly MatchDaySummary[]
  readonly undated: readonly MatchDaySummary[]
  readonly loading: boolean
  readonly error: string | null
  /** More match days exist than were loaded. */
  readonly truncated: boolean
  readonly refetch: () => void
}

interface Settled {
  readonly key: string | null
  readonly days: readonly MatchDaySummary[]
  readonly undated: readonly MatchDaySummary[]
  readonly truncated: boolean
  readonly error: string | null
}

const EMPTY: Settled = { key: null, days: [], undated: [], truncated: false, error: null }

function keyOf(filters: CalendarFilterValues): string {
  return JSON.stringify([
    filters.view,
    filters.date,
    filters.source,
    filters.season,
    filters.competition,
    filters.phase,
    filters.state,
  ])
}

/**
 * Loads every page of the visible period (sequentially, up to {@link MAX_PAGES}) and the undated match days under
 * the same filters, and keeps them current: a `match-days` event for the filtered source and season refetches once,
 * debounced; a reconnect refetches at once; an event during a request triggers one follow-up request. The previous
 * result stays on screen while a refetch runs, and a filter change aborts the request in flight.
 */
export function useMatchDayCalendar(filters: CalendarFilterValues): MatchDayCalendarResult {
  const api = useApi()
  const key = keyOf(filters)
  const [settled, setSettled] = useState<Settled>(EMPTY)
  const [refreshing, setRefreshing] = useState(false)

  const currentRef = useRef<{ key: string; filters: CalendarFilterValues } | null>(null)
  const inflightRef = useRef<AbortController | null>(null)
  const staleRef = useRef(false)
  const timerRef = useRef<number | null>(null)

  const start = useCallback(
    (requestKey: string, requestFilters: CalendarFilterValues) => {
      inflightRef.current?.abort()
      const controller = new AbortController()
      inflightRef.current = controller
      void (async () => {
        try {
          for (;;) {
            staleRef.current = false
            const days: MatchDaySummary[] = []
            let truncated = false
            for (let page = 0; ; page += 1) {
              const result = await api.matchDays.listMatchDays(toRangeQuery(requestFilters, page), controller.signal)
              days.push(...result.items)
              if (page + 1 >= result.totalPages) {
                break
              }
              if (page + 1 >= MAX_PAGES) {
                truncated = true
                break
              }
            }
            const undatedPage = await api.matchDays.listMatchDays(toUndatedQuery(requestFilters), controller.signal)
            if (undatedPage.totalItems > undatedPage.items.length) {
              truncated = true
            }
            if (controller.signal.aborted) {
              return
            }
            if (staleRef.current) {
              // An event arrived while this request was in flight: its answer may predate it.
              continue
            }
            setSettled({ key: requestKey, days, undated: undatedPage.items, truncated, error: null })
            break
          }
        } catch (failure: unknown) {
          if (controller.signal.aborted) {
            return
          }
          const message = failure instanceof Error ? failure.message : 'Failed to load the match days'
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

  /** Refetches now, or once more after the request already in flight settles. */
  const requestLoad = useCallback(() => {
    const current = currentRef.current
    if (current === null) {
      return
    }
    if (inflightRef.current !== null) {
      staleRef.current = true
      return
    }
    setRefreshing(true)
    start(current.key, current.filters)
  }, [start])

  useEffect(() => {
    currentRef.current = { key, filters }
    start(key, filters)
    return () => {
      inflightRef.current?.abort()
      inflightRef.current = null
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
    // `filters` is represented by `key`.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, start])

  useRunEvents((event) => {
    const current = currentRef.current
    if (current === null) {
      return
    }
    if (event.type === 'reconnected') {
      requestLoad()
      return
    }
    if (event.type !== 'match-days') {
      return
    }
    const { source, season } = current.filters
    if ((source !== null && source !== event.payload.source) || (season !== null && season !== event.payload.season)) {
      return
    }
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current)
    }
    timerRef.current = window.setTimeout(() => {
      timerRef.current = null
      requestLoad()
    }, LIVE_DEBOUNCE_MS)
  })

  const own = settled.key === key
  return {
    days: settled.days,
    undated: settled.undated,
    loading: !own || refreshing,
    error: own ? settled.error : null,
    truncated: settled.truncated,
    refetch: requestLoad,
  }
}
