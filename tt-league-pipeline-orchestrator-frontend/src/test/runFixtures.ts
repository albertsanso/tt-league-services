import type { Page, RunDetail, RunSummary, Step } from '../api/types'

export function makeRun(id: string, overrides: Partial<RunSummary> = {}): RunSummary {
  return {
    id,
    source: 'RFETM',
    season: '2025-2026',
    filters: [],
    fullSeason: false,
    trigger: 'MANUAL',
    requestedBy: 'ana',
    force: false,
    status: 'SUCCEEDED',
    createdAt: '2026-10-01T10:00:00Z',
    startedAt: '2026-10-01T10:00:05Z',
    finishedAt: '2026-10-01T10:01:05Z',
    durationMs: 60_000,
    error: null,
    ingestRunId: null,
    importJobId: null,
    retryOfRunId: null,
    steps: [],
    ...overrides,
  }
}

export function makePage(items: RunSummary[], pageIndex = 0, totalItems = items.length): Page<RunSummary> {
  return { items, page: pageIndex, size: 20, totalItems, totalPages: Math.max(1, Math.ceil(totalItems / 20)) }
}

export function makeStep(runId: string, overrides: Partial<Step> = {}): Step {
  return {
    runId,
    kind: 'INGEST',
    attempt: 1,
    status: 'SUCCEEDED',
    startedAt: '2026-10-01T10:00:05Z',
    finishedAt: '2026-10-01T10:00:30Z',
    durationMs: 25_000,
    externalRef: null,
    outcome: null,
    retryable: null,
    error: null,
    health: null,
    ...overrides,
  }
}

export function makeDetail(id: string, overrides: Partial<RunDetail> = {}): RunDetail {
  const { steps: _ignored, ...summary } = makeRun(id)
  void _ignored
  return { ...summary, steps: [], artifacts: [], importReport: null, issues: [], ...overrides }
}
