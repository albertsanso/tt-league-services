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

const categoryDetails = {
  ...details,
  matches: [
    { id: 'beta-win-1', source: 'FCTT', season: '2024-2025', competition: 'Preferent', homeTeam: 'Club Terrassa', awayTeam: 'Club Beta', playerTeam: 'Club Terrassa', result: 'win' },
    { id: 'beta-win-2', source: 'FCTT', season: '2024-2025', competition: 'Preferent', homeTeam: 'Club Beta', awayTeam: 'Club Terrassa', playerTeam: 'Club Terrassa', result: 'win' },
    { id: 'beta-loss', source: 'FCTT', season: '2024-2025', competition: 'Preferent', homeTeam: 'Club Terrassa', awayTeam: 'Club Beta', playerTeam: 'Club Terrassa', result: 'loss' },
    { id: 'club-alfa-draw', source: 'FCTT', season: '2024-2025', competition: 'Preferent', homeTeam: 'Club Terrassa', awayTeam: 'Club Alfa', playerTeam: 'Club Terrassa', result: 'draw' },
    { id: 'club-gamma-win', source: 'FCTT', season: '2024-2025', competition: 'Preferent', homeTeam: 'Club Gamma', awayTeam: 'Club Terrassa', playerTeam: 'Club Terrassa', result: 'win' },
    { id: 'club-gamma-loss-1', source: 'FCTT', season: '2024-2025', competition: 'Preferent', homeTeam: 'Club Terrassa', awayTeam: 'Club Gamma', playerTeam: 'Club Terrassa', result: 'loss' },
    { id: 'club-gamma-loss-2', source: 'FCTT', season: '2024-2025', competition: 'Preferent', homeTeam: 'Club Gamma', awayTeam: 'Club Terrassa', playerTeam: 'Club Terrassa', result: 'loss' },
    { id: 'club-delta-loss-1', source: 'FCTT', season: '2024-2025', competition: 'Preferent', homeTeam: 'Club Terrassa', awayTeam: 'Club Delta', playerTeam: 'Club Terrassa', result: 'loss' },
    { id: 'club-delta-loss-2', source: 'FCTT', season: '2024-2025', competition: 'Preferent', homeTeam: 'Club Delta', awayTeam: 'Club Terrassa', playerTeam: 'Club Terrassa', result: 'loss' },
  ],
}

const drawOnlyDetails = {
  ...details,
  matches: [details.matches[2]],
}

const accentedDetails = {
  ...details,
  matches: [{
    ...details.matches[0],
    id: 'accented-opponent',
    awayTeam: 'Club Òrrius',
  }],
}

const manyOpponentsDetails = {
  ...categoryDetails,
  matches: [
    ...categoryDetails.matches,
    ...['Club Epsilon', 'Club Zeta', 'Club Eta'].map((name, index) => ({
      id: `favorable-${index}`,
      source: 'FCTT',
      season: '2024-2025',
      competition: 'Preferent',
      homeTeam: 'Club Terrassa',
      awayTeam: name,
      playerTeam: 'Club Terrassa',
      result: 'win',
    })),
  ],
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

  it('defaults the opponent sub-tab and persists it in the URL', async () => {
    renderPage('/players/player-id?view=opponents')

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('?view=opponents&opponentView=categorization'))
    expect(screen.getByRole('tab', { name: "Categorització d'oponents" })).toHaveAttribute('aria-selected', 'true')
  })

  it('switches opponent sub-tabs without resetting filters', () => {
    renderPage('/players/player-id?view=opponents&opponentView=categorization&source=FCTT&season=2024-2025&competition=Preferent&chart=bar')

    fireEvent.click(screen.getByRole('tab', { name: "Cerca d'oponents" }))

    expect(screen.getByTestId('location')).toHaveTextContent('?view=opponents&opponentView=search&source=FCTT&season=2024-2025&competition=Preferent&chart=bar')
  })

  it('passes the active selectors to the server-backed details request', async () => {
    usePlayerDetails.mockClear()
    renderPage('/players/player-id?view=opponents&source=FCTT&season=2024-2025&competition=Preferent')

    expect(usePlayerDetails).toHaveBeenLastCalledWith(
      'player-id', 'FCTT', '2024-2025', 'Preferent',
    )

    fireEvent.click(screen.getByRole('radio', { name: 'RFETM' }))

    await waitFor(() => expect(usePlayerDetails).toHaveBeenLastCalledWith(
      'player-id', 'RFETM', '', '',
    ))
  })

  it('omits the all-competitions value from the filtered request', async () => {
    usePlayerDetails.mockClear()
    renderPage('/players/player-id?view=opponents&source=FCTT&season=2024-2025&competition=Preferent')

    fireEvent.change(screen.getByRole('combobox', { name: 'Competició' }), { target: { value: '' } })

    await waitFor(() => expect(usePlayerDetails).toHaveBeenLastCalledWith(
      'player-id', 'FCTT', '2024-2025', '',
    ))
  })

  it('normalizes an invalid opponent sub-tab', async () => {
    renderPage('/players/player-id?view=opponents&opponentView=invalid')

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('opponentView=categorization'))
    expect(screen.getByRole('tab', { name: "Categorització d'oponents" })).toHaveAttribute('aria-selected', 'true')
  })

  it('categorizes opponents and excludes draw-only records from categories', () => {
    usePlayerDetails.mockReturnValue({ data: categoryDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=categorization')

    const tables = screen.getAllByRole('table')
    expect(tables[0]).toHaveTextContent('Club Beta')
    expect(tables[0]).toHaveTextContent('66.7%')
    expect(tables[1]).toHaveTextContent('Club Gamma')
    expect(tables[2]).toHaveTextContent('Club Delta')
    expect(tables[0]).not.toHaveTextContent('Club Alfa')
    expect(tables[1]).not.toHaveTextContent('Club Alfa')
    expect(tables[2]).not.toHaveTextContent('Club Alfa')
  })

  it('shows a specific empty state for every empty category', () => {
    usePlayerDetails.mockReturnValue({ data: drawOnlyDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    expect(screen.getByText('No hi ha oponents favorables per als filtres seleccionats.')).toBeInTheDocument()
    expect(screen.getByText('No hi ha oponents difícils per als filtres seleccionats.')).toBeInTheDocument()
    expect(screen.getByText('No hi ha oponents problemàtics per als filtres seleccionats.')).toBeInTheDocument()
  })

  it('shows only three category rows and exposes the remaining rows on demand', () => {
    usePlayerDetails.mockReturnValue({ data: manyOpponentsDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    const favorableTable = screen.getAllByRole('table')[0]
    expect(favorableTable.querySelectorAll('tbody tr')).toHaveLength(3)
    expect(screen.getByText('Mostra 1 oponents més')).toBeInTheDocument()

    fireEvent.click(screen.getByText('Mostra 1 oponents més'))
    const more = screen.getByText('Mostra 1 oponents més').closest('details')
    expect(more.querySelectorAll('tbody tr')).toHaveLength(1)
    expect(more).toHaveTextContent('Club Beta')
  })

  it('removes the legacy detail sections from the player detail view', () => {
    renderPage('/players/player-id')

    expect(screen.queryByText('Registres federats')).not.toBeInTheDocument()
    expect(screen.queryByText('Inscripcions per temporada')).not.toBeInTheDocument()
    expect(screen.queryByText('Clubs associats')).not.toBeInTheDocument()
    expect(screen.queryByText('Competicions')).not.toBeInTheDocument()
  })

  it('describes opponent tables for non-visual table readers', () => {
    usePlayerDetails.mockReturnValue({ data: categoryDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    const table = screen.getAllByRole('table')[0]
    const description = document.getElementById(table.getAttribute('aria-describedby'))
    expect(description).toHaveTextContent('1 oponents en aquesta categoria.')
  })

  it('filters opponents by an accented substring and clears search state on return', () => {
    usePlayerDetails.mockReturnValue({ data: accentedDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=search')

    const search = screen.getByRole('searchbox', { name: 'Cerca un oponent' })
    fireEvent.change(search, { target: { value: 'òrr' } })
    expect(screen.getByRole('table')).toHaveTextContent('Club Òrrius')

    fireEvent.click(screen.getByRole('tab', { name: "Categorització d'oponents" }))
    fireEvent.click(screen.getByRole('tab', { name: "Cerca d'oponents" }))
    expect(screen.getByRole('searchbox', { name: 'Cerca un oponent' })).toHaveValue('')
  })
})
