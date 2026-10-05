import type { RunSummary, ScopeFilter, StepEvent } from '../api/types'
import {
  elapsedMs,
  formatBytes,
  formatDuration,
  mergeRunEvent,
  mergeStepSummary,
  scopeDetails,
  scopeLabel,
  upsertStep,
} from './format'

function filter(partial: Partial<ScopeFilter>): ScopeFilter {
  return { category: null, group: null, phase: null, territory: null, gender: null, matchDays: null, ...partial }
}

describe('formatDuration', () => {
  it.each([
    [null, '—'],
    [undefined, '—'],
    [0, '0 s'],
    [12_000, '12 s'],
    [59_999, '59 s'],
    [60_000, '1 min 00 s'],
    [185_000, '3 min 05 s'],
    [3_599_000, '59 min 59 s'],
    [3_720_000, '1 h 02 min'],
  ])('formats %s as %s', (value, expected) => {
    expect(formatDuration(value)).toBe(expected)
  })
})

describe('elapsedMs', () => {
  it('is null before the run started', () => {
    expect(elapsedMs(null, null, Date.now())).toBeNull()
  })

  it('measures an active run up to now', () => {
    const now = Date.parse('2026-10-01T10:00:30Z')
    expect(elapsedMs('2026-10-01T10:00:00Z', null, now)).toBe(30_000)
  })

  it('measures a finished run up to its finish', () => {
    expect(elapsedMs('2026-10-01T10:00:00Z', '2026-10-01T10:01:00Z', Date.now())).toBe(60_000)
  })
})

describe('formatBytes', () => {
  it.each([
    [512, '512 B'],
    [2048, '2.0 KiB'],
    [5 * 1024 * 1024, '5.0 MiB'],
  ])('formats %s as %s', (value, expected) => {
    expect(formatBytes(value)).toBe(expected)
  })
})

describe('scope labels', () => {
  it('labels a full season', () => {
    expect(scopeLabel({ fullSeason: true, filters: [] })).toBe('Full season')
  })

  it('labels open match days when there are no filters', () => {
    expect(scopeLabel({ fullSeason: false, filters: [] })).toBe('Open match days')
  })

  it('shows only the set fields of one filter, with match days', () => {
    const run = { fullSeason: false, filters: [filter({ category: 'Senior', group: 'A', matchDays: [3, 4] })] }
    expect(scopeLabel(run)).toBe('Senior · A · md 3, 4')
  })

  it('collapses more than two filters into +N more and keeps all in the details', () => {
    const run = {
      fullSeason: false,
      filters: [filter({ group: 'A' }), filter({ group: 'B' }), filter({ group: 'C' })],
    }
    expect(scopeLabel(run)).toBe('A | B +1 more')
    expect(scopeDetails(run)).toEqual(['A', 'B', 'C'])
  })
})

const row: RunSummary = {
  id: 'r1',
  source: 'RFETM',
  season: '2025-2026',
  filters: [],
  fullSeason: false,
  trigger: 'MANUAL',
  requestedBy: 'ana',
  force: false,
  importJobReused: null,
  status: 'QUEUED',
  createdAt: '2026-10-01T10:00:00Z',
  startedAt: null,
  finishedAt: null,
  durationMs: null,
  error: null,
  ingestRunId: null,
  importJobId: null,
  retryOfRunId: null,
  steps: [{ kind: 'INGEST', status: 'RUNNING', attempt: 1 }],
}

describe('mergeRunEvent', () => {
  it('applies event fields and keeps the row steps', () => {
    const merged = mergeRunEvent(row, { ...row, status: 'RUNNING_INGEST' })
    expect(merged.status).toBe('RUNNING_INGEST')
    expect(merged.steps).toBe(row.steps)
  })
})

describe('mergeStepSummary', () => {
  it('ignores an older attempt', () => {
    const steps = [{ kind: 'IMPORT' as const, status: 'FAILED' as const, attempt: 2 }]
    expect(mergeStepSummary(steps, { kind: 'IMPORT', status: 'RUNNING', attempt: 1 })).toBe(steps)
  })

  it('replaces the same or a newer attempt', () => {
    const steps = [{ kind: 'IMPORT' as const, status: 'RUNNING' as const, attempt: 1 }]
    expect(mergeStepSummary(steps, { kind: 'IMPORT', status: 'FAILED', attempt: 1 })).toEqual([
      { kind: 'IMPORT', status: 'FAILED', attempt: 1 },
    ])
    expect(mergeStepSummary(steps, { kind: 'IMPORT', status: 'RUNNING', attempt: 2 })).toEqual([
      { kind: 'IMPORT', status: 'RUNNING', attempt: 2 },
    ])
  })

  it('inserts a new kind in step order, also from no steps', () => {
    const steps = [{ kind: 'IMPORT' as const, status: 'RUNNING' as const, attempt: 1 }]
    expect(mergeStepSummary(steps, { kind: 'INGEST', status: 'SUCCEEDED', attempt: 1 }).map((s) => s.kind)).toEqual([
      'INGEST',
      'IMPORT',
    ])
    expect(mergeStepSummary(undefined, { kind: 'INGEST', status: 'RUNNING', attempt: 1 })).toHaveLength(1)
  })
})

describe('upsertStep', () => {
  function step(kind: StepEvent['kind'], attempt: number, status: StepEvent['status']): StepEvent {
    return {
      runId: 'r1',
      kind,
      attempt,
      status,
      startedAt: null,
      finishedAt: null,
      durationMs: null,
      externalRef: null,
      outcome: null,
      retryable: null,
      error: null,
      importJobReused: null,
      health: null,
    }
  }

  it('replaces by kind and attempt and orders by kind then attempt', () => {
    const steps = [step('IMPORT', 1, 'FAILED'), step('INGEST', 1, 'SUCCEEDED')]
    const next = upsertStep(steps, step('IMPORT', 2, 'RUNNING'))
    expect(next.map((s) => `${s.kind}#${s.attempt}`)).toEqual(['INGEST#1', 'IMPORT#1', 'IMPORT#2'])
    const replaced = upsertStep(next, step('IMPORT', 2, 'SUCCEEDED'))
    expect(replaced).toHaveLength(3)
    expect(replaced[2].status).toBe('SUCCEEDED')
  })
})
