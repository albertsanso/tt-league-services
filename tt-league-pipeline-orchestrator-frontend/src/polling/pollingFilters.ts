import type { PipelineSource, PollingStatus } from '../api/types'
import { SOURCES } from '../runs/runFilters'
import { isValidSeason } from '../statistics/statisticsFilters'

export interface PollingFilters {
  /** The source whose schedules are shown; null until chosen (the page then picks a default). */
  readonly source: PipelineSource | null
  readonly season: string | null
  readonly errors: readonly string[]
}

/** Reads the filters from the URL; an unknown source or an invalid season is ignored with a message. */
export function parsePollingFilters(params: URLSearchParams): PollingFilters {
  const errors: string[] = []
  let source: PipelineSource | null = null
  const rawSource = params.get('source')
  if (rawSource !== null && rawSource !== '') {
    if ((SOURCES as readonly string[]).includes(rawSource)) {
      source = rawSource as PipelineSource
    } else {
      errors.push(`Unknown source "${rawSource}" was ignored.`)
    }
  }
  let season: string | null = null
  const rawSeason = params.get('season')
  if (rawSeason !== null && rawSeason !== '') {
    if (isValidSeason(rawSeason)) {
      season = rawSeason
    } else {
      errors.push(`Invalid season "${rawSeason}" was ignored.`)
    }
  }
  return { source, season, errors }
}

export function serializePollingFilters(filters: Pick<PollingFilters, 'source' | 'season'>): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.source !== null) {
    params.set('source', filters.source)
  }
  if (filters.season !== null) {
    params.set('season', filters.season)
  }
  return params
}

/**
 * The filters in use: the chosen ones, else the season of the schedule (as the server reports it) and the first
 * adaptively polled source, or the first source when none is.
 */
export function resolvePollingFilters(
  filters: Pick<PollingFilters, 'source' | 'season'>,
  status: PollingStatus | null,
): { readonly source: PipelineSource; readonly season: string | null } {
  const adaptive = status?.sources.find((entry) => entry.mode === 'ADAPTIVE')?.source
  return {
    source: filters.source ?? adaptive ?? status?.sources[0]?.source ?? SOURCES[0],
    season: filters.season ?? status?.season ?? null,
  }
}
