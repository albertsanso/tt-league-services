import type { HttpClient } from './client'
import type {
  CorrectionsResponse,
  DailyStatsResponse,
  PendingResponse,
  PipelineSource,
  ReportingProgressResponse,
  RunOutcomesResponse,
  SourceHealthResponse,
  TimeToReportResponse,
} from './types'

const BASE = '/api/pipeline/statistics'

export interface RangeQuery {
  /** Local `YYYY-MM-DD`, inclusive. */
  readonly from: string
  readonly to: string
  readonly source?: readonly PipelineSource[]
}

function rangeQuery(query: RangeQuery) {
  return { from: query.from, to: query.to, source: query.source }
}

export function getDailyStats(client: HttpClient, query: RangeQuery, signal?: AbortSignal): Promise<DailyStatsResponse> {
  return client.request<DailyStatsResponse>('GET', `${BASE}/daily`, { query: rangeQuery(query), signal })
}

export function getRunOutcomes(client: HttpClient, query: RangeQuery, signal?: AbortSignal): Promise<RunOutcomesResponse> {
  return client.request<RunOutcomesResponse>('GET', `${BASE}/runs`, { query: rangeQuery(query), signal })
}

export function getTimeToReport(
  client: HttpClient,
  query: { readonly season: string; readonly source?: readonly PipelineSource[] },
  signal?: AbortSignal,
): Promise<TimeToReportResponse> {
  return client.request<TimeToReportResponse>('GET', `${BASE}/time-to-report`, {
    query: { season: query.season, source: query.source },
    signal,
  })
}

export function getPendingByAge(
  client: HttpClient,
  query: { readonly season?: string; readonly source?: readonly PipelineSource[] } = {},
  signal?: AbortSignal,
): Promise<PendingResponse> {
  return client.request<PendingResponse>('GET', `${BASE}/pending`, {
    query: { season: query.season, source: query.source },
    signal,
  })
}

export function getCorrections(client: HttpClient, query: RangeQuery, signal?: AbortSignal): Promise<CorrectionsResponse> {
  return client.request<CorrectionsResponse>('GET', `${BASE}/corrections`, { query: rangeQuery(query), signal })
}

export function getSourceHealth(client: HttpClient, query: RangeQuery, signal?: AbortSignal): Promise<SourceHealthResponse> {
  return client.request<SourceHealthResponse>('GET', `${BASE}/source-health`, { query: rangeQuery(query), signal })
}

/** Exactly one source; a null competition reads every competition of the season. */
export function getReportingProgress(
  client: HttpClient,
  query: { readonly source: PipelineSource; readonly season: string; readonly competition?: string | null },
  signal?: AbortSignal,
): Promise<ReportingProgressResponse> {
  return client.request<ReportingProgressResponse>('GET', `${BASE}/reporting-progress`, {
    query: { source: query.source, season: query.season, competition: query.competition ?? undefined },
    signal,
  })
}
