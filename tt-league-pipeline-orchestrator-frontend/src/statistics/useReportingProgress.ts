import { useEffect, useState } from 'react'
import type { PipelineSource, ReportingProgressResponse } from '../api/types'
import { useApi } from '../api/useApi'

export interface ReportingProgressResult {
  readonly data: ReportingProgressResponse | null
  readonly loading: boolean
  readonly error: string | null
}

interface Settled {
  readonly key: string
  readonly data: ReportingProgressResponse | null
  readonly error: string | null
}

/**
 * Reporting progress needs exactly one source, so it is loaded separately from the other panels. Without a source or a
 * season nothing is requested.
 */
export function useReportingProgress(
  source: PipelineSource | null,
  season: string | null,
  competition: string | null,
): ReportingProgressResult {
  const api = useApi()
  const key = source === null || season === null ? null : JSON.stringify([source, season, competition])
  const [settled, setSettled] = useState<Settled | null>(null)

  useEffect(() => {
    if (key === null || source === null || season === null) {
      return undefined
    }
    const controller = new AbortController()
    api.statistics
      .getReportingProgress({ source, season, competition }, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setSettled({ key, data, error: null })
        }
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          const message = failure instanceof Error ? failure.message : 'Failed to load the reporting progress'
          setSettled((previous) => ({ key, data: previous?.data ?? null, error: message }))
        }
      })
    return () => controller.abort()
  }, [api, key, source, season, competition])

  return {
    data: settled?.data ?? null,
    loading: key !== null && settled?.key !== key,
    error: key !== null && settled?.key === key ? settled.error : null,
  }
}
