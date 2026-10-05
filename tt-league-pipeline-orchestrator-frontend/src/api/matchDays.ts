import type { HttpClient } from './client'
import type {
  MatchDayDetail,
  MatchDayState,
  MatchDaySummary,
  Page,
  PipelineSource,
} from './types'

export interface MatchDayListQuery {
  readonly source?: PipelineSource
  readonly season?: string
  readonly state?: MatchDayState
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
      from: query.from,
      to: query.to,
      page: query.page ?? 0,
      size: query.size ?? 50,
    },
    signal,
  })
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
