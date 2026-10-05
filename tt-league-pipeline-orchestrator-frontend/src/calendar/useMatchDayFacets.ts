import { useEffect, useState } from 'react'
import type { MatchDayFacets, PipelineSource } from '../api/types'
import { useApi } from '../api/useApi'

export interface MatchDayFacetsResult {
  readonly facets: MatchDayFacets | null
  readonly error: string | null
}

interface Settled {
  readonly key: string
  readonly facets: MatchDayFacets | null
  readonly error: string | null
}

/** The values the filter bar offers: seasons honour the source; categories and phases honour source and season. */
export function useMatchDayFacets(source: PipelineSource | null, season: string | null): MatchDayFacetsResult {
  const api = useApi()
  const key = JSON.stringify([source, season])
  const [settled, setSettled] = useState<Settled | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    api.matchDays
      .getMatchDayFacets({ source: source ?? undefined, season: season ?? undefined }, controller.signal)
      .then((facets) => {
        if (!controller.signal.aborted) {
          setSettled({ key, facets, error: null })
        }
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setSettled((previous) => ({
            key,
            facets: previous?.facets ?? null,
            error: failure instanceof Error ? failure.message : 'Failed to load the filter options',
          }))
        }
      })
    return () => controller.abort()
  }, [api, key, source, season])

  return { facets: settled?.facets ?? null, error: settled?.key === key ? settled.error : null }
}
