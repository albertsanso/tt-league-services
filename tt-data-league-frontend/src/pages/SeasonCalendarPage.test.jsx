import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SeasonCalendarPage from './SeasonCalendarPage.jsx'
import {
  clearMatchOverdueMark,
  getCalendarRange,
  getMatchOptions,
  getSeasonCalendar,
  markMatchOverdue,
} from '../api/matches.js'
import { useAuth } from '../context/useAuth.js'
import { AppStateProvider } from '../context/AppStateContext.jsx'

vi.mock('../context/useAuth.js', () => ({ useAuth: vi.fn() }))
vi.mock('../api/matches.js', () => ({
  getMatchOptions: vi.fn(),
  getSeasonCalendar: vi.fn(),
  getCalendarRange: vi.fn(),
  markMatchOverdue: vi.fn(),
  clearMatchOverdueMark: vi.fn(),
  searchMatches: vi.fn(),
  getMatchDetails: vi.fn(),
}))
// FullCalendar is covered by its own tests; here it is a plain list of clickable events.
vi.mock('../components/calendar/MatchCalendar.jsx', () => ({
  default: ({ view, date, events, visibleRange, onDateSelect, onMatchSelect }) => (
    <div data-testid="calendar" data-view={view} data-date={date} data-range={visibleRange
      ? `${visibleRange.firstDate}/${visibleRange.lastDate}` : ''}
    >
      {events.map((event) => (
        <button key={event.id} type="button" onClick={() => onMatchSelect(event.extendedProps.match)}>
          {`event ${event.title}`}
        </button>
      ))}
      <button type="button" onClick={() => onDateSelect('2026-09-19')}>day 2026-09-19</button>
    </div>
  ),
}))

const match = (overrides = {}) => ({
  id: 'match-1',
  dateTime: '2026-09-19T18:30:00+02:00',
  venue: 'Pavelló',
  city: 'Terrassa',
  homeTeamName: 'Club A',
  awayTeamName: 'Club B',
  homeTeamId: 'team-a',
  awayTeamId: 'team-b',
  homeGamesWon: null,
  awayGamesWon: null,
  status: 'SCHEDULED',
  calendarState: 'AWAITING_RESULT',
  overdueMarked: false,
  overdueMarkedAt: null,
  overdueMarkedBy: null,
  overdueMarkable: true,
  competition: 'tercera',
  groupNumber: 1,
  phase: '1a Fase',
  round: 1,
  ...overrides,
})

const rangeWith = (matches, facets = {}) => ({
  source: 'FCTT',
  season: '2026-2027',
  from: '2026-09-14',
  to: '2026-09-21',
  today: '2026-09-16',
  overdueGraceDays: 7,
  matches,
  facets: {
    competitions: ['tercera'],
    groups: [1],
    teams: [
      { teamId: 'team-a', name: 'Club A', competition: 'tercera' },
      { teamId: 'team-b', name: 'Club B', competition: 'tercera' },
    ],
    ...facets,
  },
})

const jornadaCalendar = () => ({
  source: 'FCTT',
  season: '2026-2027',
  competition: 'tercera',
  today: '2026-09-16',
  overdueGraceDays: 7,
  groups: [{
    groupNumber: 1,
    phase: '1a Fase',
    currentRound: 2,
    lastCompleteRound: 1,
    scheduledMatches: 2,
    playedMatches: 2,
    overdueMatches: 0,
    postponedMatches: 0,
    rounds: [
      {
        round: 1,
        firstDate: '2026-09-05',
        lastDate: '2026-09-05',
        scheduledMatches: 0,
        playedMatches: 1,
        complete: true,
        current: false,
        matches: [match({ id: 'r1', dateTime: '2026-09-05T18:00:00+02:00', status: 'PLAYED', calendarState: 'PLAYED', round: 1 })],
      },
      {
        round: 2,
        firstDate: '2026-09-19',
        lastDate: '2026-09-20',
        scheduledMatches: 2,
        playedMatches: 1,
        complete: false,
        current: true,
        matches: [
          match({ id: 'r2a', round: 2 }),
          match({ id: 'r2b', homeTeamName: 'Club C', awayTeamName: 'Club D', homeTeamId: 'team-c', awayTeamId: 'team-d', round: 2 }),
          match({ id: 'r2u', dateTime: null, calendarState: 'UNDATED', homeTeamName: 'Club E', awayTeamName: 'Club F', homeTeamId: 'team-e', awayTeamId: 'team-f', round: 2 }),
        ],
      },
    ],
  }],
})

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="search">{location.search}</output>
}

function renderPage(path = '/calendari?source=FCTT&season=2026-2027&date=2026-09-16') {
  return render(
    <AppStateProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/calendari" element={<><SeasonCalendarPage /><LocationProbe /></>} />
        </Routes>
      </MemoryRouter>
    </AppStateProvider>,
  )
}

const search = () => new URLSearchParams(screen.getByTestId('search').textContent)

// The week view starts with every group collapsed: open sources, then competitions, so the entries show.
const expandWeek = () => {
  for (let level = 0; level < 2; level += 1) {
    screen.queryAllByRole('button', { expanded: false })
      .filter((button) => button.closest('.week-grid'))
      .forEach((button) => fireEvent.click(button))
  }
}
const openWeekMatch = async (name) => {
  await screen.findByRole('button', { name: /^FCTT, \d+ partit/ })
  expandWeek()
  fireEvent.click(screen.getByRole('button', { name }))
  return screen.findByRole('dialog')
}
const waitForWeek = () => screen.findAllByRole('button', { name: /partit/ })

// Each source imports a different newest season; every source returns one match with its own team names.
function mockSources() {
  const seasonsBySource = {
    RFETM: { seasons: ['2025-2026'], competitions: ['primera'] },
    FCTT: { seasons: ['2026-2027'], competitions: ['tercera'] },
    BCNESA: { seasons: ['2026-2027'], competitions: ['tercera'] },
  }
  getMatchOptions.mockImplementation((source) => Promise.resolve(seasonsBySource[source]))
  getCalendarRange.mockImplementation(({ source }) => Promise.resolve(rangeWith([match({
    id: `match-${source}`,
    source,
    homeTeamName: source === 'FCTT' ? 'Club A' : `Club ${source}`,
    awayTeamName: 'Club B',
    homeTeamId: `team-${source}`,
    awayTeamId: 'team-b',
  })])))
}

describe('SeasonCalendarPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn(), hasPermission: () => false })
    getMatchOptions.mockResolvedValue({ seasons: ['2026-2027'], competitions: ['tercera', 'segunda'] })
    getCalendarRange.mockResolvedValue(rangeWith([match()]))
    getSeasonCalendar.mockResolvedValue(jornadaCalendar())
  })
  afterEach(cleanup)

  it('defaults to the week view of today and requests the computed range', async () => {
    const { container } = renderPage('/calendari?source=FCTT&season=2026-2027')

    await waitFor(() => expect(getCalendarRange).toHaveBeenCalled())
    const call = getCalendarRange.mock.calls[0][0]
    expect(call).toMatchObject({ source: 'FCTT', season: '2026-2027' })
    expect(new Date(call.to) - new Date(call.from)).toBe(7 * 86400000)
    expect(container.querySelector('.week-grid')).not.toBeNull()
    expect(screen.queryByTestId('calendar')).toBeNull()
    expect(screen.getByRole('button', { name: 'Setmana' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('range views call getCalendarRange with the range of each view', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16&view=week')
    await waitFor(() => expect(getCalendarRange).toHaveBeenCalledWith(
      expect.objectContaining({ from: '2026-09-14', to: '2026-09-21' }), 'token', expect.anything(), expect.any(Function),
    ))

    fireEvent.click(screen.getByRole('button', { name: 'Mes' }))
    await waitFor(() => expect(getCalendarRange).toHaveBeenCalledWith(
      expect.objectContaining({ from: '2026-08-31', to: '2026-10-05' }), 'token', expect.anything(), expect.any(Function),
    ))

    fireEvent.click(screen.getByRole('button', { name: 'Dia' }))
    await waitFor(() => expect(getCalendarRange).toHaveBeenCalledWith(
      expect.objectContaining({ from: '2026-09-16', to: '2026-09-17' }), 'token', expect.anything(), expect.any(Function),
    ))
    expect(search().get('view')).toBe('day')
    expect(search().get('date')).toBe('2026-09-16')
  })

  it('navigates to the previous and next period and back to today', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16&view=week')
    await screen.findByRole('button', { name: /^FCTT, 1 partit/ })

    fireEvent.click(screen.getByRole('button', { name: 'Període següent' }))
    await waitFor(() => expect(search().get('date')).toBe('2026-09-23'))
    fireEvent.click(screen.getByRole('button', { name: 'Període anterior' }))
    fireEvent.click(screen.getByRole('button', { name: 'Període anterior' }))
    await waitFor(() => expect(search().get('date')).toBe('2026-09-09'))
    fireEvent.click(screen.getByRole('button', { name: 'Avui' }))
    await waitFor(() => expect(search().has('date')).toBe(false))
  })

  it('keeps every filter in the URL and clears dependent ones', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')
    await waitForWeek()

    fireEvent.change(screen.getByLabelText('Competició'), { target: { value: 'tercera' } })
    await waitFor(() => expect(search().get('competition')).toBe('tercera'))
    fireEvent.change(screen.getByLabelText('Grup'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('Equip'), { target: { value: 'team-b' } })

    await waitFor(() => expect(getCalendarRange).toHaveBeenCalledWith(
      expect.objectContaining({ competition: 'tercera', group: '1', team: 'team-b' }),
      'token', expect.anything(), expect.any(Function),
    ))
    expect(search().get('group')).toBe('1')
    expect(search().get('team')).toBe('team-b')
    expect(screen.queryByLabelText('Ordenar per')).toBeNull()

    fireEvent.change(screen.getByLabelText('Competició'), { target: { value: 'segunda' } })
    await waitFor(() => expect(search().get('competition')).toBe('segunda'))
    expect(search().has('group')).toBe(false)
    expect(search().has('team')).toBe(false)
  })

  it('a week day header opens the day view of that date', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16&view=week')

    fireEvent.click(await screen.findByRole('button', { name: /^dissabte, 19 de setembre/ }))

    await waitFor(() => expect(search().get('view')).toBe('day'))
    expect(search().get('date')).toBe('2026-09-19')
    expect(search().has('selected')).toBe(false)
    expect(screen.getByTestId('calendar')).toHaveAttribute('data-view', 'day')
  })

  it('opens the match dialog from a week entry without changing the URL, and closing it returns focus to the entry', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16&view=week')
    const before = screen.getByTestId('search').textContent

    await screen.findByRole('button', { name: /^FCTT, 1 partit/ })
    expandWeek()
    const entry = screen.getByRole('button', { name: /^Club A – Club B, 18:30/ })
    entry.focus()
    fireEvent.click(entry)

    const dialog = await screen.findByRole('dialog', { name: 'Club A – Club B' })
    expect(within(dialog).getByRole('link', { name: 'Obre el resum del partit' }))
      .toHaveAttribute('href', expect.stringContaining('/partits/match-1?'))
    expect(screen.getByTestId('search').textContent).toBe(before)

    fireEvent.keyDown(document, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(entry).toHaveFocus()
    expect(screen.getByTestId('search').textContent).toBe(before)
  })

  it('jornada view asks for a competition first', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&view=jornada')

    expect(await screen.findByText('Selecciona una competició per veure les jornades.')).toBeInTheDocument()
    expect(getSeasonCalendar).not.toHaveBeenCalled()
    expect(getCalendarRange).not.toHaveBeenCalled()
  })

  it('jornada view defaults to the current round and navigates between rounds', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&competition=tercera&view=jornada')

    await screen.findByRole('button', { name: 'event Club C – Club D' })
    expect(getSeasonCalendar.mock.calls[0][0]).toMatchObject({ competition: 'tercera', round: '' })
    expect(screen.getByTestId('calendar')).toHaveAttribute('data-range', '2026-09-19/2026-09-20')
    const undated = screen.getByRole('region', { name: /Sense data/ })
    expect(within(undated).getByRole('button', { name: /^Club E – Club F/ })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Jornada anterior' }))
    await waitFor(() => expect(search().get('round')).toBe('1'))
    expect(await screen.findByRole('button', { name: 'event Club A – Club B' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'event Club C – Club D' })).toBeNull()
    expect(screen.queryByRole('region', { name: /Sense data/ })).toBeNull()
    expect(screen.getByRole('button', { name: 'Jornada anterior' })).toBeDisabled()
  })

  it('jornada view filters by team on the client', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&competition=tercera&view=jornada&team=team-c')

    await screen.findByRole('button', { name: 'event Club C – Club D' })
    expect(screen.queryByRole('button', { name: 'event Club A – Club B' })).toBeNull()
  })

  it('shows the group progress header in the jornada view', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&competition=tercera&view=jornada')

    expect(await screen.findByRole('progressbar', { name: 'Progrés de la jornada' })).toHaveAttribute('aria-valuetext', '2 / 4')
    expect(screen.getByText(/Jornada actual: 2/)).toBeInTheDocument()
  })

  it('offers the mark action only to matches:write users', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')

    await openWeekMatch(/^Club A – Club B/)
    expect(screen.queryByRole('button', { name: /Marcar com a endarrerit/ })).toBeNull()
  })

  it('marks a scheduled match and clears it from the dialog for a write user, refetching the range', async () => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn(), hasPermission: () => true })
    markMatchOverdue.mockResolvedValue(null)
    clearMatchOverdueMark.mockResolvedValue(null)
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')
    await openWeekMatch(/^Club A – Club B/)

    getCalendarRange.mockResolvedValue(rangeWith([match({
      overdueMarked: true, calendarState: 'OVERDUE', overdueMarkedBy: 'admin', overdueMarkedAt: '2026-09-08T10:00:00+02:00',
    })]))
    fireEvent.click(screen.getByRole('button', { name: /Marcar com a endarrerit/ }))
    await waitFor(() => expect(markMatchOverdue).toHaveBeenCalledWith(
      'match-1', 'token', undefined, expect.any(Function),
    ))

    // The open dialog shows the refreshed match.
    const dialog = screen.getByRole('dialog')
    expect(await within(dialog).findByText(/Endarrerit \(manual\) marcat per admin/)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: /Treure la marca d’endarrerit/ }))
    await waitFor(() => expect(clearMatchOverdueMark).toHaveBeenCalledWith(
      'match-1', 'token', undefined, expect.any(Function),
    ))
  })

  it('shows a conflict message when marking fails with 409', async () => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn(), hasPermission: () => true })
    markMatchOverdue.mockRejectedValue(Object.assign(new Error('conflict'), { status: 409 }))
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')
    await openWeekMatch(/^Club A – Club B/)

    fireEvent.click(screen.getByRole('button', { name: /Marcar com a endarrerit/ }))

    expect(await screen.findByText('Només es poden marcar els partits programats, a partir de l’endemà de la seva data.'))
      .toBeInTheDocument()
  })

  it('shows loading, empty and error states with a working retry', async () => {
    getCalendarRange.mockResolvedValueOnce(rangeWith([]))
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')

    expect(screen.getByText('Carregant...')).toBeInTheDocument()
    expect((await screen.findAllByText('No hi ha partits per a aquesta selecció.')).length).toBeGreaterThan(0)
    cleanup()

    getCalendarRange.mockRejectedValueOnce(Object.assign(new Error('nope'), { status: 500 }))
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')
    expect(await screen.findByText('No s’ha pogut carregar el calendari.')).toBeInTheDocument()
    cleanup()

    getCalendarRange.mockRejectedValueOnce(Object.assign(new Error('nope'), { status: 401 }))
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')
    expect(await screen.findByText('La sessió no permet consultar el calendari.')).toBeInTheDocument()
  })

  it('defaults to all sources and the newest season across them without writing it to the URL', async () => {
    mockSources()
    renderPage('/calendari?date=2026-09-16&view=week')

    await waitFor(() => expect(getCalendarRange).toHaveBeenCalledTimes(3))
    expect(getCalendarRange.mock.calls.map(([filters]) => filters.source).sort())
      .toEqual(['BCNESA', 'FCTT', 'RFETM'])
    getCalendarRange.mock.calls.forEach(([filters]) => expect(filters.season).toBe('2026-2027'))
    expect(getMatchOptions).toHaveBeenCalledWith('RFETM', '', 'token', expect.anything(), expect.any(Function))
    expect(getMatchOptions).toHaveBeenCalledWith('FCTT', '2026-2027', 'token', expect.anything(), expect.any(Function))
    expect(await screen.findByRole('button', { name: /^FCTT, 1 partit/ })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Font' })).toHaveValue('')
    expect(screen.getByRole('combobox', { name: 'Temporada' })).toHaveValue('2026-2027')
    expect(search().has('season')).toBe(false)
    expect(search().has('source')).toBe(false)
  })

  it('an explicit source requests only that source and defaults to its newest season', async () => {
    mockSources()
    renderPage('/calendari?source=FCTT&date=2026-09-16&view=week')

    await screen.findByRole('button', { name: /^FCTT, 1 partit/ })
    expect(getCalendarRange.mock.calls.map(([filters]) => filters.source)).toEqual(['FCTT'])
    expect(getCalendarRange.mock.calls[0][0].season).toBe('2026-2027')
    expect(getMatchOptions.mock.calls.map(([source]) => source)).toEqual(['FCTT', 'FCTT'])
  })

  it('an explicit season is respected across all sources', async () => {
    mockSources()
    renderPage('/calendari?season=2025-2026&date=2026-09-16&view=week')

    await waitFor(() => expect(getCalendarRange).toHaveBeenCalledTimes(3))
    getCalendarRange.mock.calls.forEach(([filters]) => expect(filters.season).toBe('2025-2026'))
    expect(screen.getByRole('combobox', { name: 'Temporada' })).toHaveValue('2025-2026')
  })

  it('selecting a source from the default narrows the requests to it', async () => {
    mockSources()
    renderPage('/calendari?date=2026-09-16&view=week')
    await waitFor(() => expect(getCalendarRange).toHaveBeenCalledTimes(3))
    getCalendarRange.mockClear()

    fireEvent.change(screen.getByRole('combobox', { name: 'Font' }), { target: { value: 'BCNESA' } })

    await waitFor(() => expect(search().get('source')).toBe('BCNESA'))
    await waitFor(() => expect(getCalendarRange).toHaveBeenCalled())
    expect(getCalendarRange.mock.calls.map(([filters]) => filters.source)).toEqual(['BCNESA'])
    expect(search().has('season')).toBe(false)
  })

  it('competition filters reach every source when all sources are selected', async () => {
    mockSources()
    renderPage('/calendari?date=2026-09-16&view=week')
    await waitFor(() => expect(getCalendarRange).toHaveBeenCalledTimes(3))

    fireEvent.change(screen.getByRole('combobox', { name: 'Competició' }), { target: { value: 'tercera' } })

    await waitFor(() => expect(getCalendarRange).toHaveBeenCalledWith(
      expect.objectContaining({ source: 'BCNESA', competition: 'tercera' }),
      'token', expect.anything(), expect.any(Function),
    ))
    const narrowed = getCalendarRange.mock.calls.filter(([filters]) => filters.competition === 'tercera')
    expect(narrowed.map(([filters]) => filters.source).sort()).toEqual(['BCNESA', 'FCTT', 'RFETM'])
  })

  it('merges matches from every source and shows each source on its group and in the dialog', async () => {
    mockSources()
    renderPage('/calendari?date=2026-09-16&view=week')

    expect(await screen.findByRole('button', { name: /^FCTT, 1 partit/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^RFETM, 1 partit/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^BCNESA, 1 partit/ })).toBeInTheDocument()
    expandWeek()
    fireEvent.click(screen.getByRole('button', { name: /^Club RFETM – Club B, 18:30, RFETM/ }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog.querySelector('.cal-source-tag')).toHaveTextContent('RFETM')
  })

  it('jornada without a source asks for one and calls neither calendar API', async () => {
    mockSources()
    renderPage('/calendari?view=jornada&season=2026-2027')

    expect(await screen.findByText('Selecciona una font per veure les jornades.')).toBeInTheDocument()
    expect(getSeasonCalendar).not.toHaveBeenCalled()
    expect(getCalendarRange).not.toHaveBeenCalled()
    expect(screen.getByRole('combobox', { name: 'Competició' })).toBeDisabled()
  })

  it('shows a no-seasons message when the sources have no seasons', async () => {
    getMatchOptions.mockResolvedValue({ seasons: [], competitions: [] })
    renderPage('/calendari?date=2026-09-16&view=week')

    expect(await screen.findByText('No hi ha temporades disponibles.')).toBeInTheDocument()
    expect(getCalendarRange).not.toHaveBeenCalled()
  })

  it('all-or-nothing: a failing source shows the error state instead of a partial calendar', async () => {
    mockSources()
    getCalendarRange.mockImplementation(({ source }) => (source === 'BCNESA'
      ? Promise.reject(Object.assign(new Error('nope'), { status: 500 }))
      : Promise.resolve(rangeWith([match()]))))
    renderPage('/calendari?date=2026-09-16&view=week')

    expect(await screen.findByText('No s’ha pogut carregar el calendari.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^FCTT/ })).toBeNull()
  })
  describe('month view', () => {
    const monthPath = '/calendari?source=FCTT&season=2026-2027&date=2026-09-16&view=month'
    const withMonthMatches = () => getCalendarRange.mockResolvedValue(rangeWith([
      match({ id: 'sat' }),
      match({ id: 'sun', dateTime: '2026-09-20T10:00:00+02:00', homeTeamName: 'Club S', awayTeamName: 'Club T', calendarState: 'UPCOMING' }),
    ]))

    it('renders the month grid with counts instead of FullCalendar and no detail panel', async () => {
      withMonthMatches()
      const { container } = renderPage(monthPath)

      const saturday = await screen.findByRole('button', { name: /19 de setembre/ })
      expect(screen.queryByTestId('calendar')).toBeNull()
      expect(saturday).toHaveAccessibleName(/1 partit, Pendent d’acta 1/)
      expect(container.querySelector('.calendar-detail')).toBeNull()
      expect(screen.queryByText('Club A – Club B')).toBeNull()
    })

    it('selecting a day opens the day view of that date', async () => {
      withMonthMatches()
      renderPage(monthPath)

      fireEvent.click(await screen.findByRole('button', { name: /20 de setembre/ }))

      await waitFor(() => expect(search().get('view')).toBe('day'))
      expect(search().get('date')).toBe('2026-09-20')
      expect(search().has('selected')).toBe(false)
      expect(screen.getByTestId('calendar')).toHaveAttribute('data-view', 'day')
    })

    it('ignores a selected day and a sort carried by an old link, and drops them on the next change', async () => {
      withMonthMatches()
      const { container } = renderPage(`${monthPath}&selected=2026-09-20&sort=venue`)

      await screen.findByRole('button', { name: /20 de setembre/ })
      expect(container.querySelector('.month-grid')).not.toBeNull()
      expect(screen.queryByRole('dialog')).toBeNull()
      expect(screen.queryByLabelText('Ordenar per')).toBeNull()

      fireEvent.click(screen.getByRole('button', { name: 'Període següent' }))
      await waitFor(() => expect(search().get('date')).toBe('2026-10-16'))
      expect(search().has('selected')).toBe(false)
      expect(search().has('sort')).toBe(false)
    })

    it('uses the today of the API response to mark the current day', async () => {
      withMonthMatches()
      renderPage(monthPath)

      expect(await screen.findByRole('button', { name: /16 de setembre/ })).toHaveAttribute('aria-current', 'date')
    })
  })

  describe('week view groups', () => {
    const weekPath = '/calendari?source=FCTT&season=2026-2027&date=2026-09-16&view=week'
    const saturdayMatches = () => getCalendarRange.mockResolvedValue(rangeWith([
      match({ id: 'a' }),
      match({
        id: 'b', dateTime: '2026-09-19T10:00:00+02:00', homeTeamName: 'Club S', awayTeamName: 'Club T', competition: 'segona', calendarState: 'UPCOMING',
      }),
      match({ id: 'c', dateTime: '2026-09-20T10:00:00+02:00', homeTeamName: 'Club U', awayTeamName: 'Club V' }),
    ]))

    const saturdayColumn = async () => screen.findByRole('region', { name: /^dissabte, 19 de setembre/ })

    it('starts with every source collapsed and shows no competition or match', async () => {
      saturdayMatches()
      renderPage(weekPath)

      const saturday = await saturdayColumn()
      const source = within(saturday).getByRole('button', { name: /^FCTT, 2 partits, Pendent d’acta 1, Proper 1/ })
      expect(source).toHaveAttribute('aria-expanded', 'false')
      expect(within(saturday).queryByRole('button', { name: /^tercera/ })).toBeNull()
      expect(screen.queryByRole('button', { name: /^Club A – Club B/ })).toBeNull()
    })

    it('shows the competitions after the first expand and the matches after the second', async () => {
      saturdayMatches()
      renderPage(weekPath)
      const saturday = await saturdayColumn()

      fireEvent.click(within(saturday).getByRole('button', { name: /^FCTT/ }))
      const tercera = within(saturday).getByRole('button', { name: /^tercera, 1 partit/ })
      expect(tercera).toHaveAttribute('aria-expanded', 'false')
      expect(within(saturday).getByRole('button', { name: /^segona, 1 partit/ })).toBeInTheDocument()
      expect(within(saturday).queryByRole('button', { name: /^Club A – Club B/ })).toBeNull()

      fireEvent.click(tercera)
      expect(within(saturday).getByRole('button', { name: /^Club A – Club B, 18:30, FCTT, tercera/ })).toBeInTheDocument()
      expect(within(saturday).queryByRole('button', { name: /^Club S – Club T/ })).toBeNull()
    })

    it('expanding or collapsing changes neither the URL nor opens a dialog', async () => {
      saturdayMatches()
      renderPage(weekPath)
      const saturday = await saturdayColumn()
      const before = screen.getByTestId('search').textContent

      fireEvent.click(within(saturday).getByRole('button', { name: /^FCTT/ }))
      fireEvent.click(within(saturday).getByRole('button', { name: /^tercera/ }))
      fireEvent.click(within(saturday).getByRole('button', { name: /^FCTT/ }))

      expect(screen.getByTestId('search').textContent).toBe(before)
      expect(screen.queryByRole('dialog')).toBeNull()
    })

    it('keeps the groups open while a match dialog is opened and closed', async () => {
      saturdayMatches()
      renderPage(weekPath)
      const saturday = await saturdayColumn()
      fireEvent.click(within(saturday).getByRole('button', { name: /^FCTT/ }))
      fireEvent.click(within(saturday).getByRole('button', { name: /^tercera/ }))

      fireEvent.click(within(saturday).getByRole('button', { name: /^Club A – Club B/ }))
      fireEvent.click(await screen.findByRole('button', { name: 'Tanca' }))
      await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())

      expect(within(saturday).getByRole('button', { name: /^FCTT/ })).toHaveAttribute('aria-expanded', 'true')
      expect(within(saturday).getByRole('button', { name: /^Club A – Club B/ })).toBeInTheDocument()
    })

    it('starts collapsed again when the week or a filter changes', async () => {
      saturdayMatches()
      renderPage(weekPath)
      let saturday = await saturdayColumn()
      fireEvent.click(within(saturday).getByRole('button', { name: /^FCTT/ }))
      expect(within(saturday).getByRole('button', { name: /^tercera/ })).toBeInTheDocument()

      fireEvent.click(screen.getByRole('button', { name: 'Període següent' }))
      await waitFor(() => expect(search().get('date')).toBe('2026-09-23'))
      fireEvent.click(screen.getByRole('button', { name: 'Període anterior' }))
      await waitFor(() => expect(search().get('date')).toBe('2026-09-16'))
      saturday = await saturdayColumn()
      expect(within(saturday).getByRole('button', { name: /^FCTT/ })).toHaveAttribute('aria-expanded', 'false')

      fireEvent.click(within(saturday).getByRole('button', { name: /^FCTT/ }))
      fireEvent.change(screen.getByLabelText('Competició'), { target: { value: 'tercera' } })
      await waitFor(() => expect(search().get('competition')).toBe('tercera'))
      saturday = await saturdayColumn()
      expect(within(saturday).getByRole('button', { name: /^FCTT/ })).toHaveAttribute('aria-expanded', 'false')
    })

    it('groups the sources of an all-sources week in source order', async () => {
      mockSources()
      renderPage('/calendari?date=2026-09-16&view=week')

      const saturday = await saturdayColumn()
      await within(saturday).findByRole('button', { name: /^FCTT/ })
      expect(within(saturday).getAllByRole('button', { expanded: false })
        .map((button) => button.getAttribute('aria-label').split(',')[0])).toEqual(['RFETM', 'FCTT', 'BCNESA'])
    })

    describe('on a phone', () => {
      const originalWidth = window.innerWidth
      afterEach(() => {
        window.innerWidth = originalWidth
      })

      it('renders the same groups, not FullCalendar', async () => {
        window.innerWidth = 375
        saturdayMatches()
        const { container } = renderPage(weekPath)

        expect(await screen.findByRole('button', { name: /^FCTT, 2 partits/ })).toBeInTheDocument()
        expect(container.querySelector('.week-grid')).not.toBeNull()
        expect(screen.queryByTestId('calendar')).toBeNull()
      })
    })
  })

  describe('match dialog', () => {
    const base = '/calendari?source=FCTT&season=2026-2027&date=2026-09-16'

    it.each([['day'], ['week'], ['month'], ['jornada']])('has no detail panel in the %s view and nothing after the calendar but the legend', async (view) => {
      const extra = view === 'jornada' ? '&competition=tercera' : ''
      const { container } = renderPage(`${base}&view=${view}${extra}`)

      await screen.findByRole('region', { name: 'Llegenda dels estats' })
      await waitFor(() => expect(getCalendarRange.mock.calls.length + getSeasonCalendar.mock.calls.length).toBeGreaterThan(0))
      expect(container.querySelector('.calendar-detail')).toBeNull()
      expect(screen.queryByRole('region', { name: 'Detall del calendari' })).toBeNull()
      expect(container.querySelector('.calendar-layout').className).toBe('calendar-layout')
      expect(screen.queryByLabelText('Ordenar per')).toBeNull()
    })

    it('opens from a day view entry and from a jornada entry, without changing the URL', async () => {
      renderPage(`${base}&view=day`)
      const before = screen.getByTestId('search').textContent

      fireEvent.click(await screen.findByRole('button', { name: 'event Club A – Club B' }))

      expect(await screen.findByRole('dialog', { name: 'Club A – Club B' })).toBeInTheDocument()
      expect(screen.getByTestId('search').textContent).toBe(before)
      cleanup()

      renderPage(`${base}&view=jornada&competition=tercera`)
      fireEvent.click(await screen.findByRole('button', { name: 'event Club C – Club D' }))
      expect(await screen.findByRole('dialog', { name: 'Club C – Club D' })).toBeInTheDocument()
    })

    it('opens an undated match of the jornada view from the undated list', async () => {
      renderPage(`${base}&view=jornada&competition=tercera`)

      const list = await screen.findByRole('region', { name: /Sense data/ })
      fireEvent.click(within(list).getByRole('button', { name: /^Club E – Club F/ }))

      const dialog = await screen.findByRole('dialog', { name: 'Club E – Club F' })
      expect(within(dialog).getByText('Sense data', { selector: 'dd' })).toBeInTheDocument()
    })

    it('lists undated matches of a range view below the calendar', async () => {
      getCalendarRange.mockResolvedValue(rangeWith([match(), match({ id: 'u', dateTime: null, calendarState: 'UNDATED', homeTeamName: 'Club U', awayTeamName: 'Club V' })]))
      renderPage(`${base}&view=month`)

      const list = await screen.findByRole('region', { name: /Sense data/ })
      expect(within(list).getAllByRole('button')).toHaveLength(1)
    })

    it('closes when the period changes', async () => {
      renderPage(`${base}&view=day`)
      fireEvent.click(await screen.findByRole('button', { name: 'event Club A – Club B' }))
      await screen.findByRole('dialog')

      // The modal dialog does not hide the toolbar from the test; a period change must still close it.
      getCalendarRange.mockResolvedValue(rangeWith([]))
      fireEvent.click(screen.getByRole('button', { name: 'Període següent', hidden: true }))

      await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
      expect(search().get('date')).toBe('2026-09-17')
    })
  })

  describe('legend', () => {
    it('is shown below the calendar with every state and the overdue rule of the response', async () => {
      const { container } = renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16&view=week')

      const legend = await screen.findByRole('region', { name: 'Llegenda dels estats' })
      expect(legend).toHaveTextContent('sense acta 7 dies')
      expect(container.querySelector('.week-grid').compareDocumentPosition(legend) & Node.DOCUMENT_POSITION_FOLLOWING)
        .toBeTruthy()
      expect(screen.queryByText(/Consulta el calendari per dia/)).toBeNull()
    })
  })

  describe('filters on a phone', () => {
    const originalWidth = window.innerWidth
    afterEach(() => {
      window.innerWidth = originalWidth
    })

    it('collapse behind a button that counts the filters set in the URL, not the defaulted ones', async () => {
      window.innerWidth = 375
      renderPage('/calendari?source=FCTT&season=2026-2027&competition=tercera&date=2026-09-16')

      const toggle = await screen.findByRole('button', { name: /Filtres/ })
      expect(toggle).toHaveAttribute('aria-expanded', 'false')
      expect(toggle).toHaveTextContent('3 filtres actius')
      expect(screen.getByLabelText('Font')).not.toBeVisible()
      fireEvent.click(toggle)
      expect(screen.getByLabelText('Font')).toBeVisible()
    })

    it('does not count a season or group that is only defaulted', async () => {
      window.innerWidth = 375
      mockSources()
      renderPage('/calendari?date=2026-09-16')

      const toggle = await screen.findByRole('button', { name: /Filtres/ })
      await screen.findByRole('button', { name: /^FCTT, 1 partit/ })
      expect(toggle).not.toHaveTextContent('actiu')
      expect(toggle).toHaveTextContent('Totes les fonts · 2026-2027')
    })

    it('keeps the filters open on wider screens', async () => {
      window.innerWidth = 1440
      renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')

      await screen.findByRole('button', { name: /^FCTT, 1 partit/ })
      expect(screen.queryByRole('button', { name: /Filtres/ })).toBeNull()
      expect(screen.getByLabelText('Font')).toBeVisible()
    })
  })
})
