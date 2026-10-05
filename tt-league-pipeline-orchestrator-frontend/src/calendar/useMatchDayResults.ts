import { useCallback, useEffect, useState } from 'react'
import type { MatchDayResults } from '../api/types'
import { useApi } from '../api/useApi'

export interface MatchDayResultsResult {
  readonly results: MatchDayResults | null
  readonly loading: boolean
  /** The read from the platform failed; the page keeps working without results. */
  readonly error: string | null
  readonly retry: () => void
}

interface Settled {
  readonly key: string
  readonly results: MatchDayResults | null
  readonly error: string | null
}

/**
 * Reads the results of the match day from the platform, apart from the detail so that a platform failure never blocks
 * the page. It reads again when the number of reported matches changes and on `retry`; nothing is retried
 * automatically.
 */
export function useMatchDayResults(matchDayId: string, reportedMatches: number | null): MatchDayResultsResult {
  const api = useApi()
  const [attempt, setAttempt] = useState(0)
  const [settled, setSettled] = useState<Settled | null>(null)
  const enabled = reportedMatches !== null
  const key = JSON.stringify([matchDayId, reportedMatches, attempt])

  useEffect(() => {
    if (!enabled) {
      return undefined
    }
    const controller = new AbortController()
    api.matchDays
      .getMatchDayResults(matchDayId, controller.signal)
      .then((results) => {
        if (!controller.signal.aborted) {
          setSettled({ key, results, error: null })
        }
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setSettled({
            key,
            results: null,
            error: failure instanceof Error ? failure.message : 'Failed to read the results',
          })
        }
      })
    return () => controller.abort()
  }, [api, matchDayId, enabled, key])

  const retry = useCallback(() => setAttempt((value) => value + 1), [])
  const current = settled?.key === key
  return {
    results: settled?.results ?? null,
    loading: enabled && !current,
    error: current ? settled.error : null,
    retry,
  }
}
