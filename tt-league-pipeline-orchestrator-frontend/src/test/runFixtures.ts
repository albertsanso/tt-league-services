import type { Page, RunDetail, RunSummary, RunUnit, RunUnitDetail, Step } from '../api/types'

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
    retryOfUnitId: null,
    importJobReused: null,
    currentUnitId: null,
    steps: [],
    ...overrides,
  }
}

export function makePage(items: RunSummary[], pageIndex = 0, totalItems = items.length): Page<RunSummary> {
  return { items, page: pageIndex, size: 20, totalItems, totalPages: Math.max(1, Math.ceil(totalItems / 20)) }
}

/** One unit of a run; the default is the whole-season unit of `r1`, finished and without progress. */
export function makeUnit(id: string, overrides: Partial<RunUnit> = {}): RunUnit {
  return {
    runId: 'r1',
    id,
    ordinal: 0,
    unitKey: 'season',
    label: 'Full season',
    filters: [],
    status: 'SUCCEEDED',
    startedAt: '2026-10-01T10:00:05Z',
    finishedAt: '2026-10-01T10:01:05Z',
    durationMs: 60_000,
    error: null,
    ingestRunId: null,
    importJobId: null,
    progress: null,
    counters: null,
    ...overrides,
  }
}

/** A unit of the run detail: no steps or artifacts, not retryable, no retries. */
export function makeUnitDetail(id: string, overrides: Partial<RunUnitDetail> = {}): RunUnitDetail {
  return {
    ...makeUnit(id),
    steps: [],
    artifacts: [],
    storageFolder: null,
    packageUrl: null,
    retry: { eligible: false, reason: 'UNIT_NOT_RETRYABLE' },
    retriedBy: [],
    ...overrides,
  }
}

export function makeStep(runId: string, overrides: Partial<Step> = {}): Step {
  return {
    runId,
    unitId: 'u1',
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
    importJobReused: null,
    ...overrides,
  }
}

export function makeDetail(id: string, overrides: Partial<RunDetail> = {}): RunDetail {
  const { steps: _steps, units: _units, ...summary } = makeRun(id)
  void _steps
  void _units
  return {
    ...summary,
    units: [],
    steps: [],
    artifacts: [],
    importReport: null,
    issues: [],
    replay: { allowed: true, code: null },
    ...overrides,
  }
}
