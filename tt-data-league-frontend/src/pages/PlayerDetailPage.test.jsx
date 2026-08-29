import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PlayerDetailPage from './PlayerDetailPage.jsx'
import { usePlayerDetails } from '../hooks/usePlayers.js'

vi.mock('../hooks/usePlayers.js', () => ({
  usePlayerDetails: vi.fn(),
}))

const details = {
  id: 'player-id',
  name: 'Anna Player',
  federatedPlayers: [{ id: 'federated-fctt', name: 'Anna FCTT', source: 'FCTT' }, { id: 'federated-rfetm', name: 'Anna RFETM', source: 'RFETM' }],
  registrations: [{ id: 'registration-fctt', name: 'Anna Player', source: 'FCTT', season: '2024-2025', license: '1' }, { id: 'registration-rfetm', name: 'Anna Player', source: 'RFETM', season: '2023-2024', license: '2' }],
  clubs: [{ id: 'club-id', name: 'Club Terrassa', source: 'FCTT', season: '2024-2025' }],
  competitions: [{ name: 'Preferent', source: 'FCTT', season: '2024-2025', matchCount: 3 }, { name: 'Divisió', source: 'RFETM', season: '2023-2024', matchCount: 1 }],
  matches: [
    { id: 'match-win', source: 'FCTT', season: '2024-2025', competition: 'Preferent', dateTime: '2025-01-01T12:00:00Z', homeTeam: 'Club Terrassa', awayTeam: 'Club Beta', playerTeam: 'Club Terrassa', homeGamesWon: 4, awayGamesWon: 2, result: 'win' },
    { id: 'match-loss', source: 'FCTT', season: '2024-2025', competition: 'Preferent', dateTime: '2025-01-08T12:00:00Z', homeTeam: 'Club Beta', awayTeam: 'Club Terrassa', playerTeam: 'Club Terrassa', homeGamesWon: 4, awayGamesWon: 1, result: 'loss' },
    { id: 'match-draw', source: 'FCTT', season: '2024-2025', competition: 'Preferent', dateTime: null, homeTeam: 'Club Terrassa', awayTeam: 'Club Alfa', playerTeam: 'Club Terrassa', homeGamesWon: null, awayGamesWon: null, result: 'draw' },
    { id: 'match-rfetm', source: 'RFETM', season: '2023-2024', competition: 'Divisió', dateTime: '2024-01-01T12:00:00Z', homeTeam: 'Club Gamma', awayTeam: 'Club Terrassa', playerTeam: 'Club Terrassa', homeGamesWon: 2, awayGamesWon: 4, result: 'win' },
  ],
  statistics: [{ source: 'FCTT', season: '2024-2025', matchesPlayed: 3, wins: 1, losses: 1, winPercentage: 50, averageScore: 3 }],
}

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.search}</output>
}

function renderPage(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/players/:playerId" element={<><PlayerDetailPage /><LocationProbe /></>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PlayerDetailPage', () => {
  beforeEach(() => {
    usePlayerDetails.mockReturnValue({ data: details, loading: false, error: null, retry: vi.fn() })
  })

  afterEach(cleanup)

  it('selects and persists the default statistics tab', async () => {
    renderPage('/players/player-id')

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('view=statistics'))
    expect(screen.getByRole('tab', { name: 'Estadístiques' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel', { name: 'Estadístiques' })).toHaveTextContent('Historial estadístic')
  })

  it('normalizes an invalid tab and retains a direct matches URL', async () => {
    renderPage('/players/player-id?view=matches')

    expect(screen.getByRole('tabpanel', { name: 'Partits' })).toHaveTextContent('Club Beta')
    expect(screen.getAllByText('—')).not.toHaveLength(0)

    cleanup()
    renderPage('/players/player-id?view=invalid')
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('view=statistics'))
  })

  it('changes tab with the keyboard without resetting query filters', () => {
    renderPage('/players/player-id?view=statistics&source=FCTT&season=2024-2025&competition=Preferent&chart=bar')

    const opponentsTab = screen.getByRole('tab', { name: 'Anàlisi d\'oponents' })
    opponentsTab.focus()
    expect(opponentsTab).toHaveFocus()
    fireEvent.keyDown(opponentsTab, { key: 'Enter' })

    expect(screen.getByRole('tabpanel', { name: 'Anàlisi d\'oponents' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('?view=opponents&source=FCTT&season=2024-2025&competition=Preferent&chart=bar')
  })

  it('groups opponents with draws and calculates decided-match percentages', () => {
    renderPage('/players/player-id?view=opponents')

    const rows = screen.getAllByRole('row').map((row) => row.textContent)
    expect(rows).toContain('Club Alfa1010—')
    expect(rows).toContain('Club Beta210150.0%')
    expect(rows).toContain('Club Gamma1100100.0%')
  })
})
