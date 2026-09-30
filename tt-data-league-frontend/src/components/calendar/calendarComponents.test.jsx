import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CalendarDetailPanel from './CalendarDetailPanel.jsx'
import CalendarFilters from './CalendarFilters.jsx'
import CalendarToolbar from './CalendarToolbar.jsx'
import MatchCalendar from './MatchCalendar.jsx'
import { fullCalendarView, toEvents } from './calendarEvents.js'

afterEach(cleanup)

const match = (overrides = {}) => ({
  id: 'm1',
  dateTime: '2026-09-19T18:30:00+02:00',
  venue: 'Pavelló',
  city: 'Terrassa',
  homeTeamName: 'Club A',
  awayTeamName: 'Club B',
  homeGamesWon: null,
  awayGamesWon: null,
  status: 'SCHEDULED',
  calendarState: 'UPCOMING',
  overdueMarked: false,
  overdueMarkedAt: null,
  overdueMarkedBy: null,
  overdueMarkable: false,
  competition: 'tercera',
  groupNumber: 1,
  ...overrides,
})

describe('CalendarToolbar', () => {
  const renderToolbar = (props = {}) => {
    const handlers = {
      onViewChange: vi.fn(), onPrevious: vi.fn(), onNext: vi.fn(), onToday: vi.fn(), onRoundChange: vi.fn(),
    }
    render(<CalendarToolbar view="week" title="14 set – 20 set 2026" {...handlers} {...props} />)
    return handlers
  }

  it('switches view with pressed-state buttons and navigates with named buttons', () => {
    const handlers = renderToolbar()

    expect(screen.getByRole('button', { name: 'Setmana' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Mes' })).toHaveAttribute('aria-pressed', 'false')
    fireEvent.click(screen.getByRole('button', { name: 'Mes' }))
    fireEvent.click(screen.getByRole('button', { name: 'Període anterior' }))
    fireEvent.click(screen.getByRole('button', { name: 'Període següent' }))
    fireEvent.click(screen.getByRole('button', { name: 'Avui' }))

    expect(handlers.onViewChange).toHaveBeenCalledWith('month')
    expect(handlers.onPrevious).toHaveBeenCalledTimes(1)
    expect(handlers.onNext).toHaveBeenCalledTimes(1)
    expect(handlers.onToday).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('heading', { name: '14 set – 20 set 2026' })).toBeInTheDocument()
  })

  it('shows the jornada with previous/next over the rounds and a round select', () => {
    const handlers = renderToolbar({ view: 'jornada', rounds: [1, 2, 3], round: 2 })

    expect(screen.getByRole('heading', { name: 'Jornada 2' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Jornada anterior' }))
    fireEvent.click(screen.getByRole('button', { name: 'Jornada següent' }))
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '3' } })

    expect(handlers.onRoundChange).toHaveBeenNthCalledWith(1, 1)
    expect(handlers.onRoundChange).toHaveBeenNthCalledWith(2, 3)
    expect(handlers.onRoundChange).toHaveBeenNthCalledWith(3, 3)
    expect(screen.queryByRole('button', { name: 'Avui' })).toBeNull()
  })

  it('disables the round buttons at the ends', () => {
    renderToolbar({ view: 'jornada', rounds: [1, 2], round: 1 })
    expect(screen.getByRole('button', { name: 'Jornada anterior' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Jornada següent' })).toBeEnabled()
  })
})

describe('CalendarFilters', () => {
  const filters = { source: 'FCTT', season: '2026-2027', competition: '', group: '', team: '', sort: 'date' }
  const options = {
    seasons: ['2026-2027'],
    competitions: ['tercera', 'segunda'],
    groups: [1, 2],
    teams: [
      { teamId: 't1', name: 'Club A', competition: 'tercera' },
      { teamId: 't2', name: 'Club B', competition: 'segunda' },
    ],
  }

  it('renders the facet options and reports changes', () => {
    const onChange = vi.fn()
    render(<CalendarFilters filters={{ ...filters, competition: 'tercera' }} options={options} onChange={onChange} />)

    expect(within(screen.getByLabelText('Competició')).getAllByRole('option').map((o) => o.textContent))
      .toEqual(['Totes les competicions', 'tercera', 'segunda'])
    expect(within(screen.getByLabelText('Grup')).getAllByRole('option').map((o) => o.textContent))
      .toEqual(['Tots els grups', '1', '2'])
    expect(within(screen.getByLabelText('Equip')).getAllByRole('option').map((o) => o.textContent))
      .toEqual(['Tots els equips', 'Club A', 'Club B'])

    fireEvent.change(screen.getByLabelText('Equip'), { target: { value: 't2' } })
    fireEvent.change(screen.getByLabelText('Ordenar per'), { target: { value: 'venue' } })

    expect(onChange).toHaveBeenNthCalledWith(1, 'team', 't2')
    expect(onChange).toHaveBeenNthCalledWith(2, 'sort', 'venue')
  })

  it('tells teams apart across competitions when none is selected and disables the group', () => {
    render(<CalendarFilters filters={filters} options={options} onChange={vi.fn()} />)

    expect(within(screen.getByLabelText('Equip')).getByRole('option', { name: 'Club A · tercera' })).toBeInTheDocument()
    expect(screen.getByLabelText('Grup')).toBeDisabled()
  })

  it('requires a competition when the scope is not optional', () => {
    render(<CalendarFilters filters={filters} options={options} optionalScope={false} onChange={vi.fn()} />)

    expect(within(screen.getByLabelText('Competició')).getAllByRole('option')[0]).toHaveTextContent('Selecciona')
  })
})

describe('CalendarDetailPanel', () => {
  const renderPanel = (matches, props = {}) => render(
    <MemoryRouter>
      <CalendarDetailPanel
        heading="Dissabte"
        matches={matches}
        sort="date"
        returnSearch="source=FCTT&season=2026-2027"
        onToggleMark={vi.fn()}
        {...props}
      />
    </MemoryRouter>,
  )

  it('lists matches sorted, each linking to the match detail for every status', () => {
    renderPanel([
      match({ id: 'late', dateTime: '2026-09-19T20:00:00+02:00', homeTeamName: 'Late', awayTeamName: 'Team' }),
      match({
        id: 'played', dateTime: '2026-09-19T10:00:00+02:00', homeTeamName: 'Early', awayTeamName: 'Team',
        status: 'PLAYED', calendarState: 'PLAYED', homeGamesWon: 4, awayGamesWon: 1,
      }),
    ])

    const links = screen.getAllByRole('link')
    expect(links[0]).toHaveTextContent('Early – Team')
    expect(links[0]).toHaveAttribute('href', '/partits/played?source=FCTT&season=2026-2027')
    expect(links[0]).toHaveTextContent('4–1')
    expect(links[1]).toHaveAttribute('href', '/partits/late?source=FCTT&season=2026-2027')
    expect(screen.getByText('Jugat')).toBeInTheDocument()
    expect(screen.getByText('Proper')).toBeInTheDocument()
  })

  it('sorts by the requested criterion', () => {
    renderPanel([
      match({ id: 'a', venue: 'Zeta', homeTeamName: 'Zed' }),
      match({ id: 'b', venue: 'Alfa', homeTeamName: 'Alpha' }),
    ], { sort: 'venue' })

    expect(screen.getAllByRole('link').map((link) => link.textContent.slice(0, 5))).toEqual(['Alpha', 'Zed –'])
  })

  it('hides the mark action until the backend reports the match markable (the day after its date)', () => {
    renderPanel([match({ overdueMarkable: false })], { canWrite: true })

    expect(screen.queryByRole('button', { name: /endarrerit/ })).toBeNull()
  })

  it('offers the overdue buttons only with write permission and only on scheduled rows', () => {
    const matches = [
      match({ overdueMarkable: true, calendarState: 'AWAITING_RESULT' }),
      match({ id: 'played', status: 'PLAYED', calendarState: 'PLAYED' }),
    ]
    const onToggleMark = vi.fn()
    const { unmount } = renderPanel(matches, { canWrite: false })
    expect(screen.queryByRole('button', { name: /endarrerit/ })).toBeNull()
    unmount()

    renderPanel(matches, { canWrite: true, onToggleMark })
    const buttons = screen.getAllByRole('button', { name: /Marcar com a endarrerit/ })
    expect(buttons).toHaveLength(1)
    fireEvent.click(buttons[0])
    expect(onToggleMark).toHaveBeenCalledWith(expect.objectContaining({ id: 'm1' }))
  })

  it('shows the clear action and the author of a manual mark', () => {
    renderPanel([match({
      overdueMarked: true, calendarState: 'OVERDUE', overdueMarkedBy: 'admin',
      overdueMarkedAt: '2026-09-20T10:00:00+02:00',
    })], { canWrite: true })

    expect(screen.getByRole('button', { name: /Treure la marca d’endarrerit/ })).toBeInTheDocument()
    expect(screen.getByText(/marcat per admin/)).toBeInTheDocument()
  })

  it('lists undated matches under their own subheading', () => {
    renderPanel([match(), match({ id: 'undated', dateTime: null, calendarState: 'UNDATED' })])

    expect(screen.getByRole('heading', { name: 'Sense data', level: 3 })).toBeInTheDocument()
    expect(screen.getAllByRole('link')).toHaveLength(2)
  })

  it('shows an empty status for no matches', () => {
    renderPanel([])
    expect(screen.getByRole('status')).toHaveTextContent('No hi ha partits en aquesta selecció.')
  })
})

describe('MatchCalendar', () => {
  it('maps our views to FullCalendar views', () => {
    expect(fullCalendarView('day')).toBe('listDay')
    expect(fullCalendarView('week')).toBe('timeGridWeek')
    expect(fullCalendarView('week', true)).toBe('listWeek')
    expect(fullCalendarView('month')).toBe('dayGridMonth')
    expect(fullCalendarView('jornada')).toBe('listJornada')
  })

  it('renders match events in the month grid and reports a click on one', () => {
    const onMatchSelect = vi.fn()
    const { container } = render(
      <MatchCalendar
        view="month"
        date="2026-09-16"
        events={toEvents([match()])}
        onDateSelect={vi.fn()}
        onMatchSelect={onMatchSelect}
      />,
    )

    const event = container.querySelector('.fc-event')
    expect(event).not.toBeNull()
    expect(event).toHaveTextContent('Club A – Club B')
    expect(event).toHaveClass('cal-state-upcoming')
    expect(event.getAttribute('aria-label')).toContain('Club A – Club B')
    fireEvent.click(event)
    expect(onMatchSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 'm1' }))
  })

  it('renders the jornada as a list over the round dates', () => {
    const { container } = render(
      <MatchCalendar
        view="jornada"
        date="2026-09-16"
        events={toEvents([match()])}
        visibleRange={{ firstDate: '2026-09-19', lastDate: '2026-09-19' }}
        onDateSelect={vi.fn()}
        onMatchSelect={vi.fn()}
      />,
    )

    expect(container.querySelector('.fc-list')).not.toBeNull()
    expect(container).toHaveTextContent('Club A – Club B')
  })
})
