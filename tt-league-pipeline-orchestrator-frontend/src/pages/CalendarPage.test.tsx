import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import type { Api } from '../api/bindApi'
import type { MatchDayListQuery } from '../api/matchDays'
import type { MatchDaySummary, Page } from '../api/types'
import { AuthContext } from '../auth/authContext'
import type { AuthContextValue } from '../auth/authContext'
import { createEventBus } from '../test/eventBus'
import { FakeEvents } from '../test/FakeEvents'
import { makeFacets, makeSummary, makeSummaryPage, summariesForEveryCompletion } from '../test/matchDayFixtures'
import { TestApiProvider } from '../test/TestApiProvider'
import CalendarPage from './CalendarPage'

function Location() {
  const location = useLocation()
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>
}

function DetailStub() {
  const location = useLocation()
  return <div data-testid="detail">{`detail ${JSON.stringify(location.state)}`}</div>
}

type ListFn = (query: MatchDayListQuery, signal?: AbortSignal) => Promise<Page<MatchDaySummary>>

interface Setup {
  dated?: MatchDaySummary[]
  undated?: MatchDaySummary[]
  list?: ReturnType<typeof vi.fn<ListFn>>
  path?: string
}

function setup(options: Setup = {}) {
  const bus = createEventBus()
  const list =
    options.list ??
    vi.fn<ListFn>(async (query) =>
      query.undated ? makeSummaryPage(options.undated ?? []) : makeSummaryPage(options.dated ?? summariesForEveryCompletion()),
    )
  const facets = vi.fn(async () => makeFacets())
  const auth: AuthContextValue = {
    status: 'signed-in',
    token: 't',
    user: { username: 'u', roles: [], permissions: [], expiresAt: Date.now() + 60_000 },
    signIn: async () => undefined,
    signOut: () => undefined,
    getToken: () => 't',
  }
  const api = { matchDays: { listMatchDays: list, getMatchDayFacets: facets } } as unknown as Partial<Api>
  render(
    <MemoryRouter initialEntries={[options.path ?? '/calendar?date=2026-10-05']}>
      <AuthContext.Provider value={auth}>
        <FakeEvents bus={bus}>
          <TestApiProvider api={api}>
            <Routes>
              <Route path="/calendar" element={<CalendarPage />} />
              <Route path="/calendar/match-days/:matchDayId" element={<DetailStub />} />
            </Routes>
            <Location />
          </TestApiProvider>
        </FakeEvents>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
  return { bus, list, facets, user: userEvent.setup() }
}

const datedQueries = (list: ReturnType<typeof vi.fn<ListFn>>) => list.mock.calls.map(([query]) => query).filter((query) => !query.undated)

afterEach(() => {
  vi.useRealTimers()
})

describe('CalendarPage', () => {
  it('asks for the visible month and the filters of the URL, and never preselects a filter', async () => {
    const { list } = setup({
      path: '/calendar?date=2026-10-05&source=FCTT&season=2026-2027&competition=TERCERA-masculino&phase=1a%20Fase&state=OPEN',
    })

    await screen.findByRole('grid', { name: 'Month calendar' })
    expect(datedQueries(list)[0]).toMatchObject({
      source: 'FCTT',
      season: '2026-2027',
      competition: 'TERCERA-masculino',
      phase: '1a Fase',
      state: 'OPEN',
      from: '2026-09-28',
      to: '2026-11-01',
      page: 0,
      size: 200,
    })
  })

  it('opens without any data filter when the URL has none', async () => {
    const { list } = setup()

    await screen.findByRole('grid', { name: 'Month calendar' })
    const query = datedQueries(list)[0]
    expect(query.source).toBeUndefined()
    expect(query.season).toBeUndefined()
    expect(query.competition).toBeUndefined()
    expect(query.phase).toBeUndefined()
    expect(query.state).toBeUndefined()
  })

  it('asks for the visible week in the week view', async () => {
    const { list } = setup({ path: '/calendar?view=week&date=2026-10-07' })

    await screen.findByRole('grid', { name: 'Week calendar' })
    expect(datedQueries(list)[0]).toMatchObject({ from: '2026-10-05', to: '2026-10-11' })
  })

  it('shows every entry with its label, reported over total and completion', async () => {
    setup()

    const complete = await screen.findByRole('link', { name: /J1, All reported, closed, 6 \/ 6 reported/ })
    expect(complete).toHaveTextContent('TERCERA-masculino · G2 · 1a Fase · J1')
    expect(complete).toHaveTextContent('6 / 6')
    expect(screen.getByRole('link', { name: /J2, In progress, open, 4 \/ 6 reported/ })).toHaveTextContent('4 / 6')
    expect(screen.getByRole('link', { name: /J3, Has overdue, open, 3 \/ 5 reported, 1 postponed, 1 ignored/ })).toHaveTextContent(
      '3 / 5',
    )
    expect(screen.getByRole('link', { name: /J4, Future, upcoming, 0 \/ 6 reported/ })).toHaveTextContent('0 / 6')
  })

  it('places each entry on its first date', async () => {
    setup()

    const cell = await screen.findByRole('gridcell', { name: 'Sunday 4 October 2026' })
    expect(within(cell).getByRole('link', { name: /J2/ })).toBeInTheDocument()
    expect(within(cell).queryByRole('link', { name: /J1/ })).not.toBeInTheDocument()
  })

  it('shows the legend with the four completion labels', async () => {
    setup()

    const legend = await screen.findByRole('list', { name: 'Completion legend' })
    expect(within(legend).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'All reported',
      'In progress',
      'Has overdue',
      'Future',
    ])
  })

  it('shows at most four entries per day and lists the rest in a dialog', async () => {
    const six = Array.from({ length: 6 }, (_, index) =>
      makeSummary(`d${index}`, { firstDate: '2026-10-06', lastDate: '2026-10-06', round: index + 1 }),
    )
    const { user } = setup({ dated: six })

    const cell = await screen.findByRole('gridcell', { name: 'Tuesday 6 October 2026' })
    expect(within(cell).getAllByRole('link')).toHaveLength(4)
    await user.click(within(cell).getByRole('button', { name: '+2 more' }))

    const dialog = await screen.findByRole('dialog', { name: 'Tuesday 6 October 2026' })
    expect(within(dialog).getAllByRole('link')).toHaveLength(6)
  })

  it('lists every entry of a day in the week view', async () => {
    const six = Array.from({ length: 6 }, (_, index) =>
      makeSummary(`d${index}`, { firstDate: '2026-10-06', lastDate: '2026-10-06', round: index + 1 }),
    )
    setup({ dated: six, path: '/calendar?view=week&date=2026-10-05' })

    const cell = await screen.findByRole('gridcell', { name: 'Tuesday 6 October 2026' })
    expect(within(cell).getAllByRole('link')).toHaveLength(6)
    expect(screen.queryByRole('button', { name: /more/ })).not.toBeInTheDocument()
  })

  it('moves to the previous and next period and keeps the view', async () => {
    const { user, list } = setup({ path: '/calendar?view=week&date=2026-10-05' })
    await screen.findByRole('grid', { name: 'Week calendar' })

    await user.click(screen.getByRole('button', { name: 'Next week' }))
    expect(screen.getByTestId('location')).toHaveTextContent('view=week&date=2026-10-12')
    await user.click(screen.getByRole('button', { name: 'Previous week' }))
    await user.click(screen.getByRole('button', { name: 'Previous week' }))
    expect(screen.getByTestId('location')).toHaveTextContent('view=week&date=2026-09-28')
    expect(await screen.findByRole('heading', { name: '28 Sep – 4 Oct 2026' })).toBeInTheDocument()
    expect(datedQueries(list).some((query) => query.from === '2026-09-28' && query.to === '2026-10-04')).toBe(true)
  })

  it('moves by months and switches between month and week', async () => {
    const { user } = setup()
    await screen.findByRole('grid', { name: 'Month calendar' })
    expect(screen.getByRole('heading', { name: 'October 2026' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Next month' }))
    expect(screen.getByTestId('location')).toHaveTextContent('date=2026-11-05')
    expect(screen.getByRole('heading', { name: 'November 2026' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Week' }))
    expect(screen.getByTestId('location')).toHaveTextContent('view=week&date=2026-11-05')
    expect(await screen.findByRole('grid', { name: 'Week calendar' })).toBeInTheDocument()
  })

  it('goes back to the current date with Today', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 11, 24, 12, 0))
    const { user } = setup({ path: '/calendar?date=2026-03-01' })
    await screen.findByRole('grid', { name: 'Month calendar' })

    await user.click(screen.getByRole('button', { name: 'Today' }))

    expect(screen.getByTestId('location')).toHaveTextContent('date=2026-12-24')
    expect(await screen.findByRole('heading', { name: 'December 2026' })).toBeInTheDocument()
  })

  it('writes a changed filter to the URL and keeps the view and date', async () => {
    const { user, list } = setup({ path: '/calendar?view=week&date=2026-10-07' })
    await screen.findByRole('grid', { name: 'Week calendar' })

    await user.click(screen.getByRole('combobox', { name: 'Source' }))
    await user.click(await screen.findByRole('option', { name: 'FCTT' }))
    expect(screen.getByTestId('location')).toHaveTextContent('/calendar?view=week&date=2026-10-07&source=FCTT')

    await user.click(screen.getByRole('combobox', { name: 'Category' }))
    await user.click(await screen.findByRole('option', { name: 'TERCERA-masculino' }))
    expect(screen.getByTestId('location')).toHaveTextContent('competition=TERCERA-masculino')

    await user.click(screen.getByRole('combobox', { name: 'Phase' }))
    await user.click(await screen.findByRole('option', { name: '2a Fase' }))
    await user.click(screen.getByRole('combobox', { name: 'State' }))
    await user.click(await screen.findByRole('option', { name: 'Open' }))
    expect(screen.getByTestId('location')).toHaveTextContent('phase=2a+Fase')
    expect(screen.getByTestId('location')).toHaveTextContent('state=OPEN')

    await vi.waitFor(() =>
      expect(datedQueries(list).at(-1)).toMatchObject({
        source: 'FCTT',
        competition: 'TERCERA-masculino',
        phase: '2a Fase',
        state: 'OPEN',
        from: '2026-10-05',
      }),
    )
  })

  it('clears the data filters but keeps the period', async () => {
    const { user } = setup({ path: '/calendar?view=week&date=2026-10-07&source=FCTT&state=OPEN' })
    await screen.findByRole('grid', { name: 'Week calendar' })

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/calendar?view=week&date=2026-10-07')
    expect(screen.getByTestId('location')).not.toHaveTextContent('source=')
  })

  it('lists the undated match days under the calendar and hides the list when there are none', async () => {
    const undated = [makeSummary('u1', { firstDate: null, lastDate: null, state: 'UPCOMING', completion: 'FUTURE' })]
    const first = setup({ dated: [], undated })

    const toggle = await screen.findByRole('button', { name: 'Undated match days (1)' })
    expect(screen.queryByRole('link', { name: /Future, upcoming/ })).not.toBeInTheDocument()
    await first.user.click(toggle)
    expect(await screen.findByRole('link', { name: /Future, upcoming/ })).toBeInTheDocument()
  })

  it('has no undated section when there are no undated match days', async () => {
    setup()

    await screen.findByRole('grid', { name: 'Month calendar' })
    expect(screen.queryByRole('button', { name: /Undated match days/ })).not.toBeInTheDocument()
  })

  it('refetches when a match-days event of the filtered source arrives', async () => {
    const { bus, list } = setup({ path: '/calendar?date=2026-10-05&source=FCTT' })
    await screen.findByRole('grid', { name: 'Month calendar' })
    const before = datedQueries(list).length

    await bus.emit({ type: 'match-days', payload: { source: 'FCTT', season: '2026-2027', matchDayId: null, cause: 'RECOMPUTED' } })

    await vi.waitFor(() => expect(datedQueries(list).length).toBe(before + 1), { timeout: 3000 })
  })

  it('navigates to the detail with the calendar search as the way back', async () => {
    const { user } = setup({ path: '/calendar?date=2026-10-05&source=FCTT' })

    await user.click(await screen.findByRole('link', { name: /J2, In progress/ }))

    expect(screen.getByTestId('location')).toHaveTextContent('/calendar/match-days/progress')
    expect(screen.getByTestId('detail')).toHaveTextContent('{"backTo":"?date=2026-10-05&source=FCTT"}')
  })

  it('shows a load error with Retry', async () => {
    const list = vi.fn<ListFn>().mockRejectedValueOnce(new Error('Cannot reach the server'))
    list.mockImplementation(async (query) => makeSummaryPage(query.undated ? [] : summariesForEveryCompletion()))
    const { user } = setup({ list })

    expect(await screen.findByText('Cannot reach the server')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByRole('link', { name: /J2, In progress/ })).toBeInTheDocument()
    expect(screen.queryByText('Cannot reach the server')).not.toBeInTheDocument()
  })

  it('says so when the period has no match days', async () => {
    setup({ dated: [] })

    expect(await screen.findByText('No tracked match days in this period.')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Clear filters' })).toHaveLength(1)
  })

  it('offers Clear filters in the empty state when a filter is set', async () => {
    setup({ dated: [], path: '/calendar?date=2026-10-05&source=RFETM' })

    expect(await screen.findByText('No tracked match days in this period.')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Clear filters' })).toHaveLength(2)
  })

  it('warns about invalid URL values without replacing them', async () => {
    setup({ path: '/calendar?date=2026-10-05&source=NOPE&view=year' })

    expect(await screen.findByText(/Unknown source "NOPE" was ignored/)).toBeInTheDocument()
    expect(screen.getByText(/Unknown view "year" was ignored/)).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('source=NOPE')
  })

  it('warns when the period holds more match days than were loaded', async () => {
    const list = vi.fn<ListFn>(async (query) => {
      const page = query.page ?? 0
      return query.undated
        ? makeSummaryPage([])
        : { items: [makeSummary(`d${page}`)], page, size: 200, totalItems: 5000, totalPages: 25 }
    })
    setup({ list })

    expect(await screen.findByText('More than 2000 match days — narrow the filters')).toBeInTheDocument()
  })

  it('does not keep the page busy after loading', async () => {
    setup()

    await screen.findByRole('link', { name: /J2/ })
    await act(async () => {
      await Promise.resolve()
    })
    expect(screen.queryByRole('progressbar', { name: 'Loading match days' })).not.toBeInTheDocument()
  })
})
