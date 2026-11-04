import type { HttpClient } from './client'
import type { PendingTrigger } from './types'

export function listPendingTriggers(client: HttpClient, signal?: AbortSignal): Promise<PendingTrigger[]> {
  return client.request<PendingTrigger[]>('GET', '/api/pipeline/pending-triggers', { signal })
}
