import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'
import { ApiProvider } from '../api/ApiProvider'
import { AuthProvider } from '../auth/AuthProvider'
import { writeToken } from '../auth/tokenStorage'
import { json, stubFetch } from './fakeFetch'
import type { FakeFetch } from './fakeFetch'
import { makeToken } from './jwt'
import type { TestClaims } from './jwt'
import { makeFacets, makeMatchDayDetail, makeResults, makeSummaryPage, summariesForEveryCompletion } from './matchDayFixtures'
import { makeDetail, makePage, makeRun } from './runFixtures'
import { statisticsResponse } from './statisticsFixtures'
import type { StatisticsOverrides } from './statisticsFixtures'

export const runPage = makePage([makeRun('run-1')])

export interface AppFakeOptions {
  /** Claims of the token returned by the platform login endpoint. */
  loginClaims?: TestClaims
  loginStatus?: number
  runs?: unknown
  statistics?: StatisticsOverrides
  /** Statistics answers wait for this promise, to observe the loading state. */
  gateStatistics?: Promise<unknown>
}

/** Fake platform + orchestrator: login, me, logout, an idle event stream and the runs list. */
export function stubBackends(options: AppFakeOptions = {}): FakeFetch {
  return stubFetch((request) => {
    if (request.url.endsWith('/api/v1/auth/login')) {
      if (options.loginStatus && options.loginStatus !== 200) {
        return json({ message: 'Invalid username or password' }, options.loginStatus)
      }
      return json({ token: makeToken(options.loginClaims), type: 'Bearer', username: 'operator' })
    }
    if (request.url.endsWith('/api/v1/auth/me')) {
      return json({ id: '1', username: 'operator', roles: [], permissions: [] })
    }
    if (request.url.endsWith('/api/v1/auth/logout')) {
      return json({})
    }
    if (request.url.endsWith('/api/pipeline/events')) {
      return new Response(new ReadableStream<Uint8Array>({ start() {} }), {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' },
      })
    }
    if (request.url.startsWith('/api/pipeline/match-days/facets')) {
      return json(makeFacets())
    }
    if (/^\/api\/pipeline\/match-days\/[^/?]+\/results/.test(request.url)) {
      return json(makeResults())
    }
    if (/^\/api\/pipeline\/match-days\/[^/?]+$/.test(request.url)) {
      return json(makeMatchDayDetail())
    }
    if (request.url.startsWith('/api/pipeline/match-days')) {
      return json(makeSummaryPage(request.url.includes('undated=true') ? [] : summariesForEveryCompletion()))
    }
    const statistics = statisticsResponse(request.url, options.statistics)
    if (statistics !== null) {
      return options.gateStatistics === undefined ? statistics : options.gateStatistics.then(() => statistics)
    }
    if (request.url.startsWith('/api/pipeline/runs/')) {
      return json(makeDetail('run-1'))
    }
    if (request.url.startsWith('/api/pipeline/runs')) {
      return json(options.runs ?? runPage)
    }
    return json({ title: 'Not found' }, 404)
  })
}

export function storeSession(claims: TestClaims = {}): string {
  const token = makeToken(claims)
  writeToken(token)
  return token
}

export function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <ApiProvider>
          <App />
        </ApiProvider>
      </AuthProvider>
    </MemoryRouter>,
  )
}
