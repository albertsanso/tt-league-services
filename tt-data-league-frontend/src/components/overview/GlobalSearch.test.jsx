import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import GlobalSearch from './GlobalSearch.jsx'
import { useGlobalSearch } from '../../hooks/useSearch.js'

const navigateMock = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => navigateMock }
})

vi.mock('../../hooks/useSearch.js', () => ({
  useGlobalSearch: vi.fn(),
}))

function renderGlobalSearch() {
  return render(
    <MemoryRouter>
      <GlobalSearch />
    </MemoryRouter>,
  )
}

describe('GlobalSearch', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    useGlobalSearch.mockReturnValue({ data: null, loading: false })
  })

  afterEach(cleanup)

  it('shows a loading state while the search is in flight', () => {
    useGlobalSearch.mockReturnValue({ data: null, loading: true })
    renderGlobalSearch()

    fireEvent.change(screen.getByLabelText('Cerca clubs, jugadors o partits'), { target: { value: 'Anna' } })

    expect(screen.getByRole('status')).toHaveTextContent('Cercant...')
  })

  it('shows an overall empty state when every group has no results', () => {
    useGlobalSearch.mockReturnValue({ data: { players: [], clubs: [], matches: [] }, loading: false })
    renderGlobalSearch()

    fireEvent.change(screen.getByLabelText('Cerca clubs, jugadors o partits'), { target: { value: 'Anna' } })

    expect(screen.getByRole('status')).toHaveTextContent('No s’ha trobat cap resultat.')
  })

  it('groups results by entity type and links each result to its detail page', () => {
    useGlobalSearch.mockReturnValue({
      data: {
        players: [{
          id: 'player-id', name: 'Anna Player', canonicalPlayerId: 'player-id',
          sources: ['RFETM'], federatedPlayers: [],
        }],
        clubs: [{ id: 'club-id', name: 'Club Terrassa', source: 'RFETM' }],
        matches: [{ id: 'match-id', homeTeam: 'Club Terrassa', awayTeam: 'Club Barcelona' }],
      },
      loading: false,
    })
    renderGlobalSearch()

    fireEvent.change(screen.getByLabelText('Cerca clubs, jugadors o partits'), { target: { value: 'Anna' } })

    const playerLink = screen.getByRole('link', { name: 'Anna Player' })
    expect(playerLink).toHaveAttribute('href', expect.stringContaining('/jugadors/player-id'))
    const clubLink = screen.getByRole('link', { name: 'Club Terrassa' })
    expect(clubLink).toHaveAttribute('href', '/clubs/club-id?season=all&source=all')
    const matchLink = screen.getByRole('link', { name: 'Club Terrassa – Club Barcelona' })
    expect(matchLink).toHaveAttribute('href', '/partits/match-id')
  })

  it('shows a canonical-pending player without a link', () => {
    useGlobalSearch.mockReturnValue({
      data: {
        players: [{ id: 'player-id', name: 'Anna Player', canonicalPlayerId: null, federatedPlayers: [] }],
        clubs: [],
        matches: [],
      },
      loading: false,
    })
    renderGlobalSearch()

    fireEvent.change(screen.getByLabelText('Cerca clubs, jugadors o partits'), { target: { value: 'Anna' } })

    expect(screen.getByText('Anna Player')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Anna Player' })).not.toBeInTheDocument()
  })

  it('navigates to /cerca on submit regardless of dropdown state', () => {
    renderGlobalSearch()

    fireEvent.change(screen.getByLabelText('Cerca clubs, jugadors o partits'), { target: { value: 'Anna' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cercar' }))

    expect(navigateMock).toHaveBeenCalledWith('/cerca?q=Anna')
  })
})
