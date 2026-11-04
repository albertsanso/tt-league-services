import type { RunSummary, ScopeFilter, StepEvent } from '../api/types'
import { makeUnit } from '../test/runFixtures'
import {
  elapsedMs,
  formatBytes,
  formatDuration,
  mergeRunEvent,
  isStaleUnitEvent,
  mergeStepSummary,
  patchUnit,
  progressText,
  scopeDetails,
  scopeLabel,
  unitsSummary,
  upsertStep,
  upsertUnit,
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
  retryOfUnitId: null,
  currentUnitId: null,
  steps: [{ kind: 'INGEST', status: 'RUNNING', attempt: 1 }],
}

describe('mergeRunEvent', () => {
  it('applies event fields and keeps the row steps', () => {
    const merged = mergeRunEvent(row, { ...row, status: 'RUNNING' })
    expect(merged.status).toBe('RUNNING')
    expect(merged.steps).toBe(row.steps)
  })
})

describe('mergeStepSummary', () => {
  it('replaces the entry of the kind with the newest event, whichever attempt it is', () => {
    const steps = [{ kind: 'IMPORT' as const, status: 'FAILED' as const, attempt: 2 }]
    // the next unit starts again at attempt 1 of the same kind
    expect(mergeStepSummary(steps, { kind: 'IMPORT', status: 'RUNNING', attempt: 1 })).toEqual([
      { kind: 'IMPORT', status: 'RUNNING', attempt: 1 },
    ])
    expect(mergeStepSummary(steps, { kind: 'IMPORT', status: 'RUNNING', attempt: 3 })).toEqual([
      { kind: 'IMPORT', status: 'RUNNING', attempt: 3 },
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
  function step(kind: StepEvent['kind'], attempt: number, status: StepEvent['status'], unitId = 'u1'): StepEvent {
    return {
      runId: 'r1',
      unitId,
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

  it('keeps the attempts of different units apart', () => {
    const steps = [step('INGEST', 1, 'SUCCEEDED', 'u1')]
    const next = upsertStep(steps, step('INGEST', 1, 'RUNNING', 'u2'))
    expect(next.map((s) => `${s.unitId}:${s.status}`)).toEqual(['u1:SUCCEEDED', 'u2:RUNNING'])
    expect(upsertStep(next, step('INGEST', 1, 'SUCCEEDED', 'u2')).map((s) => `${s.unitId}:${s.status}`)).toEqual([
      'u1:SUCCEEDED',
      'u2:SUCCEEDED',
    ])
  })

  it('replaces by kind and attempt and orders by kind then attempt', () => {
    const steps = [step('IMPORT', 1, 'FAILED'), step('INGEST', 1, 'SUCCEEDED')]
    const next = upsertStep(steps, step('IMPORT', 2, 'RUNNING'))
    expect(next.map((s) => `${s.kind}#${s.attempt}`)).toEqual(['INGEST#1', 'IMPORT#1', 'IMPORT#2'])
    const replaced = upsertStep(next, step('IMPORT', 2, 'SUCCEEDED'))
    expect(replaced).toHaveLength(3)
    expect(replaced[2].status).toBe('SUCCEEDED')
  })
})

describe('units', () => {
  const progress = (updatedAt: string, itemsProcessed = 1) => ({
    step: 'INGEST' as const,
    stage: 'DOWNLOAD',
    itemsProcessed,
    itemsTotal: 10,
    percent: itemsProcessed * 10,
    currentItem: null,
    updatedAt,
  })

  it('ignores an event with older progress for the same status', () => {
    const current = makeUnit('u1', { status: 'RUNNING_INGEST', progress: progress('2026-10-01T10:00:10Z', 5) })
    const late = makeUnit('u1', { status: 'RUNNING_INGEST', progress: progress('2026-10-01T10:00:05Z', 3) })
    expect(isStaleUnitEvent(current, late)).toBe(true)
    expect(isStaleUnitEvent(late, current)).toBe(false)
  })

  it('never ignores a change of status, or an event without progress on either side', () => {
    const running = makeUnit('u1', { status: 'RUNNING_INGEST', progress: progress('2026-10-01T10:00:10Z') })
    const packed = makeUnit('u1', { status: 'PACKED', progress: progress('2026-10-01T10:00:05Z') })
    expect(isStaleUnitEvent(running, packed)).toBe(false)
    expect(isStaleUnitEvent(running, makeUnit('u1', { status: 'RUNNING_INGEST', progress: null }))).toBe(false)
    expect(isStaleUnitEvent(makeUnit('u1', { status: 'RUNNING_INGEST' }), running)).toBe(false)
  })

  it('patches the unit with the same id and keeps the counters the event does not carry', () => {
    const counters = {
      status: 'SUCCEEDED',
      filesSeen: 1,
      itemsPersisted: 1,
      skipped: 0,
      processorFailures: 0,
      scheduledCreated: 0,
      upgradedToPlayed: 0,
      rescheduled: 0,
      partialActas: 0,
      invalidActas: 0,
      unresolvedPendingFixtures: 0,
      amendedPlayed: 0,
      receivedAt: null,
    }
    const units = [makeUnit('u1', { counters }), makeUnit('u2', { ordinal: 1, status: 'PENDING', startedAt: null })]
    const patched = patchUnit(units, makeUnit('u2', { ordinal: 1, status: 'RUNNING_INGEST', counters: null }))
    expect(patched.map((unit) => unit.status)).toEqual(['SUCCEEDED', 'RUNNING_INGEST'])
    expect(patched[0]).toBe(units[0])
    expect(patchUnit(units, makeUnit('u1', { status: 'FAILED', counters: null }))[0].counters).toBe(counters)
  })

  it('leaves the list unchanged for an unknown unit or a late event', () => {
    const units = [makeUnit('u1', { status: 'RUNNING_INGEST', progress: progress('2026-10-01T10:00:10Z') })]
    expect(patchUnit(units, makeUnit('u9'))).toBe(units)
    expect(patchUnit(units, makeUnit('u1', { status: 'RUNNING_INGEST', progress: progress('2026-10-01T10:00:01Z') }))).toBe(
      units,
    )
  })

  it('adds an unknown unit in ordinal order and patches a known one', () => {
    const units = [makeUnit('u1'), makeUnit('u3', { ordinal: 2 })]
    expect(upsertUnit(units, makeUnit('u2', { ordinal: 1 })).map((unit) => unit.id)).toEqual(['u1', 'u2', 'u3'])
    expect(upsertUnit(undefined, makeUnit('u1')).map((unit) => unit.id)).toEqual(['u1'])
    expect(upsertUnit(units, makeUnit('u1', { status: 'FAILED' }))[0].status).toBe('FAILED')
  })

  it('counts the finished units', () => {
    expect(unitsSummary(undefined)).toBe('')
    expect(unitsSummary([])).toBe('')
    expect(unitsSummary([makeUnit('u1')])).toBe('1/1 unit')
    expect(
      unitsSummary([
        makeUnit('u1'),
        makeUnit('u2', { status: 'FAILED' }),
        makeUnit('u3', { status: 'SKIPPED' }),
        makeUnit('u4', { status: 'PENDING' }),
        makeUnit('u5', { status: 'IMPORTING' }),
      ]),
    ).toBe('3/5 units')
  })

  it('describes the progress', () => {
    expect(progressText(null)).toBe('')
    expect(progressText(progress('2026-10-01T10:00:10Z', 3))).toBe('DOWNLOAD · 3/10')
    expect(
      progressText({ ...progress('2026-10-01T10:00:10Z', 4), stage: null, itemsTotal: null, currentItem: 'group-1' }),
    ).toBe('4 · group-1')
    expect(progressText({ ...progress('2026-10-01T10:00:10Z', 0), itemsTotal: null })).toBe('DOWNLOAD')
  })
})
