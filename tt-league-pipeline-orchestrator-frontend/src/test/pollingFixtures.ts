import type { PipelineSource, PollingPolicy, PollingStatus, PollSchedule } from '../api/types'
import { json } from './fakeFetch'
import type { FakeHandler } from './fakeFetch'

/** FCTT is polled adaptively, RFETM has a cron and BCNESA is not scheduled. */
export function makeStatus(overrides: Partial<PollingStatus> = {}): PollingStatus {
  return {
    season: '2026-2027',
    zone: 'Europe/Madrid',
    tickInterval: 'PT5M',
    sources: [
      { source: 'RFETM', mode: 'CRON', cron: '0 0 3 * * *' },
      { source: 'BCNESA', mode: 'NONE', cron: null },
      { source: 'FCTT', mode: 'ADAPTIVE', cron: null },
    ],
    ...overrides,
  }
}

export function makePolicy(source: PipelineSource, overrides: Partial<PollingPolicy> = {}): PollingPolicy {
  return {
    source,
    matchDay: 'PT2H',
    matchDayStartOffset: 'PT2H',
    dayAfter: 'PT3H',
    daysTwoToSeven: 'PT12H',
    open: 'PT24H',
    overdue: 'PT24H',
    overdueStopAfterDays: 21,
    fullRefresh: 'PT168H',
    noChangeThreshold: 3,
    recentMatchDays: 3,
    overridden: false,
    version: 0,
    updatedBy: null,
    updatedAt: null,
    ...overrides,
  }
}

export function makePolicies(overrides: Partial<Record<PipelineSource, Partial<PollingPolicy>>> = {}): PollingPolicy[] {
  return (['RFETM', 'BCNESA', 'FCTT'] as const).map((source) => makePolicy(source, overrides[source]))
}

export function makeSchedule(id: string, overrides: Partial<PollSchedule> = {}): PollSchedule {
  return {
    id,
    source: 'FCTT',
    season: '2026-2027',
    kind: 'GROUP',
    scopeKey: `key-${id}`,
    filter: { category: 'tercera', group: 'G1', phase: '1a Fase', territory: null, gender: 'male', matchDays: [3, 4, 5] },
    level: 'OPEN',
    interval: 'PT24H',
    consecutiveNoChange: 1,
    nextRunAt: '2026-10-05T10:00:00Z',
    lastRunAt: '2026-10-04T10:00:00Z',
    pendingRunId: null,
    stoppedAt: null,
    stopReason: null,
    version: 1,
    ...overrides,
  }
}

export function makeStoppedSchedule(id: string, overrides: Partial<PollSchedule> = {}): PollSchedule {
  return makeSchedule(id, {
    level: 'STOPPED',
    interval: null,
    nextRunAt: null,
    stoppedAt: '2026-10-03T10:00:00Z',
    stopReason: 'OVERDUE_LIMIT',
    ...overrides,
  })
}

export interface PollingOverrides {
  readonly status?: PollingStatus
  readonly policies?: readonly PollingPolicy[]
  readonly schedules?: readonly PollSchedule[]
  /** Answers a polling request itself (for PUT, DELETE and POST); return undefined to fall back to the fixtures. */
  readonly onRequest?: (request: Parameters<FakeHandler>[0]) => Response | undefined
}

/** Answers the `/api/pipeline/polling/*` reads from the overrides; null when the request is not a polling one. */
export function pollingResponse(
  request: Parameters<FakeHandler>[0],
  overrides: PollingOverrides = {},
): Response | null {
  const path = request.url.split('?')[0]
  if (!path.startsWith('/api/pipeline/polling/')) {
    return null
  }
  const custom = overrides.onRequest?.(request)
  if (custom !== undefined) {
    return custom
  }
  if (request.method === 'GET' && path.endsWith('/polling/status')) {
    return json(overrides.status ?? makeStatus())
  }
  if (request.method === 'GET' && path.endsWith('/polling/policies')) {
    return json(overrides.policies ?? makePolicies())
  }
  if (request.method === 'GET' && path.endsWith('/polling/schedules')) {
    return json(overrides.schedules ?? [])
  }
  return json({ title: 'Not found' }, 404)
}
