import type { HttpClient } from './client'
import type { TriggerRunOutcome } from './runs'
import type {
  MatchDayDetail,
  MatchDayFacets,
  MatchDayResults,
  MatchDayState,
  MatchDaySummary,
  Page,
  PipelineSource,
  TriggerResponse,
} from './types'

export interface MatchDayListQuery {
  readonly source?: PipelineSource
  readonly season?: string
  readonly state?: MatchDayState
  /** The category: an exact competition value from the facets. */
  readonly competition?: string
  readonly phase?: string
  /** Only match days without dates; cannot be combined with `from` or `to`. */
  readonly undated?: boolean
  readonly from?: string
  readonly to?: string
  readonly page?: number
  readonly size?: number
}

const BASE = '/api/pipeline/match-days'

function matchDayPath(id: string, suffix = ''): string {
  return `${BASE}/${encodeURIComponent(id)}${suffix}`
}

export function listMatchDays(
  client: HttpClient,
  query: MatchDayListQuery = {},
  signal?: AbortSignal,
): Promise<Page<MatchDaySummary>> {
  return client.request<Page<MatchDaySummary>>('GET', BASE, {
    query: {
      source: query.source,
      season: query.season,
      state: query.state,
      competition: query.competition,
      phase: query.phase,
      undated: query.undated === true ? true : undefined,
      from: query.from,
      to: query.to,
      page: query.page ?? 0,
      size: query.size ?? 50,
    },
    signal,
  })
}

export function getMatchDayFacets(
  client: HttpClient,
  query: { readonly source?: PipelineSource; readonly season?: string } = {},
  signal?: AbortSignal,
): Promise<MatchDayFacets> {
  return client.request<MatchDayFacets>('GET', `${BASE}/facets`, {
    query: { source: query.source, season: query.season },
    signal,
  })
}

export function getMatchDayResults(client: HttpClient, id: string, signal?: AbortSignal): Promise<MatchDayResults> {
  return client.request<MatchDayResults>('GET', matchDayPath(id, '/results'), { signal })
}

/** Resolves for 201/202; a 409/422 rejects with an ApiError whose problem carries the per-source results. */
export async function refreshMatchDay(client: HttpClient, id: string, force: boolean): Promise<TriggerRunOutcome> {
  const { status, data } = await client.requestWithStatus<TriggerResponse>('POST', matchDayPath(id, '/refresh'), {
    body: { force },
  })
  return { status, response: data }
}

export function getMatchDay(client: HttpClient, id: string, signal?: AbortSignal): Promise<MatchDayDetail> {
  return client.request<MatchDayDetail>('GET', matchDayPath(id), { signal })
}

export function closeMatchDay(client: HttpClient, id: string, note?: string): Promise<MatchDayDetail> {
  return client.request<MatchDayDetail>('POST', matchDayPath(id, '/close'), { body: { note } })
}

export function reopenMatchDay(client: HttpClient, id: string, note?: string): Promise<MatchDayDetail> {
  return client.request<MatchDayDetail>('POST', matchDayPath(id, '/reopen'), { body: { note } })
}

export function ignoreMatch(
  client: HttpClient,
  id: string,
  matchId: string,
  note?: string,
): Promise<MatchDayDetail> {
  return client.request<MatchDayDetail>('PUT', matchDayPath(id, `/matches/${encodeURIComponent(matchId)}/ignore`), {
    body: { note },
  })
}

export function unignoreMatch(
  client: HttpClient,
  id: string,
  matchId: string,
  note?: string,
): Promise<MatchDayDetail> {
  return client.request<MatchDayDetail>('DELETE', matchDayPath(id, `/matches/${encodeURIComponent(matchId)}/ignore`), {
    body: { note },
  })
}

export function addMatchDayNote(
  client: HttpClient,
  id: string,
  text: string,
  matchId?: string,
): Promise<MatchDayDetail> {
  return client.request<MatchDayDetail>('POST', matchDayPath(id, '/notes'), { body: { text, matchId } })
}
