import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CalendarFilters from './CalendarFilters.jsx'
import CalendarLegend from './CalendarLegend.jsx'
import CalendarToolbar from './CalendarToolbar.jsx'
import MatchCalendar from './MatchCalendar.jsx'
import { toEvents } from './calendarEvents.js'

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

    expect(onChange).toHaveBeenCalledWith('team', 't2')
    expect(screen.queryByLabelText('Ordenar per')).toBeNull()
  })

  it('names its region instead of repeating a heading, and always shows the fields when not collapsible', () => {
    render(<CalendarFilters filters={filters} options={options} onChange={vi.fn()} />)

    expect(screen.getByRole('region', { name: 'Selecció de calendari' })).toBeInTheDocument()
    expect(screen.queryByRole('heading')).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.getByLabelText('Font')).toBeVisible()
  })

  describe('when collapsible', () => {
    const renderCollapsible = (props = {}) => render(
      <CalendarFilters
        filters={filters}
        options={options}
        collapsible
        activeCount={2}
        onChange={vi.fn()}
        {...props}
      />,
    )

    it('hides the fields behind a button that shows the active count and the source and season', () => {
      renderCollapsible()

      const toggle = screen.getByRole('button', { name: /Filtres/ })
      expect(toggle).toHaveAttribute('aria-expanded', 'false')
      expect(toggle).toHaveAttribute('aria-controls', document.querySelector('.match-filter-grid').id)
      expect(toggle).toHaveTextContent('2 filtres actius')
      expect(toggle).toHaveTextContent('FCTT · 2026-2027')
      expect(document.querySelector('.match-filter-grid')).not.toBeVisible()

      fireEvent.click(toggle)
      expect(toggle).toHaveAttribute('aria-expanded', 'true')
      expect(screen.getByLabelText('Font')).toBeVisible()
      fireEvent.click(toggle)
      expect(toggle).toHaveAttribute('aria-expanded', 'false')
    })

    it('shows no count when no filter is active and says all sources without one', () => {
      renderCollapsible({ activeCount: 0, filters: { ...filters, source: '' } })

      const toggle = screen.getByRole('button', { name: /Filtres/ })
      expect(toggle).not.toHaveTextContent('actiu')
      expect(toggle).toHaveTextContent('Totes les fonts')
    })

    it('uses the singular for one active filter', () => {
      renderCollapsible({ activeCount: 1 })
      expect(screen.getByRole('button', { name: /Filtres/ })).toHaveTextContent('1 filtre actiu')
    })

    it('forces the fields open while the jornada view still needs a source or a competition', () => {
      const { unmount } = renderCollapsible({ optionalScope: false, filters: { ...filters, source: '' } })
      expect(screen.getByRole('button', { name: /Filtres/ })).toHaveAttribute('aria-expanded', 'true')
      expect(screen.getByLabelText('Font')).toBeVisible()
      unmount()

      const second = renderCollapsible({ optionalScope: false })
      expect(screen.getByLabelText('Competició')).toBeVisible()
      second.unmount()

      renderCollapsible({ optionalScope: false, filters: { ...filters, competition: 'tercera' } })
      expect(screen.getByRole('button', { name: /Filtres/ })).toHaveAttribute('aria-expanded', 'false')
    })
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

  it('enables the season with no source selected and shows the placeholder only without a season', () => {
    const { unmount } = render(<CalendarFilters
      filters={{ ...filters, source: '', season: '' }}
      options={options}
      onChange={vi.fn()}
    />)

    expect(screen.getByLabelText('Temporada')).toBeEnabled()
    expect(within(screen.getByLabelText('Temporada')).getAllByRole('option')[0]).toHaveTextContent('Selecciona')
    unmount()

    render(<CalendarFilters filters={{ ...filters, source: '' }} options={options} onChange={vi.fn()} />)
    expect(within(screen.getByLabelText('Temporada')).queryByRole('option', { name: 'Selecciona' })).toBeNull()
  })

  it('disables competition and team for jornada until a source is chosen', () => {
    render(<CalendarFilters
      filters={{ ...filters, source: '' }}
      options={options}
      optionalScope={false}
      onChange={vi.fn()}
    />)

    expect(screen.getByLabelText('Competició')).toBeDisabled()
    expect(screen.getByLabelText('Equip')).toBeDisabled()
  })

  it('shows the source of a team facet when it carries one', () => {
    render(<CalendarFilters
      filters={{ ...filters, source: '' }}
      options={{ ...options, teams: [{ teamId: 't1', name: 'Club A', competition: 'tercera', source: 'RFETM' }] }}
      onChange={vi.fn()}
    />)

    expect(within(screen.getByLabelText('Equip')).getByRole('option', { name: 'Club A · tercera · RFETM' }))
      .toBeInTheDocument()
  })
})

describe('CalendarLegend', () => {
  it('explains every state with its translated name and the overdue rule', () => {
    const { container } = render(<CalendarLegend overdueGraceDays={7} />)

    const legend = screen.getByRole('region', { name: 'Llegenda dels estats' })
    expect(within(legend).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Endarrerit', 'Pendent d’acta', 'Ajornat', 'Proper', 'Jugat', 'Sense data',
    ])
    expect(container.querySelectorAll('li svg[aria-hidden="true"]')).toHaveLength(6)
    expect(legend).toHaveTextContent('Endarrerit: sense acta 7 dies després de la data')
  })

  it('omits the overdue rule until the API reports the grace days', () => {
    render(<CalendarLegend />)
    expect(screen.queryByText(/sense acta/)).toBeNull()
  })
})

describe('MatchCalendar', () => {
  it('renders a day list entry with the teams, the source tag and the state, and reports a click', () => {
    const onMatchSelect = vi.fn()
    const { container } = render(
      <MatchCalendar
        view="day"
        date="2026-09-19"
        events={toEvents([match({ source: 'FCTT' })])}
        onMatchSelect={onMatchSelect}
      />,
    )

    const entry = screen.getByRole('button', { name: 'Club A – Club B, 18:30, FCTT, Proper' })
    expect(container.querySelector('.fc-event')).toContainElement(entry)
    expect(entry).toHaveTextContent('Club A – Club B')
    expect(entry.querySelector('.cal-source-tag')).toHaveTextContent('FCTT')
    expect(entry.querySelector('svg.state-icon-upcoming')).toHaveAttribute('aria-hidden', 'true')
    fireEvent.click(entry)
    expect(onMatchSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 'm1' }))
  })

  it('renders the jornada as a list over the round dates', () => {
    const { container } = render(
      <MatchCalendar
        view="jornada"
        date="2026-09-16"
        events={toEvents([match()])}
        visibleRange={{ firstDate: '2026-09-19', lastDate: '2026-09-19' }}
        onMatchSelect={vi.fn()}
      />,
    )

    expect(container.querySelector('.fc-list')).not.toBeNull()
    expect(container).toHaveTextContent('Club A – Club B')
  })

  it('shows teams, source tag and state icon with its name in list entries', () => {
    const { container } = render(
      <MatchCalendar
        view="jornada"
        date="2026-09-16"
        events={toEvents([match({ source: 'BCNESA', calendarState: 'OVERDUE' })])}
        visibleRange={{ firstDate: '2026-09-19', lastDate: '2026-09-19' }}
        onMatchSelect={vi.fn()}
      />,
    )

    const entry = container.querySelector('.cal-entry-list')
    expect(entry.querySelector('.cal-entry-teams')).toHaveTextContent('Club A – Club B')
    expect(entry.querySelector('.cal-source-tag')).toHaveTextContent('BCNESA')
    expect(entry.querySelector('.cal-entry-state')).toHaveTextContent('Endarrerit')
    expect(entry.querySelector('svg.state-icon-overdue')).not.toBeNull()
  })
})
