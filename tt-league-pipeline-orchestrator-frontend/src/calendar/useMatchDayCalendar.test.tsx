import { act, render, screen } from '@testing-library/react'
import type { MatchDayListQuery } from '../api/matchDays'
import type { MatchDaySummary, MatchDaysEvent, Page } from '../api/types'
import { createEventBus } from '../test/eventBus'
import { FakeEvents } from '../test/FakeEvents'
import { makeSummary, makeSummaryPage } from '../test/matchDayFixtures'
import { TestApiProvider } from '../test/TestApiProvider'
import { parseCalendarFilters } from './calendarFilters'
import type { CalendarFilterValues } from './calendarFilters'
import { LIVE_DEBOUNCE_MS, MAX_PAGES, useMatchDayCalendar } from './useMatchDayCalendar'

function filtersFor(search: string): CalendarFilterValues {
  const { errors, ...values } = parseCalendarFilters(new URLSearchParams(search), '2026-10-05')
  void errors
  return values
}

function Harness({ filters }: { filters: CalendarFilterValues }) {
  const { days, undated, loading, error, truncated, refetch } = useMatchDayCalendar(filters)
  return (
    <div>
      <div data-testid="loading">{String(loading)}</div>
      <div data-testid="error">{error ?? ''}</div>
      <div data-testid="truncated">{String(truncated)}</div>
      <div data-testid="days">{days.map((day) => day.id).join(',')}</div>
      <div data-testid="undated">{undated.map((day) => day.id).join(',')}</div>
      <button onClick={refetch}>refetch</button>
    </div>
  )
}

type ListFn = (query: MatchDayListQuery, signal: AbortSignal) => Promise<Page<MatchDaySummary>>

function setup(listMatchDays: ReturnType<typeof vi.fn<ListFn>>, filters: CalendarFilterValues) {
  const bus = createEventBus()
  const view = (f: CalendarFilterValues) => (
    <FakeEvents bus={bus}>
      <TestApiProvider api={{ matchDays: { listMatchDays } as never }}>
        <Harness filters={f} />
      </TestApiProvider>
    </FakeEvents>
  )
  const { rerender } = render(view(filters))
  return { bus, rerender: (f: CalendarFilterValues) => rerender(view(f)) }
}

async function flush() {
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
  })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

const matchDaysEvent = (overrides: Partial<MatchDaysEvent> = {}) =>
  ({
    type: 'match-days',
    payload: { source: 'FCTT', season: '2026-2027', matchDayId: null, cause: 'RECOMPUTED', ...overrides },
  }) as const

/** A fake backend: `pages` dated pages of 200 and an undated page. */
function backend(pages: number, undated: MatchDaySummary[] = []) {
  return vi.fn<ListFn>(async (query) => {
    if (query.undated) {
      return makeSummaryPage(undated)
    }
    const page = query.page ?? 0
    return {
      items: [makeSummary(`d${page}`)],
      page,
      size: 200,
      totalItems: pages * 200,
      totalPages: pages,
    }
  })
}

afterEach(() => {
  vi.useRealTimers()
})

describe('useMatchDayCalendar', () => {
  it('loads the visible range with the filters, then the undated match days', async () => {
    const list = backend(1, [makeSummary('u1', { firstDate: null, lastDate: null })])
    setup(list, filtersFor('date=2026-10-05&source=FCTT&season=2026-2027&competition=TERCERA-masculino'))

    expect(await screen.findByText('d0')).toBeInTheDocument()
    expect(screen.getByTestId('undated')).toHaveTextContent('u1')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
    expect(list).toHaveBeenCalledTimes(2)
    expect(list.mock.calls[0][0]).toMatchObject({
      source: 'FCTT',
      season: '2026-2027',
      competition: 'TERCERA-masculino',
      from: '2026-09-28',
      to: '2026-11-01',
      page: 0,
      size: 200,
    })
    expect(list.mock.calls[1][0]).toMatchObject({ undated: true, source: 'FCTT' })
    expect(list.mock.calls[1][0].from).toBeUndefined()
  })

  it('loads every page until the total is reached', async () => {
    const list = backend(3)
    setup(list, filtersFor(''))

    await screen.findByText('d0,d1,d2')
    expect(list.mock.calls.filter(([query]) => !query.undated).map(([query]) => query.page)).toEqual([0, 1, 2])
    expect(screen.getByTestId('truncated')).toHaveTextContent('false')
  })

  it('stops after ten pages and reports the truncation', async () => {
    const list = backend(MAX_PAGES + 4)
    setup(list, filtersFor(''))

    await screen.findByText(/d9$/)
    expect(list.mock.calls.filter(([query]) => !query.undated)).toHaveLength(MAX_PAGES)
    expect(screen.getByTestId('truncated')).toHaveTextContent('true')
  })

  it('aborts the request in flight when the filters change', async () => {
    const signals: AbortSignal[] = []
    const first = deferred<Page<MatchDaySummary>>()
    const list = vi.fn<ListFn>((query, signal) => {
      signals.push(signal)
      if (query.undated) {
        return Promise.resolve(makeSummaryPage([]))
      }
      return query.from === '2026-09-28' ? first.promise : Promise.resolve(makeSummaryPage([makeSummary('week')]))
    })
    const { rerender } = setup(list, filtersFor('date=2026-10-05'))

    rerender(filtersFor('view=week&date=2026-10-05'))
    expect(await screen.findByText('week')).toBeInTheDocument()
    expect(signals[0].aborted).toBe(true)

    await act(async () => {
      first.resolve(makeSummaryPage([makeSummary('late')]))
    })
    expect(screen.queryByText('late')).not.toBeInTheDocument()
  })

  it('shows the failure and keeps the previous result when a refetch fails', async () => {
    const list = backend(1)
    setup(list, filtersFor(''))
    await screen.findByText('d0')

    list.mockRejectedValueOnce(new Error('Cannot reach the server'))
    await act(async () => {
      screen.getByRole('button', { name: 'refetch' }).click()
    })

    expect(await screen.findByText('Cannot reach the server')).toBeInTheDocument()
    expect(screen.getByTestId('days')).toHaveTextContent('d0')
  })

  describe('live updates', () => {
    async function ready(filters: CalendarFilterValues) {
      vi.useFakeTimers()
      const list = backend(1)
      const view = setup(list, filters)
      await flush()
      await flush()
      expect(screen.getByTestId('days')).toHaveTextContent('d0')
      list.mockClear()
      return { ...view, list }
    }

    it('refetches once, debounced, for an event of the filtered source and season', async () => {
      const { bus, list } = await ready(filtersFor('source=FCTT&season=2026-2027'))

      await bus.emit(matchDaysEvent())
      await bus.emit(matchDaysEvent({ matchDayId: 'x', cause: 'ACTION' }))
      await act(async () => {
        await vi.advanceTimersByTimeAsync(LIVE_DEBOUNCE_MS - 1)
      })
      expect(list).not.toHaveBeenCalled()

      await act(async () => {
        await vi.advanceTimersByTimeAsync(2)
      })
      await flush()
      expect(list.mock.calls.filter(([query]) => !query.undated)).toHaveLength(1)
    })

    it('ignores events of another source or season', async () => {
      const { bus, list } = await ready(filtersFor('source=FCTT&season=2026-2027'))

      await bus.emit(matchDaysEvent({ source: 'RFETM' }))
      await bus.emit(matchDaysEvent({ season: '2025-2026' }))
      await act(async () => {
        await vi.advanceTimersByTimeAsync(LIVE_DEBOUNCE_MS * 2)
      })

      expect(list).not.toHaveBeenCalled()
    })

    it('reacts to every source and season when the filters leave them open', async () => {
      const { bus, list } = await ready(filtersFor(''))

      await bus.emit(matchDaysEvent({ source: 'BCNESA', season: '2025-2026' }))
      await act(async () => {
        await vi.advanceTimersByTimeAsync(LIVE_DEBOUNCE_MS + 1)
      })
      await flush()

      expect(list.mock.calls.filter(([query]) => !query.undated)).toHaveLength(1)
    })

    it('refetches at once after a reconnect', async () => {
      const { bus, list } = await ready(filtersFor(''))

      await bus.emit({ type: 'reconnected' })
      await flush()

      expect(list.mock.calls.filter(([query]) => !query.undated)).toHaveLength(1)
    })

    it('runs one follow-up request when an event arrives during a request', async () => {
      const { bus, list } = await ready(filtersFor(''))
      const slow = deferred<Page<MatchDaySummary>>()
      list.mockImplementationOnce(() => slow.promise)

      await bus.emit({ type: 'reconnected' })
      await bus.emit({ type: 'reconnected' })
      await bus.emit({ type: 'reconnected' })
      await act(async () => {
        slow.resolve({ items: [makeSummary('stale')], page: 0, size: 200, totalItems: 1, totalPages: 1 })
      })
      await flush()
      await flush()

      expect(list.mock.calls.filter(([query]) => !query.undated)).toHaveLength(2)
      expect(screen.getByTestId('days')).toHaveTextContent('d0')
      expect(screen.getByTestId('days')).not.toHaveTextContent('stale')
    })
  })
})
