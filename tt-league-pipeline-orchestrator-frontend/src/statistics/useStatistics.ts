import { useCallback, useEffect, useState } from 'react'
import type {
  CorrectionsResponse,
  DailyStatsResponse,
  PendingResponse,
  PipelineSource,
  RunOutcomesResponse,
  SourceHealthResponse,
  TimeToReportResponse,
} from '../api/types'
import { useApi } from '../api/useApi'

export interface StatisticsQuery {
  readonly sources: readonly PipelineSource[]
  /** Needed by the time-to-report panel; without it that panel is skipped and pending counts every season. */
  readonly season: string | null
  readonly from: string
  readonly to: string
}

export interface StatisticsData {
  readonly daily: DailyStatsResponse
  readonly runs: RunOutcomesResponse
  readonly timeToReport: TimeToReportResponse | null
  readonly pending: PendingResponse
  readonly corrections: CorrectionsResponse
  readonly sourceHealth: SourceHealthResponse
}

export interface StatisticsResult {
  readonly data: StatisticsData | null
  readonly loading: boolean
  readonly error: string | null
  readonly reload: () => void
}

interface Settled {
  readonly key: string
  readonly data: StatisticsData | null
  readonly error: string | null
}

/**
 * Loads every statistics endpoint in parallel with one abort controller; a filter change aborts the requests in flight
 * and reloads. A `null` query sends nothing. The previous data stays visible while a reload is in flight. There is no
 * event subscription: the page has a refresh button.
 */
export function useStatistics(query: StatisticsQuery | null): StatisticsResult {
  const api = useApi()
  const key = query === null ? null : JSON.stringify(query)
  const [reloads, setReloads] = useState(0)
  const [settled, setSettled] = useState<Settled | null>(null)

  useEffect(() => {
    if (key === null) {
      return undefined
    }
    const request = JSON.parse(key) as StatisticsQuery
    const controller = new AbortController()
    const range = { from: request.from, to: request.to, source: request.sources }
    const requestKey = `${key}#${reloads}`
    Promise.all([
      api.statistics.getDailyStats(range, controller.signal),
      api.statistics.getRunOutcomes(range, controller.signal),
      request.season === null
        ? Promise.resolve(null)
        : api.statistics.getTimeToReport({ season: request.season, source: request.sources }, controller.signal),
      api.statistics.getPendingByAge(
        { season: request.season ?? undefined, source: request.sources },
        controller.signal,
      ),
      api.statistics.getCorrections(range, controller.signal),
      api.statistics.getSourceHealth(range, controller.signal),
    ])
      .then(([daily, runs, timeToReport, pending, corrections, sourceHealth]) => {
        if (!controller.signal.aborted) {
          setSettled({ key: requestKey, data: { daily, runs, timeToReport, pending, corrections, sourceHealth }, error: null })
        }
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        controller.abort()
        const message = failure instanceof Error ? failure.message : 'Failed to load the statistics'
        setSettled((previous) => ({ key: requestKey, data: previous?.data ?? null, error: message }))
      })
    return () => controller.abort()
  }, [api, key, reloads])

  const reload = useCallback(() => setReloads((count) => count + 1), [])
  const current = key === null ? null : `${key}#${reloads}`
  return {
    data: settled?.data ?? null,
    loading: current !== null && settled?.key !== current,
    error: current !== null && settled?.key === current ? settled.error : null,
    reload,
  }
}
