import type {
  CorrectionsResponse,
  DailyStatsResponse,
  PendingResponse,
  ReportingProgressResponse,
  RunOutcomesResponse,
  SourceHealthResponse,
  TimeToReportResponse,
  UnitOutcomesResponse,
} from '../api/types'
import { json } from './fakeFetch'

export function makeDaily(): DailyStatsResponse {
  return {
    zone: 'Europe/Madrid',
    rows: [
      {
        date: '2026-10-03',
        source: 'FCTT',
        runs: 4,
        failures: 1,
        matchesReported: 7,
        avgTimeToReportSeconds: 5400,
        pendingEndOfDay: 3,
        computedAt: '2026-10-04T00:30:00Z',
      },
      {
        date: '2026-10-04',
        source: 'FCTT',
        runs: 2,
        failures: 0,
        matchesReported: 0,
        avgTimeToReportSeconds: null,
        pendingEndOfDay: 5,
        computedAt: '2026-10-05T00:30:00Z',
      },
    ],
  }
}

export function makeRunOutcomes(): RunOutcomesResponse {
  return {
    zone: 'Europe/Madrid',
    days: [{ date: '2026-10-03', source: 'FCTT', succeeded: 2, noChanges: 1, partial: 0, failed: 1 }],
    stepAverages: [{ source: 'FCTT', kind: 'INGEST', attempts: 5, avgStepSeconds: 90 }],
  }
}

export function makeUnitOutcomes(): UnitOutcomesResponse {
  return {
    zone: 'Europe/Madrid',
    units: [
      { source: 'FCTT', unitKey: 'season', label: 'Full season', succeeded: 5, noChanges: 2, partial: 0, failed: 1, skipped: 0, avgSeconds: 125 },
      { source: 'FCTT', unitKey: 'a'.repeat(64), label: 'Tercera Group 1', succeeded: 3, noChanges: 0, partial: 1, failed: 2, skipped: 1, avgSeconds: null },
    ],
  }
}

export function makeTimeToReport(): TimeToReportResponse {
  return {
    season: '2026-2027',
    rows: [
      { source: 'FCTT', competition: null, count: 6, medianSeconds: 21_600, p90Seconds: 72_000 },
      { source: 'FCTT', competition: 'PREFERENT', count: 1, medianSeconds: 72_000, p90Seconds: 72_000 },
      { source: 'FCTT', competition: 'TERCERA', count: 5, medianSeconds: 21_600, p90Seconds: 36_000 },
    ],
  }
}

export function makePending(): PendingResponse {
  return {
    asOf: '2026-10-05T08:00:00Z',
    sources: [{ source: 'FCTT', under1Day: 1, days1To2: 2, days2To7: 3, over7Days: 4, overdue: 5 }],
  }
}

export function makeCorrections(): CorrectionsResponse {
  return {
    zone: 'Europe/Madrid',
    days: [{ date: '2026-10-03', source: 'FCTT', amendedPlayed: 2 }],
    totals: [{ source: 'FCTT', amendedPlayed: 2 }],
  }
}

export function makeSourceHealth(): SourceHealthResponse {
  const counts = { source: 'FCTT', httpErrors: 3, timeouts: 2, parseErrors: 1, ingestAttempts: 4, sourceUnavailable: 1, healthUnknown: 0 } as const
  return {
    zone: 'Europe/Madrid',
    days: [{ date: '2026-10-03', ...counts }],
    totals: [counts, { ...counts, source: 'RFETM', parseErrors: 0 }],
  }
}

export function makeProgress(): ReportingProgressResponse {
  return {
    source: 'FCTT',
    season: '2026-2027',
    zone: 'Europe/Madrid',
    matchDays: [
      {
        matchDayId: 'md-1',
        competition: 'TERCERA',
        groupNumber: 1,
        phase: '1a Fase',
        round: 3,
        state: 'OPEN',
        windowStart: '2026-10-03',
        windowEnd: '2026-10-06',
        active: 8,
        reported: 6,
        postponed: 1,
        pending: 1,
        points: [
          { date: '2026-10-03', reported: 2, pending: 5 },
          { date: '2026-10-04', reported: 6, pending: 1 },
        ],
      },
      {
        matchDayId: 'md-2',
        competition: 'TERCERA',
        groupNumber: 1,
        phase: '1a Fase',
        round: 4,
        state: 'UPCOMING',
        windowStart: '2026-10-10',
        windowEnd: '2026-10-13',
        active: 8,
        reported: 0,
        postponed: 0,
        pending: 8,
        points: [],
      },
    ],
  }
}

export interface StatisticsOverrides {
  daily?: unknown
  runs?: unknown
  units?: unknown
  timeToReport?: unknown
  pending?: unknown
  corrections?: unknown
  sourceHealth?: unknown
  progress?: unknown
  /** A status other than 200 makes every statistics endpoint fail with it. */
  failWith?: number
}

/** The default answer of every statistics endpoint, by URL; null for URLs of other endpoints. */
export function statisticsResponse(url: string, overrides: StatisticsOverrides = {}): Response | null {
  if (!url.startsWith('/api/pipeline/statistics/')) {
    return null
  }
  if (overrides.failWith !== undefined) {
    return json({ title: 'Statistics unavailable', status: overrides.failWith }, overrides.failWith, 'application/problem+json')
  }
  if (url.startsWith('/api/pipeline/statistics/daily')) {
    return json(overrides.daily ?? makeDaily())
  }
  if (url.startsWith('/api/pipeline/statistics/runs')) {
    return json(overrides.runs ?? makeRunOutcomes())
  }
  if (url.startsWith('/api/pipeline/statistics/units')) {
    return json(overrides.units ?? makeUnitOutcomes())
  }
  if (url.startsWith('/api/pipeline/statistics/time-to-report')) {
    return json(overrides.timeToReport ?? makeTimeToReport())
  }
  if (url.startsWith('/api/pipeline/statistics/pending')) {
    return json(overrides.pending ?? makePending())
  }
  if (url.startsWith('/api/pipeline/statistics/corrections')) {
    return json(overrides.corrections ?? makeCorrections())
  }
  if (url.startsWith('/api/pipeline/statistics/source-health')) {
    return json(overrides.sourceHealth ?? makeSourceHealth())
  }
  if (url.startsWith('/api/pipeline/statistics/reporting-progress')) {
    return json(overrides.progress ?? makeProgress())
  }
  return json({ title: 'Not found' }, 404)
}

export const emptyDaily: DailyStatsResponse = { zone: 'Europe/Madrid', rows: [] }
export const emptyRuns: RunOutcomesResponse = { zone: 'Europe/Madrid', days: [], stepAverages: [] }
export const emptyUnits: UnitOutcomesResponse = { zone: 'Europe/Madrid', units: [] }
export const emptyCorrections: CorrectionsResponse = { zone: 'Europe/Madrid', days: [], totals: [] }
export const emptyHealth: SourceHealthResponse = { zone: 'Europe/Madrid', days: [], totals: [] }
export const emptyPending: PendingResponse = { asOf: '2026-10-05T08:00:00Z', sources: [] }
export const emptyTimeToReport: TimeToReportResponse = { season: '2026-2027', rows: [] }
