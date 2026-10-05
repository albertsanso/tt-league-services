import type { HttpClient } from './client'
import type { PipelineSource, PollingPolicy, PollingPolicyRequest, PollSchedule } from './types'

const BASE = '/api/pipeline/polling'

export function listPollingPolicies(client: HttpClient, signal?: AbortSignal): Promise<PollingPolicy[]> {
  return client.request<PollingPolicy[]>('GET', `${BASE}/policies`, { signal })
}

export function getPollingPolicy(
  client: HttpClient,
  source: PipelineSource,
  signal?: AbortSignal,
): Promise<PollingPolicy> {
  return client.request<PollingPolicy>('GET', `${BASE}/policies/${encodeURIComponent(source)}`, { signal })
}

export function replacePollingPolicy(
  client: HttpClient,
  source: PipelineSource,
  request: PollingPolicyRequest,
): Promise<PollingPolicy> {
  return client.request<PollingPolicy>('PUT', `${BASE}/policies/${encodeURIComponent(source)}`, { body: request })
}

export function deletePollingPolicy(client: HttpClient, source: PipelineSource): Promise<void> {
  return client.request<void>('DELETE', `${BASE}/policies/${encodeURIComponent(source)}`)
}

export function listPollSchedules(
  client: HttpClient,
  query: { readonly source?: PipelineSource; readonly season?: string } = {},
  signal?: AbortSignal,
): Promise<PollSchedule[]> {
  return client.request<PollSchedule[]>('GET', `${BASE}/schedules`, {
    query: { source: query.source, season: query.season },
    signal,
  })
}

export function resumePollSchedule(client: HttpClient, id: string): Promise<PollSchedule> {
  return client.request<PollSchedule>('POST', `${BASE}/schedules/${encodeURIComponent(id)}/resume`)
}
