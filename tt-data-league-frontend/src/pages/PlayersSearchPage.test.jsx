import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import PlayersSearchPage from './PlayersSearchPage.jsx'
import { usePlayerSearch } from '../hooks/usePlayers.js'

vi.mock('../hooks/usePlayers.js', () => ({
  usePlayerSearch: vi.fn(),
}))

describe('PlayersSearchPage', () => {
  beforeEach(() => {
    usePlayerSearch.mockReturnValue({
      data: [{
        id: 'player-id',
        name: 'Anna Player',
        source: 'RFETM',
        canonicalPlayerId: 'player-id',
        sources: ['RFETM'],
        seasons: ['2024-2025', '2023-2024'],
        federatedPlayers: [],
      }],
      loading: false,
      error: null,
      retry: vi.fn(),
    })
  })

  it('shows the seasons in each search result', () => {
    render(
      <MemoryRouter initialEntries={['/players?q=Anna']}>
        <PlayersSearchPage />
      </MemoryRouter>,
    )

    expect(screen.getByText('Temporades: 2024-2025, 2023-2024')).toBeInTheDocument()
  })

  it('does not render a source selector or pass a source filter', () => {
    render(
      <MemoryRouter initialEntries={['/players?q=Anna&source=RFETM']}>
        <PlayersSearchPage />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('combobox', { name: 'Filtra per font' })).not.toBeInTheDocument()
    expect(usePlayerSearch).toHaveBeenLastCalledWith('Anna')
  })
})
