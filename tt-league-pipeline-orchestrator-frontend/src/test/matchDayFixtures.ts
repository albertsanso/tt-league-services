import type {
  MatchDayCompletion,
  MatchDayDetail,
  MatchDayEvent,
  MatchDayFacets,
  MatchDayResults,
  MatchDaySummary,
  MatchResult,
  Page,
  TrackedMatch,
  TrackedMatchStatus,
} from '../api/types'
import { makeRun } from './runFixtures'

const ZERO_COUNTS: Record<TrackedMatchStatus, number> = {
  SCHEDULED: 0,
  AWAITING_RESULT: 0,
  REPORTED: 0,
  POSTPONED: 0,
  OVERDUE: 0,
}

export function makeSummary(id: string, overrides: Partial<MatchDaySummary> = {}): MatchDaySummary {
  return {
    id,
    source: 'FCTT',
    season: '2026-2027',
    competition: 'TERCERA-masculino',
    groupNumber: 2,
    phase: '1a Fase',
    round: 3,
    firstDate: '2026-10-04',
    lastDate: '2026-10-05',
    windowEnd: '2026-10-07',
    graceDays: 2,
    state: 'OPEN',
    closeReason: null,
    closedAt: null,
    closedBy: null,
    openedAt: '2026-10-04T08:00:00Z',
    lastRecomputedAt: '2026-10-05T08:00:00Z',
    matchCounts: { ...ZERO_COUNTS, REPORTED: 4, AWAITING_RESULT: 2 },
    ignoredMatches: 0,
    completion: 'IN_PROGRESS',
    reportedMatches: 4,
    totalMatches: 6,
    ...overrides,
  }
}

/** One summary per completion category, on consecutive days of October 2026. */
export function summariesForEveryCompletion(): MatchDaySummary[] {
  const make = (
    id: string,
    completion: MatchDayCompletion,
    day: string,
    round: number,
    overrides: Partial<MatchDaySummary>,
  ) => makeSummary(id, { completion, firstDate: day, lastDate: day, round, ...overrides })
  return [
    make('complete', 'COMPLETE', '2026-10-03', 1, {
      state: 'CLOSED',
      closeReason: 'ALL_RESOLVED',
      closedBy: 'system:tracker',
      closedAt: '2026-10-04T09:00:00Z',
      matchCounts: { ...ZERO_COUNTS, REPORTED: 6 },
      reportedMatches: 6,
      totalMatches: 6,
    }),
    make('progress', 'IN_PROGRESS', '2026-10-04', 2, {}),
    make('overdue', 'HAS_OVERDUE', '2026-10-05', 3, {
      matchCounts: { ...ZERO_COUNTS, REPORTED: 3, OVERDUE: 2, POSTPONED: 1 },
      ignoredMatches: 1,
      reportedMatches: 3,
      totalMatches: 5,
    }),
    make('future', 'FUTURE', '2026-10-12', 4, {
      state: 'UPCOMING',
      matchCounts: { ...ZERO_COUNTS, SCHEDULED: 6 },
      reportedMatches: 0,
      totalMatches: 6,
    }),
  ]
}

export function makeSummaryPage(items: MatchDaySummary[], pageIndex = 0, totalItems = items.length): Page<MatchDaySummary> {
  return { items, page: pageIndex, size: 200, totalItems, totalPages: Math.max(1, Math.ceil(totalItems / 200)) }
}

export function makeFacets(overrides: Partial<MatchDayFacets> = {}): MatchDayFacets {
  return {
    seasons: ['2026-2027', '2025-2026'],
    competitions: ['SEGUNDA-masculino', 'TERCERA-masculino'],
    phases: ['1a Fase', '2a Fase'],
    ...overrides,
  }
}

export function makeMatch(matchId: string, status: TrackedMatchStatus, overrides: Partial<TrackedMatch> = {}): TrackedMatch {
  const reported = status === 'REPORTED'
  return {
    matchId,
    status,
    matchDateTime: '2026-10-04T16:00:00Z',
    homeTeamName: `Home ${matchId}`,
    awayTeamName: `Away ${matchId}`,
    firstSeenAt: '2026-10-01T08:00:00Z',
    statusChangedAt: '2026-10-04T18:00:00Z',
    lastSeenAt: '2026-10-05T08:00:00Z',
    reportedAt: reported ? '2026-10-04T19:00:00Z' : null,
    reportedRunId: reported ? 'run-1' : null,
    ignoredAt: null,
    ignoredBy: null,
    ...overrides,
  }
}

export function makeEvent(id: string, kind: MatchDayEvent['kind'], overrides: Partial<MatchDayEvent> = {}): MatchDayEvent {
  return {
    id,
    matchId: null,
    kind,
    actor: 'system:tracker',
    occurredAt: '2026-10-04T10:00:00Z',
    runId: null,
    note: null,
    ...overrides,
  }
}

/** Reported, postponed, overdue and ignored matches, an event of every kind and two runs. */
export function makeMatchDayDetail(id = 'day-1', overrides: Partial<MatchDayDetail> = {}): MatchDayDetail {
  const matchDay = makeSummary(id, {
    completion: 'HAS_OVERDUE',
    matchCounts: { ...ZERO_COUNTS, REPORTED: 2, POSTPONED: 1, OVERDUE: 2 },
    ignoredMatches: 1,
    reportedMatches: 2,
    totalMatches: 4,
  })
  return {
    matchDay,
    matches: [
      makeMatch('m-reported', 'REPORTED', { matchDateTime: '2026-10-04T10:00:00Z' }),
      makeMatch('m-reported-2', 'REPORTED', { matchDateTime: '2026-10-04T11:00:00Z', reportedRunId: 'run-2' }),
      makeMatch('m-postponed', 'POSTPONED', { matchDateTime: '2026-10-04T12:00:00Z' }),
      makeMatch('m-overdue', 'OVERDUE', { matchDateTime: '2026-10-04T13:00:00Z' }),
      makeMatch('m-ignored', 'OVERDUE', {
        matchDateTime: '2026-10-04T14:00:00Z',
        ignoredAt: '2026-10-05T07:00:00Z',
        ignoredBy: 'ana',
      }),
    ],
    events: [
      makeEvent('e1', 'OPENED', { occurredAt: '2026-10-04T08:00:00Z' }),
      makeEvent('e2', 'MATCH_REPORTED', { matchId: 'm-reported', runId: 'run-1', occurredAt: '2026-10-04T19:00:00Z' }),
      makeEvent('e3', 'MATCH_REMOVED', { matchId: 'm-gone', note: 'No longer listed by the platform', occurredAt: '2026-10-04T20:00:00Z' }),
      makeEvent('e4', 'MATCH_IGNORED', { matchId: 'm-ignored', actor: 'ana', occurredAt: '2026-10-05T07:00:00Z' }),
      makeEvent('e5', 'MATCH_UNIGNORED', { matchId: 'm-ignored', actor: 'ana', occurredAt: '2026-10-05T07:10:00Z' }),
      makeEvent('e6', 'NOTE', { actor: 'ana', note: 'Called the club', occurredAt: '2026-10-05T07:20:00Z' }),
      makeEvent('e7', 'REFRESH_REQUESTED', { actor: 'ana', runId: 'run-2', occurredAt: '2026-10-05T07:30:00Z' }),
      makeEvent('e8', 'CLOSED', { actor: 'ana', note: 'League decision', occurredAt: '2026-10-05T07:40:00Z' }),
      makeEvent('e9', 'REOPENED', { actor: 'ana', occurredAt: '2026-10-05T07:50:00Z' }),
    ],
    runs: [
      makeRun('run-2', { createdAt: '2026-10-05T07:30:00Z', requestedBy: 'ana', status: 'RUNNING', finishedAt: null }),
      makeRun('run-1', { createdAt: '2026-10-04T18:30:00Z', source: 'FCTT', requestedBy: null, trigger: 'SCHEDULED' }),
    ],
    ...overrides,
  }
}

export function makeResults(matchDayId = 'day-1', results: MatchResult[] = []): MatchDayResults {
  return {
    matchDayId,
    platformToday: '2026-10-05',
    results: results.length > 0
      ? results
      : [
          { matchId: 'm-reported', platformStatus: 'PLAYED', homeGamesWon: 3, awayGamesWon: 1, winnerTeamName: 'Home m-reported' },
          { matchId: 'm-reported-2', platformStatus: 'PLAYED', homeGamesWon: 2, awayGamesWon: 3, winnerTeamName: 'Away m-reported-2' },
          { matchId: 'm-overdue', platformStatus: 'SCHEDULED', homeGamesWon: null, awayGamesWon: null, winnerTeamName: null },
        ],
  }
}
