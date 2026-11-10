import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WeekGrid from './WeekGrid.jsx'

afterEach(cleanup)

const match = (id, dateTime, overrides = {}) => ({
  id,
  dateTime,
  source: 'FCTT',
  competition: 'tercera',
  calendarState: 'UPCOMING',
  homeTeamName: `H${id}`,
  awayTeamName: `A${id}`,
  ...overrides,
})

const matches = [
  match('1', '2026-09-19T18:30:00+02:00'),
  match('2', '2026-09-19T10:00:00+02:00', { calendarState: 'OVERDUE' }),
  match('3', '2026-09-19T12:00:00+02:00', { competition: 'segona', calendarState: 'PLAYED' }),
  match('4', '2026-09-19T11:00:00+02:00', { source: 'RFETM', competition: 'primera' }),
  match('5', '2026-09-15T11:00:00+02:00', { source: 'BCNESA' }),
]

const renderGrid = (props = {}) => render(
  <WeekGrid
    date="2026-09-16"
    today="2026-09-16"
    matches={matches}
    onDateSelect={vi.fn()}
    onMatchSelect={vi.fn()}
    {...props}
  />,
)

const day = (name) => screen.getByRole('region', { name })
const saturday = () => day(/^dissabte, 19 de setembre/)

describe('WeekGrid', () => {
  it('shows the seven days of the week, marking today, with day headers that open the day view', () => {
    renderGrid()

    expect(screen.getAllByRole('region')).toHaveLength(7)
    expect(screen.getByRole('button', { name: /^dimecres, 16 de setembre/ })).toHaveAttribute('aria-current', 'date')
    const head = within(saturday()).getByRole('button', { name: /^dissabte, 19 de setembre/ })
    expect(head).not.toHaveAttribute('aria-pressed')
    expect(head).toHaveAccessibleName(/Obre la vista de dia$/)
    expect(within(day(/^dilluns, 14 de setembre/)).getByText('Cap partit')).toBeInTheDocument()
  })

  it('starts with only the source groups, collapsed, with their counts and state counts', () => {
    renderGrid()

    const sources = within(saturday()).getAllByRole('button', { expanded: false })
    expect(sources.map((button) => button.getAttribute('aria-label'))).toEqual([
      'RFETM, 1 partit, Proper 1',
      'FCTT, 3 partits, Endarrerit 1, Proper 1, Jugat 1',
    ])
    expect(within(saturday()).queryByRole('button', { name: /^tercera/ })).toBeNull()
    expect(within(saturday()).queryByRole('button', { name: /^H1/ })).toBeNull()
  })

  it('shows the competitions collapsed after the first expand, then the matches in time order', () => {
    renderGrid()
    const column = saturday()

    fireEvent.click(within(column).getByRole('button', { name: /^FCTT/ }))
    expect(within(column).getByRole('button', { name: /^FCTT/ })).toHaveAttribute('aria-expanded', 'true')
    expect(within(column).getAllByRole('button', { expanded: false }).map((button) => button.getAttribute('aria-label')))
      .toEqual(expect.arrayContaining(['segona, 1 partit, Jugat 1', 'tercera, 2 partits, Endarrerit 1, Proper 1']))
    expect(within(column).queryByRole('button', { name: /^H1/ })).toBeNull()

    fireEvent.click(within(column).getByRole('button', { name: /^tercera/ }))
    const entries = within(column).getAllByRole('button', { name: /^H\d – A\d/ })
    expect(entries.map((entry) => entry.getAttribute('aria-label'))).toEqual([
      'H2 – A2, 10:00, FCTT, tercera, Endarrerit',
      'H1 – A1, 18:30, FCTT, tercera, Proper',
    ])
    expect(within(column).queryByRole('button', { name: /^H3/ })).toBeNull()
  })

  it('points each toggle at the region it controls', () => {
    renderGrid()
    const column = saturday()
    const toggle = within(column).getByRole('button', { name: /^FCTT/ })

    const region = document.getElementById(toggle.getAttribute('aria-controls'))
    expect(region).toBeInTheDocument()
    expect(region).toHaveAttribute('hidden')
    fireEvent.click(toggle)
    expect(region).not.toHaveAttribute('hidden')
    expect(region).toContainElement(within(column).getByRole('button', { name: /^tercera/ }))
  })

  it('collapses a source with its competitions, which are collapsed again when it reopens', () => {
    renderGrid()
    const column = saturday()
    fireEvent.click(within(column).getByRole('button', { name: /^FCTT/ }))
    fireEvent.click(within(column).getByRole('button', { name: /^tercera/ }))

    fireEvent.click(within(column).getByRole('button', { name: /^FCTT/ }))
    expect(within(column).queryByRole('button', { name: /^tercera/ })).toBeNull()
    expect(within(column).queryByRole('button', { name: /^H1/ })).toBeNull()

    fireEvent.click(within(column).getByRole('button', { name: /^FCTT/ }))
    expect(within(column).getByRole('button', { name: /^tercera/ })).toHaveAttribute('aria-expanded', 'false')
    expect(within(column).queryByRole('button', { name: /^H1/ })).toBeNull()
  })

  it('collapses a competition on its own and expands days and groups independently', () => {
    renderGrid()
    const column = saturday()
    fireEvent.click(within(column).getByRole('button', { name: /^FCTT/ }))
    fireEvent.click(within(column).getByRole('button', { name: /^tercera/ }))
    fireEvent.click(within(column).getByRole('button', { name: /^segona/ }))
    fireEvent.click(within(column).getByRole('button', { name: /^tercera/ }))

    expect(within(column).queryByRole('button', { name: /^H1/ })).toBeNull()
    expect(within(column).getByRole('button', { name: /^H3/ })).toBeInTheDocument()
    expect(within(column).getByRole('button', { name: /^RFETM/ })).toHaveAttribute('aria-expanded', 'false')
    expect(within(day(/^dimarts, 15 de setembre/)).getByRole('button', { name: /^BCNESA/ }))
      .toHaveAttribute('aria-expanded', 'false')
  })

  it('uses native buttons, so Enter and Space activate them', () => {
    renderGrid()

    screen.getAllByRole('button').forEach((button) => {
      expect(button.tagName).toBe('BUTTON')
      expect(button).toHaveAttribute('type', 'button')
    })
  })

  it('reports a match entry and a day header click', () => {
    const onMatchSelect = vi.fn()
    const onDateSelect = vi.fn()
    renderGrid({ onMatchSelect, onDateSelect })
    const column = saturday()

    fireEvent.click(within(column).getByRole('button', { name: /^dissabte, 19 de setembre/ }))
    fireEvent.click(within(column).getByRole('button', { name: /^FCTT/ }))
    fireEvent.click(within(column).getByRole('button', { name: /^tercera/ }))
    fireEvent.click(within(column).getByRole('button', { name: /^H1 – A1/ }))

    expect(onDateSelect).toHaveBeenCalledWith('2026-09-19')
    expect(onMatchSelect).toHaveBeenCalledWith(expect.objectContaining({ id: '1' }))
  })

  it('groups matches without a source under the fallback source', () => {
    renderGrid({
      fallbackSource: 'FCTT',
      matches: [match('1', '2026-09-19T10:00:00+02:00', { source: undefined })],
    })

    expect(within(saturday()).getByRole('button', { name: /^FCTT, 1 partit/ })).toBeInTheDocument()
  })

  it('labels a match without a source or competition instead of dropping it', () => {
    renderGrid({ matches: [match('1', '2026-09-19T10:00:00+02:00', { source: undefined, competition: undefined })] })
    const column = saturday()

    fireEvent.click(within(column).getByRole('button', { name: /^Sense font/ }))
    fireEvent.click(within(column).getByRole('button', { name: /^Sense competició/ }))
    expect(within(column).getByRole('button', { name: /^H1 – A1/ })).toBeInTheDocument()
  })

  it('keeps every match of a busy day reachable, with no "more" link', () => {
    const many = Array.from({ length: 12 }, (_, index) => match(
      `m${index}`, `2026-09-19T${String(8 + index).padStart(2, '0')}:00:00+02:00`,
    ))
    renderGrid({ matches: many })
    const column = saturday()

    fireEvent.click(within(column).getByRole('button', { name: /^FCTT, 12 partits/ }))
    fireEvent.click(within(column).getByRole('button', { name: /^tercera, 12 partits/ }))
    expect(within(column).getAllByRole('button', { name: /^Hm\d+ – Am\d+/ })).toHaveLength(12)
    expect(screen.queryByText(/més|more/i)).toBeNull()
  })

  it('ignores matches outside the week and undated ones', () => {
    renderGrid({
      matches: [
        match('out', '2026-09-30T10:00:00+02:00'),
        match('undated', null),
      ],
    })

    expect(screen.getAllByText('Cap partit')).toHaveLength(7)
  })
})
