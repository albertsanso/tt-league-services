import { ApiError, triggerResults } from './ApiError'
import { currentUser, login, logout } from './auth'
import type { HttpClient } from './client'
import { createHttpClient } from './client'
import {
  addMatchDayNote,
  closeMatchDay,
  getMatchDay,
  getMatchDayFacets,
  getMatchDayResults,
  ignoreMatch,
  listMatchDays,
  refreshMatchDay,
  reopenMatchDay,
  unignoreMatch,
} from './matchDays'
import { listPendingTriggers } from './pendingTriggers'
import {
  deletePollingPolicy,
  getPollingPolicy,
  listPollingPolicies,
  listPollSchedules,
  replacePollingPolicy,
  resumePollSchedule,
} from './polling'
import { getRun, listRuns, triggerRun } from './runs'
import type { PollingPolicyRequest } from './types'

interface Call {
  method: string
  path: string
  query?: unknown
  body?: unknown
}

function recordingClient(): { client: HttpClient; calls: Call[] } {
  const calls: Call[] = []
  const client: HttpClient = {
    async request(method, path, options) {
      calls.push({ method, path, query: options?.query, body: options?.body })
      return undefined as never
    },
    async requestWithStatus(method, path, options) {
      calls.push({ method, path, query: options?.query, body: options?.body })
      return { status: 201, data: { results: [] } as never }
    },
  }
  return { client, calls }
}

const policyRequest: PollingPolicyRequest = {
  matchDay: 'PT1H',
  matchDayStartOffset: 'PT0S',
  dayAfter: 'PT2H',
  daysTwoToSeven: 'PT6H',
  open: 'PT12H',
  overdue: 'PT24H',
  overdueStopAfterDays: 14,
  fullRefresh: 'PT24H',
  noChangeThreshold: 3,
  version: 0,
}

const cases: ReadonlyArray<readonly [string, (c: HttpClient) => Promise<unknown>, Call]> = [
  ['login', (c) => login(c, 'u', 'p'), { method: 'POST', path: '/api/v1/auth/login', body: { username: 'u', password: 'p' } }],
  ['currentUser', (c) => currentUser(c), { method: 'GET', path: '/api/v1/auth/me' }],
  ['logout', (c) => logout(c), { method: 'POST', path: '/api/v1/auth/logout' }],
  [
    'listRuns',
    (c) => listRuns(c, { source: ['RFETM'], status: ['FAILED'], page: 2 }),
    { method: 'GET', path: '/api/pipeline/runs', query: { source: ['RFETM'], status: ['FAILED'], from: undefined, to: undefined, page: 2, size: 20 } },
  ],
  ['getRun', (c) => getRun(c, 'a/b'), { method: 'GET', path: '/api/pipeline/runs/a%2Fb' }],
  ['listPendingTriggers', (c) => listPendingTriggers(c), { method: 'GET', path: '/api/pipeline/pending-triggers' }],
  [
    'listMatchDays',
    (c) => listMatchDays(c, { source: 'FCTT', season: '2025-2026', state: 'OPEN' }),
    { method: 'GET', path: '/api/pipeline/match-days', query: { source: 'FCTT', season: '2025-2026', state: 'OPEN', competition: undefined, phase: undefined, undated: undefined, from: undefined, to: undefined, page: 0, size: 50 } },
  ],
  [
    'listMatchDays with the calendar filters',
    (c) => listMatchDays(c, { source: 'FCTT', season: '2025-2026', competition: 'TERCERA-masculino', phase: '1a Fase', from: '2026-10-01', to: '2026-10-31', size: 200 }),
    { method: 'GET', path: '/api/pipeline/match-days', query: { source: 'FCTT', season: '2025-2026', state: undefined, competition: 'TERCERA-masculino', phase: '1a Fase', undated: undefined, from: '2026-10-01', to: '2026-10-31', page: 0, size: 200 } },
  ],
  [
    'listMatchDays undated',
    (c) => listMatchDays(c, { undated: true }),
    { method: 'GET', path: '/api/pipeline/match-days', query: { source: undefined, season: undefined, state: undefined, competition: undefined, phase: undefined, undated: true, from: undefined, to: undefined, page: 0, size: 50 } },
  ],
  ['getMatchDay', (c) => getMatchDay(c, 'id1'), { method: 'GET', path: '/api/pipeline/match-days/id1' }],
  [
    'getMatchDayFacets',
    (c) => getMatchDayFacets(c, { source: 'FCTT', season: '2025-2026' }),
    { method: 'GET', path: '/api/pipeline/match-days/facets', query: { source: 'FCTT', season: '2025-2026' } },
  ],
  ['getMatchDayResults', (c) => getMatchDayResults(c, 'id/1'), { method: 'GET', path: '/api/pipeline/match-days/id%2F1/results' }],
  ['refreshMatchDay', (c) => refreshMatchDay(c, 'id1', true), { method: 'POST', path: '/api/pipeline/match-days/id1/refresh', body: { force: true } }],
  ['closeMatchDay', (c) => closeMatchDay(c, 'id1', 'n'), { method: 'POST', path: '/api/pipeline/match-days/id1/close', body: { note: 'n' } }],
  ['reopenMatchDay', (c) => reopenMatchDay(c, 'id1'), { method: 'POST', path: '/api/pipeline/match-days/id1/reopen', body: { note: undefined } }],
  ['ignoreMatch', (c) => ignoreMatch(c, 'id1', 'm1', 'x'), { method: 'PUT', path: '/api/pipeline/match-days/id1/matches/m1/ignore', body: { note: 'x' } }],
  ['unignoreMatch', (c) => unignoreMatch(c, 'id1', 'm1'), { method: 'DELETE', path: '/api/pipeline/match-days/id1/matches/m1/ignore', body: { note: undefined } }],
  ['addMatchDayNote', (c) => addMatchDayNote(c, 'id1', 'hello', 'm1'), { method: 'POST', path: '/api/pipeline/match-days/id1/notes', body: { text: 'hello', matchId: 'm1' } }],
  ['listPollingPolicies', (c) => listPollingPolicies(c), { method: 'GET', path: '/api/pipeline/polling/policies' }],
  ['getPollingPolicy', (c) => getPollingPolicy(c, 'RFETM'), { method: 'GET', path: '/api/pipeline/polling/policies/RFETM' }],
  ['replacePollingPolicy', (c) => replacePollingPolicy(c, 'BCNESA', policyRequest), { method: 'PUT', path: '/api/pipeline/polling/policies/BCNESA', body: policyRequest }],
  ['deletePollingPolicy', (c) => deletePollingPolicy(c, 'FCTT'), { method: 'DELETE', path: '/api/pipeline/polling/policies/FCTT' }],
  ['listPollSchedules', (c) => listPollSchedules(c, { source: 'RFETM', season: '2025-2026' }), { method: 'GET', path: '/api/pipeline/polling/schedules', query: { source: 'RFETM', season: '2025-2026' } }],
  ['resumePollSchedule', (c) => resumePollSchedule(c, 's1'), { method: 'POST', path: '/api/pipeline/polling/schedules/s1/resume' }],
]

describe('endpoint modules', () => {
  it.each(cases)('%s uses the documented method, path, query and body', async (_name, invoke, expected) => {
    const { client, calls } = recordingClient()
    await invoke(client)
    expect(calls).toHaveLength(1)
    expect(calls[0].method).toBe(expected.method)
    expect(calls[0].path).toBe(expected.path)
    expect(calls[0].query).toEqual(expected.query)
    expect(calls[0].body).toEqual(expected.body)
  })
})

describe('refreshMatchDay', () => {
  function clientFor(response: Response) {
    return createHttpClient({
      baseUrl: '',
      getToken: () => 't',
      fetch: (async () => response) as unknown as typeof fetch,
    })
  }

  it.each([201, 202])('returns status %i with the results', async (status) => {
    const body = { results: [{ source: 'FCTT', outcome: 'CREATED' }] }
    const outcome = await refreshMatchDay(
      clientFor(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })),
      'id1',
      false,
    )
    expect(outcome.status).toBe(status)
    expect(outcome.response.results[0].outcome).toBe('CREATED')
  })

  it.each([409, 422])('rejects with the per-source results on %i', async (status) => {
    const problem = { status, detail: 'nope', code: 'X', results: [{ source: 'FCTT', outcome: 'REJECTED', code: 'ACTIVE_RUN' }] }
    const error = await refreshMatchDay(
      clientFor(new Response(JSON.stringify(problem), { status, headers: { 'Content-Type': 'application/problem+json' } })),
      'id1',
      false,
    ).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(triggerResults(error)[0].code).toBe('ACTIVE_RUN')
  })
})

describe('triggerRun', () => {
  const request = { source: 'ALL', season: '2025-2026', scopeType: 'OPEN_MATCH_DAYS', force: false } as const

  function clientFor(response: Response) {
    return createHttpClient({
      baseUrl: '',
      getToken: () => 't',
      fetch: (async () => response) as unknown as typeof fetch,
    })
  }

  it.each([201, 202])('returns status %i with the results', async (status) => {
    const body = { results: [{ source: 'RFETM', outcome: 'CREATED' }] }
    const outcome = await triggerRun(
      clientFor(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })),
      request,
    )
    expect(outcome.status).toBe(status)
    expect(outcome.response.results).toHaveLength(1)
  })

  it('rejects with the per-source results on 409', async () => {
    const problem = { status: 409, detail: 'busy', results: [{ source: 'RFETM', outcome: 'REJECTED', code: 'RUN_ACTIVE' }] }
    const error = await triggerRun(
      clientFor(new Response(JSON.stringify(problem), { status: 409, headers: { 'Content-Type': 'application/problem+json' } })),
      request,
    ).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(triggerResults(error)[0].code).toBe('RUN_ACTIVE')
  })
})
