import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MonthGrid from './MonthGrid.jsx'

afterEach(cleanup)

const match = (id, dateTime, calendarState) => ({
  id, dateTime, calendarState, homeTeamName: `H${id}`, awayTeamName: `A${id}`,
})

const matches = [
  match('1', '2026-09-19T10:00:00+02:00', 'OVERDUE'),
  match('2', '2026-09-19T12:00:00+02:00', 'UPCOMING'),
  match('3', '2026-09-19T13:00:00+02:00', 'UPCOMING'),
  match('4', '2026-09-26T13:00:00+02:00', 'POSTPONED'),
  match('5', '2026-09-26T14:00:00+02:00', 'OVERDUE'),
  match('6', '2026-09-26T15:00:00+02:00', 'PLAYED'),
]

const renderGrid = (props = {}) => render(
  <MonthGrid date="2026-09-16" today="2026-09-16" matches={matches} onDateSelect={vi.fn()} {...props} />,
)

describe('MonthGrid', () => {
  it('shows every day of the weeks that cover the month, with a header per weekday', () => {
    const { container } = renderGrid()

    // September 2026 spans Monday 31 August to Sunday 4 October.
    expect(screen.getAllByRole('button')).toHaveLength(35)
    expect(container.querySelectorAll('.month-grid-weekdays span')).toHaveLength(7)
  })

  it('gives each day cell a name with the long date, the total and the count per state', () => {
    renderGrid()

    const busy = screen.getByRole('button', { name: /19 de setembre/ })
    expect(busy).toHaveAccessibleName(/3 partits, Endarrerit 1, Proper 2/)
    expect(busy).toHaveTextContent('3')
    expect(busy.querySelectorAll('.month-cell-state')).toHaveLength(2)
    expect(busy.querySelector('.state-icon-overdue')).not.toBeNull()
    expect(busy.querySelector('.state-icon-upcoming')).not.toBeNull()
  })

  it('renders a day without matches as an empty, still selectable cell', () => {
    const onDateSelect = vi.fn()
    renderGrid({ onDateSelect })

    const empty = screen.getByRole('button', { name: /17 de setembre/ })
    expect(empty).toHaveAccessibleName(/0 partits/)
    expect(empty.querySelector('.month-cell-total')).toBeNull()
    expect(empty.querySelectorAll('.month-cell-state')).toHaveLength(0)
    fireEvent.click(empty)
    expect(onDateSelect).toHaveBeenCalledWith('2026-09-17')
  })

  it('uses native buttons, so Enter and Space activate them', () => {
    renderGrid()

    screen.getAllByRole('button').forEach((cell) => {
      expect(cell.tagName).toBe('BUTTON')
      expect(cell).toHaveAttribute('type', 'button')
    })
  })

  it('marks today and the days outside the month, and a day is a button that opens the day view', () => {
    const onDateSelect = vi.fn()
    renderGrid({ onDateSelect })

    const saturday = screen.getByRole('button', { name: /19 de setembre/ })
    expect(saturday).not.toHaveAttribute('aria-pressed')
    expect(saturday).toHaveAccessibleName(/Obre la vista de dia$/)
    fireEvent.click(saturday)
    expect(onDateSelect).toHaveBeenCalledWith('2026-09-19')
    expect(screen.getByRole('button', { name: /16 de setembre/ })).toHaveAttribute('aria-current', 'date')
    expect(screen.getByRole('button', { name: /17 de setembre/ })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('button', { name: /31 de agost/ })).toHaveClass('month-cell-outside')
    expect(screen.getByRole('button', { name: /, 1 de setembre/ })).not.toHaveClass('month-cell-outside')
  })

  it('shows the count of each state next to its icon', () => {
    renderGrid()

    const states = [...screen.getByRole('button', { name: /19 de setembre/ }).querySelectorAll('.month-cell-state')]
    expect(states.map((state) => state.textContent)).toEqual(['1', '2'])
  })

  it('compact cells keep the total and one icon, by priority overdue, awaiting result, postponed', () => {
    renderGrid({ compact: true })

    const overdueFirst = screen.getByRole('button', { name: /26 de setembre/ })
    expect(overdueFirst.querySelectorAll('.month-cell-state')).toHaveLength(1)
    expect(overdueFirst.querySelector('.state-icon-overdue')).not.toBeNull()
    expect(overdueFirst.querySelector('.state-icon-postponed')).toBeNull()
    expect(overdueFirst.querySelector('.month-cell-state')).toHaveTextContent('')
    expect(overdueFirst.querySelector('.month-cell-total')).toHaveTextContent('3')
    // Several states of the day collapse to the most urgent one; the label still names every state.
    const busy = screen.getByRole('button', { name: /19 de setembre/ })
    expect(busy.querySelectorAll('.month-cell-state')).toHaveLength(1)
    expect(busy).toHaveAccessibleName(/Endarrerit 1, Proper 2/)
  })

  it('compact cells without an urgent state show no icon', () => {
    renderGrid({ compact: true, matches: [match('1', '2026-09-19T10:00:00+02:00', 'PLAYED')] })

    expect(screen.getByRole('button', { name: /19 de setembre/ }).querySelectorAll('.month-cell-state'))
      .toHaveLength(0)
  })
})
