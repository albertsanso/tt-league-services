import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import UndatedMatches from './UndatedMatches.jsx'

afterEach(cleanup)

const undated = (id, overrides = {}) => ({
  id,
  dateTime: null,
  homeTeamName: `Home ${id}`,
  awayTeamName: `Away ${id}`,
  calendarState: 'UNDATED',
  competition: 'tercera',
  source: 'FCTT',
  ...overrides,
})

describe('UndatedMatches', () => {
  it('renders nothing without undated matches', () => {
    const { container } = render(<UndatedMatches matches={[]} onMatchSelect={vi.fn()} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('lists one native button per match and opens it on click', () => {
    const onMatchSelect = vi.fn()
    render(<UndatedMatches matches={[undated('a'), undated('b')]} onMatchSelect={onMatchSelect} />)

    expect(screen.getByRole('heading', { name: /Sense data/ })).toHaveTextContent('2 partits')
    const buttons = screen.getAllByRole('button')
    expect(buttons).toHaveLength(2)
    expect(buttons[0]).toHaveAccessibleName('Home a – Away a, FCTT, tercera, Sense data')
    fireEvent.click(buttons[1])
    expect(onMatchSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 'b' }))
  })
})
