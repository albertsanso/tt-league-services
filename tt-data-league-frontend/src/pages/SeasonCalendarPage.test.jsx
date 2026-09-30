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
    renderPage('/calendari?source=FCTT&season=2026-2027')

    await screen.findByText('Club A – Club B')
    const call = getCalendarRange.mock.calls[0][0]
    expect(call).toMatchObject({ source: 'FCTT', season: '2026-2027' })
    expect(new Date(call.to) - new Date(call.from)).toBe(7 * 86400000)
    expect(screen.getByTestId('calendar')).toHaveAttribute('data-view', 'week')
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
    await screen.findByTestId('calendar')

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
    await screen.findByText('Club A – Club B')

    fireEvent.change(screen.getByLabelText('Competició'), { target: { value: 'tercera' } })
    await waitFor(() => expect(search().get('competition')).toBe('tercera'))
    fireEvent.change(screen.getByLabelText('Grup'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('Equip'), { target: { value: 'team-b' } })
    fireEvent.change(screen.getByLabelText('Ordenar per'), { target: { value: 'venue' } })

    await waitFor(() => expect(getCalendarRange).toHaveBeenCalledWith(
      expect.objectContaining({ competition: 'tercera', group: '1', team: 'team-b' }),
      'token', expect.anything(), expect.any(Function),
    ))
    expect(search().get('group')).toBe('1')
    expect(search().get('team')).toBe('team-b')
    expect(search().get('sort')).toBe('venue')

    fireEvent.change(screen.getByLabelText('Competició'), { target: { value: 'segunda' } })
    await waitFor(() => expect(search().get('competition')).toBe('segunda'))
    expect(search().has('group')).toBe(false)
    expect(search().has('team')).toBe(false)
    expect(search().get('sort')).toBe('venue')
  })

  it('a day selection narrows the detail list and a view switch clears it', async () => {
    getCalendarRange.mockResolvedValue(rangeWith([
      match({ id: 'sat' }),
      match({ id: 'sun', dateTime: '2026-09-20T10:00:00+02:00', homeTeamName: 'Club S', awayTeamName: 'Club T' }),
    ]))
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16&view=week')
    const detail = await screen.findByRole('region', { name: 'Detall del calendari' })
    await within(detail).findByText('Club S – Club T')

    fireEvent.click(screen.getByRole('button', { name: 'day 2026-09-19' }))

    await waitFor(() => expect(within(detail).queryByText('Club S – Club T')).toBeNull())
    expect(within(detail).getByText('Club A – Club B')).toBeInTheDocument()
    expect(search().get('selected')).toBe('2026-09-19')

    fireEvent.click(screen.getByRole('button', { name: 'Mes' }))
    await waitFor(() => expect(search().has('selected')).toBe(false))
    expect(await within(detail).findByText('Club S – Club T')).toBeInTheDocument()
  })

  it('clicking an event selects its day and focuses its row link to the match detail', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16&view=week')

    fireEvent.click(await screen.findByRole('button', { name: 'event Club A – Club B' }))

    await waitFor(() => expect(search().get('selected')).toBe('2026-09-19'))
    const link = within(screen.getByRole('region', { name: 'Detall del calendari' })).getByRole('link')
    expect(link).toHaveAttribute('href', expect.stringContaining('/partits/match-1?'))
    await waitFor(() => expect(link).toHaveFocus())
  })

  it('jornada view asks for a competition first', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&view=jornada')

    expect(await screen.findByText('Selecciona una competició per veure les jornades.')).toBeInTheDocument()
    expect(getSeasonCalendar).not.toHaveBeenCalled()
    expect(getCalendarRange).not.toHaveBeenCalled()
  })

  it('jornada view defaults to the current round and navigates between rounds', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&competition=tercera&view=jornada')

    const detail = await screen.findByRole('region', { name: 'Detall del calendari' })
    await within(detail).findByText('Club C – Club D')
    expect(screen.getAllByRole('heading', { name: 'Jornada 2' })).toHaveLength(2)
    expect(getSeasonCalendar.mock.calls[0][0]).toMatchObject({ competition: 'tercera', round: '' })
    expect(screen.getByTestId('calendar')).toHaveAttribute('data-range', '2026-09-19/2026-09-20')
    expect(within(detail).getByRole('heading', { name: 'Sense data', level: 3 })).toBeInTheDocument()
    expect(within(detail).getByText('Club E – Club F')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Jornada anterior' }))
    await waitFor(() => expect(search().get('round')).toBe('1'))
    expect((await screen.findAllByRole('heading', { name: 'Jornada 1' })).length).toBeGreaterThan(0)
    expect(within(detail).queryByText('Club C – Club D')).toBeNull()
    expect(screen.getByRole('button', { name: 'Jornada anterior' })).toBeDisabled()
  })

  it('jornada view filters by team on the client', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&competition=tercera&view=jornada&team=team-c')

    const detail = await screen.findByRole('region', { name: 'Detall del calendari' })
    await within(detail).findByText('Club C – Club D')
    expect(within(detail).queryByText('Club A – Club B')).toBeNull()
  })

  it('shows the group progress header in the jornada view', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&competition=tercera&view=jornada')

    expect(await screen.findByRole('progressbar', { name: 'Progrés de la jornada' })).toHaveAttribute('aria-valuetext', '2 / 4')
    expect(screen.getByText(/Jornada actual: 2/)).toBeInTheDocument()
  })

  it('offers the mark action only to matches:write users', async () => {
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')

    await screen.findByText('Club A – Club B')
    expect(screen.queryByRole('button', { name: /Marcar com a endarrerit/ })).toBeNull()
  })

  it('marks a scheduled match and clears it for a write user, refetching the range', async () => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn(), hasPermission: () => true })
    markMatchOverdue.mockResolvedValue(null)
    clearMatchOverdueMark.mockResolvedValue(null)
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')

    fireEvent.click(await screen.findByRole('button', { name: /Marcar com a endarrerit/ }))
    await waitFor(() => expect(markMatchOverdue).toHaveBeenCalledWith(
      'match-1', 'token', undefined, expect.any(Function),
    ))

    getCalendarRange.mockResolvedValue(rangeWith([match({
      overdueMarked: true, calendarState: 'OVERDUE', overdueMarkedBy: 'admin', overdueMarkedAt: '2026-09-08T10:00:00+02:00',
    })]))
    fireEvent.click(await screen.findByRole('button', { name: /Treure la marca d’endarrerit/ }))
    await waitFor(() => expect(clearMatchOverdueMark).toHaveBeenCalledWith(
      'match-1', 'token', undefined, expect.any(Function),
    ))
  })

  it('shows a conflict message when marking fails with 409', async () => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn(), hasPermission: () => true })
    markMatchOverdue.mockRejectedValue(Object.assign(new Error('conflict'), { status: 409 }))
    renderPage('/calendari?source=FCTT&season=2026-2027&date=2026-09-16')

    fireEvent.click(await screen.findByRole('button', { name: /Marcar com a endarrerit/ }))

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

  it('asks for a source and season before loading anything', () => {
    renderPage('/calendari')

    expect(screen.getByText('Selecciona una font i una temporada per veure el calendari.')).toBeInTheDocument()
    expect(getCalendarRange).not.toHaveBeenCalled()
  })
})
