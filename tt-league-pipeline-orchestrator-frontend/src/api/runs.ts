import type { BlobResponse, HttpClient } from './client'
import type {
  Page,
  PipelineSource,
  RunDetail,
  RunStatus,
  RunSummary,
  TriggerResponse,
  TriggerRunRequest,
} from './types'

export interface RunListQuery {
  readonly source?: readonly PipelineSource[]
  readonly status?: readonly RunStatus[]
  /** Keeps the runs that have a unit with this key. */
  readonly unitKey?: string
  readonly from?: string
  readonly to?: string
  readonly page?: number
  readonly size?: number
}

export function listRuns(client: HttpClient, query: RunListQuery = {}, signal?: AbortSignal): Promise<Page<RunSummary>> {
  return client.request<Page<RunSummary>>('GET', '/api/pipeline/runs', {
    query: {
      source: query.source,
      status: query.status,
      unitKey: query.unitKey,
      from: query.from,
      to: query.to,
      page: query.page ?? 0,
      size: query.size ?? 20,
    },
    signal,
  })
}

export function getRun(client: HttpClient, id: string, signal?: AbortSignal): Promise<RunDetail> {
  return client.request<RunDetail>('GET', `/api/pipeline/runs/${encodeURIComponent(id)}`, { signal })
}

/** Resolves with the new RETRY run on 201; a 404, 409 or 422 rejects with an ApiError whose problem carries the code. */
export function replayRun(client: HttpClient, id: string): Promise<RunSummary> {
  return client.request<RunSummary>('POST', `/api/pipeline/runs/${encodeURIComponent(id)}/replay`)
}

/** The run created for a unit retry. */
export interface UnitRetryResult {
  readonly runId: string
}

/**
 * Re-runs one failed or skipped unit as a new UNIT_RETRY run (202); a 404, 409 or 422 rejects with an ApiError whose
 * problem carries the code.
 */
export function retryUnit(client: HttpClient, runId: string, unitId: string): Promise<UnitRetryResult> {
  return client.request<UnitRetryResult>(
    'POST',
    `/api/pipeline/runs/${encodeURIComponent(runId)}/units/${encodeURIComponent(unitId)}/retry`,
  )
}

/** The stored ZIP of a unit; the request carries the token, so it cannot be a plain link. */
export function downloadUnitPackage(client: HttpClient, runId: string, unitId: string): Promise<BlobResponse> {
  return client.requestBlob(
    `/api/pipeline/runs/${encodeURIComponent(runId)}/units/${encodeURIComponent(unitId)}/package`,
  )
}

export interface TriggerRunOutcome {
  readonly status: number
  readonly response: TriggerResponse
}

/** Resolves for 201/202; a 409/422 rejects with an ApiError whose problem carries the per-source results. */
export async function triggerRun(client: HttpClient, request: TriggerRunRequest): Promise<TriggerRunOutcome> {
  const { status, data } = await client.requestWithStatus<TriggerResponse>('POST', '/api/pipeline/runs', {
    body: request,
  })
  return { status, response: data }
}
