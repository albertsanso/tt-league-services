import { act, render, screen } from '@testing-library/react'
import { ApiError } from '../api/ApiError'
import {
  makeCorrections,
  makeDaily,
  makePending,
  makeRunOutcomes,
  makeSourceHealth,
  makeTimeToReport,
  makeUnitOutcomes,
} from '../test/statisticsFixtures'
import { TestApiProvider } from '../test/TestApiProvider'
import { useStatistics } from './useStatistics'
import type { StatisticsQuery } from './useStatistics'

function Harness({ query }: { query: StatisticsQuery | null }) {
  const { data, loading, error, reload } = useStatistics(query)
  return (
    <div>
      <div data-testid="loading">{String(loading)}</div>
      <div data-testid="error">{error ?? ''}</div>
      <div data-testid="zone">{data?.daily.zone ?? ''}</div>
      <div data-testid="ttr">{data?.timeToReport === null ? 'none' : (data?.timeToReport.season ?? '')}</div>
      <button onClick={reload}>reload</button>
    </div>
  )
}

function makeApi() {
  return {
    getDailyStats: vi.fn(async () => makeDaily()),
    getRunOutcomes: vi.fn(async () => makeRunOutcomes()),
    getUnitOutcomes: vi.fn(async () => makeUnitOutcomes()),
    getTimeToReport: vi.fn(async () => makeTimeToReport()),
    getPendingByAge: vi.fn(async () => makePending()),
    getCorrections: vi.fn(async () => makeCorrections()),
    getSourceHealth: vi.fn(async () => makeSourceHealth()),
  }
}

type StatisticsApi = ReturnType<typeof makeApi>

function view(api: StatisticsApi, query: StatisticsQuery | null) {
  return (
    <TestApiProvider api={{ statistics: api as never }}>
      <Harness query={query} />
    </TestApiProvider>
  )
}

const QUERY: StatisticsQuery = {
  sources: ['FCTT'],
  season: '2026-2027',
  unitKey: null,
  from: '2026-09-06',
  to: '2026-10-05',
}

async function flush() {
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
  })
}

describe('useStatistics', () => {
  it('loads every endpoint in parallel with the filters', async () => {
    const api = makeApi()
    render(view(api, QUERY))
    await flush()

    expect(screen.getByTestId('zone')).toHaveTextContent('Europe/Madrid')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
    const range = { from: '2026-09-06', to: '2026-10-05', source: ['FCTT'] }
    expect(api.getDailyStats).toHaveBeenCalledWith(range, expect.any(AbortSignal))
    expect(api.getRunOutcomes).toHaveBeenCalledWith({ ...range, unitKey: null }, expect.any(AbortSignal))
    expect(api.getUnitOutcomes).toHaveBeenCalledWith(range, expect.any(AbortSignal))
    expect(api.getCorrections).toHaveBeenCalledWith(range, expect.any(AbortSignal))
    expect(api.getSourceHealth).toHaveBeenCalledWith(range, expect.any(AbortSignal))
    expect(api.getTimeToReport).toHaveBeenCalledWith({ season: '2026-2027', source: ['FCTT'] }, expect.any(AbortSignal))
    expect(api.getPendingByAge).toHaveBeenCalledWith({ season: '2026-2027', source: ['FCTT'] }, expect.any(AbortSignal))
  })

  it('limits the run outcomes to the unit but still lists every unit', async () => {
    const api = makeApi()
    render(view(api, { ...QUERY, unitKey: 'season' }))
    await flush()

    const range = { from: '2026-09-06', to: '2026-10-05', source: ['FCTT'] }
    expect(api.getRunOutcomes).toHaveBeenCalledWith({ ...range, unitKey: 'season' }, expect.any(AbortSignal))
    expect(api.getUnitOutcomes).toHaveBeenCalledWith(range, expect.any(AbortSignal))
  })

  it('starts every request before any of them settles', async () => {
    const api = makeApi()
    api.getDailyStats.mockImplementation(() => new Promise(() => undefined))
    render(view(api, QUERY))
    await flush()

    expect(screen.getByTestId('loading')).toHaveTextContent('true')
    for (const call of Object.values(api)) {
      expect(call).toHaveBeenCalledTimes(1)
    }
  })

  it('skips time to report without a season and counts every season as pending', async () => {
    const api = makeApi()
    render(view(api, { ...QUERY, season: null }))
    await flush()

    expect(api.getTimeToReport).not.toHaveBeenCalled()
    expect(api.getPendingByAge).toHaveBeenCalledWith({ season: undefined, source: ['FCTT'] }, expect.any(AbortSignal))
    expect(screen.getByTestId('ttr')).toHaveTextContent('none')
  })

  it('sends nothing for a null query', async () => {
    const api = makeApi()
    render(view(api, null))
    await flush()

    expect(api.getDailyStats).not.toHaveBeenCalled()
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
  })

  it('aborts the requests in flight when the filters change and reloads', async () => {
    const api = makeApi()
    const signals: AbortSignal[] = []
    api.getDailyStats.mockImplementation(((_range: unknown, signal: AbortSignal) => {
      signals.push(signal)
      return new Promise(() => undefined)
    }) as never)
    const { rerender } = render(view(api, QUERY))
    await flush()

    rerender(view(api, { ...QUERY, sources: ['RFETM'] }))
    await flush()

    expect(signals).toHaveLength(2)
    expect(signals[0].aborted).toBe(true)
    expect(signals[1].aborted).toBe(false)
    expect(api.getDailyStats).toHaveBeenLastCalledWith(
      { from: '2026-09-06', to: '2026-10-05', source: ['RFETM'] },
      signals[1],
    )
  })

  it('reports a failure and keeps no data', async () => {
    const api = makeApi()
    api.getSourceHealth.mockRejectedValue(new ApiError(500, null, 'Server exploded'))
    render(view(api, QUERY))
    await flush()

    expect(screen.getByTestId('error')).toHaveTextContent('Server exploded')
    expect(screen.getByTestId('zone')).toHaveTextContent('')
  })

  it('reloads on demand', async () => {
    const api = makeApi()
    render(view(api, QUERY))
    await flush()

    await act(async () => {
      screen.getByRole('button', { name: 'reload' }).click()
    })
    await flush()

    expect(api.getDailyStats).toHaveBeenCalledTimes(2)
  })
})
