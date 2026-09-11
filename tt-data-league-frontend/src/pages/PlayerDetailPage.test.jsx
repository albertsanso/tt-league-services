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

function opponentMatches(name, wins, losses, draws = 0) {
  return [...Array(wins)].map((_, index) => ({
    id: `${name}-win-${index}`,
    source: 'FCTT',
    season: '2024-2025',
    competition: 'Preferent',
    homeTeam: 'Club Terrassa',
    awayTeam: name,
    playerTeam: 'Club Terrassa',
    result: 'win',
  })).concat([...Array(losses)].map((_, index) => ({
    id: `${name}-loss-${index}`,
    source: 'FCTT',
    season: '2024-2025',
    competition: 'Preferent',
    homeTeam: 'Club Terrassa',
    awayTeam: name,
    playerTeam: 'Club Terrassa',
    result: 'loss',
  }))).concat([...Array(draws)].map((_, index) => ({
    id: `${name}-draw-${index}`,
    source: 'FCTT',
    season: '2024-2025',
    competition: 'Preferent',
    homeTeam: 'Club Terrassa',
    awayTeam: name,
    playerTeam: 'Club Terrassa',
    result: 'draw',
  })))
}

const sortingDetails = {
  ...details,
  matches: [
    ...opponentMatches('Club Beta', 2, 0),
    ...opponentMatches('Club Gamma', 1, 0),
    ...opponentMatches('Club Alfa', 2, 1),
    ...opponentMatches('Club Omega', 2, 1),
    ...opponentMatches('Club Delta', 1, 2),
    ...opponentMatches('Club Epsilon', 2, 3),
    ...opponentMatches('Club Zeta', 1, 4),
    ...opponentMatches('Club Eta', 0, 2),
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

    expect(screen.getByRole('tabpanel', { name: 'Partits' })
      .querySelectorAll('.match-opponent-list .match-game-row, .match-result-list .match-game-row')).toHaveLength(0)
    expect(screen.getAllByText('—')).not.toHaveLength(0)

    cleanup()
    renderPage('/players/player-id?view=invalid')
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('view=statistics'))
  })

  it('shows source-scoped player opponents and player-level results in match rows', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [{
          ...details.matches[0],
          result: 'loss',
          games: [{
            id: 'game-1',
            gameNumber: 1,
            type: 'INDIVIDUAL',
            result: 'win',
            homeSetsWon: 3,
            awaySetsWon: 1,
            opponents: [{
              playerId: 'opponent-id',
              federatedPlayerId: 'opponent-federated-id',
              playerSeasonId: 'opponent-season-id',
              name: 'Opponent Player',
              source: 'FCTT',
              season: '2024-2025',
              available: true,
            }],
          }],
        }],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage('/players/player-id?view=matches')

    const row = screen.getByRole('table').querySelector('tbody tr')
    expect(row).toHaveTextContent('Opponent Player')
    expect(row.querySelector('td:last-child')).toHaveTextContent('Club Beta')
    expect(row.querySelectorAll('.match-opponent-list .match-game-row')).toHaveLength(1)
    expect(row.querySelectorAll('.match-result-list .match-game-row')).toHaveLength(1)
    const resultRow = row.querySelector('.match-result-list .match-game-row')
    expect(resultRow).toHaveTextContent('3-1')
    expect(resultRow).toHaveTextContent('Victòria')
    expect(resultRow).toHaveClass('match-result-win')
    const matchScore = row.querySelector('td:nth-last-child(2) .match-game-row')
    expect(matchScore).toHaveTextContent('4 — 2')
    expect(matchScore).not.toHaveTextContent('Derrota')
    expect(matchScore).toHaveClass('match-result-loss')
    expect(row.querySelector('td:last-child')).toHaveTextContent('Club Beta')
  })

  it('shows round, group number, and phase for each match, falling back to unavailable when missing', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [
          { ...details.matches[0], round: 3, groupNumber: 2, phase: 'Regular Season' },
          { ...details.matches[1], round: 4, groupNumber: null, phase: null },
        ],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage('/players/player-id?view=matches')

    const rows = screen.getByRole('table').querySelectorAll('tbody tr')
    expect(rows[0]).toHaveTextContent('4')
    expect(rows[0]).toHaveTextContent('No disponible')
    expect(rows[1]).toHaveTextContent('3')
    expect(rows[1]).toHaveTextContent('2')
    expect(rows[1]).toHaveTextContent('Regular Season')
  })

  it('styles a losing game result red beside its set score', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [{
          ...details.matches[0],
          games: [{
            id: 'game-loss',
            gameNumber: 1,
            type: 'INDIVIDUAL',
            result: 'loss',
            homeSetsWon: 1,
            awaySetsWon: 3,
            opponents: [{
              playerId: 'opponent-loss',
              name: 'Opponent Loss',
              available: true,
              source: 'FCTT',
              season: '2024-2025',
            }],
          }],
        }],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage('/players/player-id?view=matches')

    const resultRow = screen.getByRole('table').querySelector('.match-result-list .match-game-row')
    expect(resultRow).toHaveTextContent('1-3')
    expect(resultRow).toHaveTextContent('Derrota')
    expect(resultRow).toHaveClass('match-result-loss')
  })

  it('deduplicates doubles opponents and omits unavailable game rows', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [{
          ...details.matches[0],
          games: [
            {
              id: 'game-1',
              gameNumber: 1,
              type: 'DOUBLES',
              result: 'win',
              homeSetsWon: 3,
              awaySetsWon: 2,
              opponents: [
                { playerId: 'opponent-a', name: 'Opponent A', available: true, source: 'FCTT', season: '2024-2025' },
                { playerId: 'opponent-b', name: 'Opponent B', available: true, source: 'FCTT', season: '2024-2025' },
              ],
            },
            {
              id: 'game-2',
              gameNumber: 2,
              type: 'DOUBLES',
              result: 'unavailable',
              homeSetsWon: null,
              awaySetsWon: null,
              opponents: [{ playerId: null, name: null, available: false, source: 'FCTT', season: '2024-2025' }],
            },
          ],
        }],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage('/players/player-id?view=matches')

    const row = screen.getByRole('table').querySelector('tbody tr')
    expect(row).toHaveTextContent('Opponent A, Opponent B')
    expect(row.querySelectorAll('.match-opponent-list .match-game-row')).toHaveLength(1)
    expect(row.querySelectorAll('.match-result-list .match-game-row')).toHaveLength(1)
    const resultRow = row.querySelectorAll('.match-result-list .match-game-row')[0]
    expect(resultRow).toHaveTextContent('3-2')
    expect(resultRow).toHaveTextContent('Victòria')
    expect(resultRow).toHaveClass('match-result-win')
    expect(row.querySelector('td:last-child')).toHaveTextContent('Club Beta')
  })

  it('shows unavailable values when game opponents have no identity', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [{
          ...details.matches[0],
          games: [{
            id: 'game-1',
            gameNumber: 1,
            type: 'INDIVIDUAL',
            result: 'unavailable',
            homeSetsWon: null,
            awaySetsWon: null,
            opponents: [{ playerId: null, federatedPlayerId: null, playerSeasonId: null, name: null, available: false }],
          }],
        }],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage('/players/player-id?view=matches')

    const row = screen.getByRole('table').querySelector('tbody tr')
    expect(row.querySelectorAll('.match-opponent-list .match-game-row')).toHaveLength(0)
    expect(row.querySelectorAll('.match-result-list .match-game-row')).toHaveLength(0)
    expect(row.querySelector('td:last-child')).toHaveTextContent('Club Beta')
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

  it('keeps the detail view visible while a selector request refreshes data', () => {
    usePlayerDetails.mockReturnValue({ data: details, loading: true, error: null, retry: vi.fn() })
    renderPage('/players/player-id?source=RFETM')

    expect(screen.getByRole('heading', { name: 'Anna Player' })).toBeInTheDocument()
    expect(screen.queryByText('Carregant el jugador...')).not.toBeInTheDocument()
    expect(screen.getByText('Actualitzant les dades del jugador...')).toHaveAttribute('role', 'status')
  })

  it('sizes the season selector for its longest label', () => {
    renderPage('/players/player-id')

    expect(screen.getByRole('group', { name: 'Temporada' }))
      .toHaveStyle({ '--season-label-width': '20ch' })
  })

  it('places season across the first row and source beside competition on the second', () => {
    renderPage('/players/player-id')

    const filters = document.querySelector('.club-filters')
    const season = screen.getByRole('group', { name: 'Temporada' })
    const competition = screen.getByRole('combobox', { name: 'Competició' }).closest('label')
    const source = filters.querySelector('.source-options')
    expect(source).toBeInTheDocument()
    expect(filters.contains(season)).toBe(true)
    expect(filters.contains(competition)).toBe(true)
    expect(season.compareDocumentPosition(competition) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(season.compareDocumentPosition(source) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(season).toHaveClass('season-slider')
    expect(competition).toHaveClass('player-competition-filter')
    expect(source).toHaveClass('source-options')
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

  it('derives opponent analysis results from each game result, not the overall match result', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [{
          ...details.matches[0],
          result: 'win',
          games: [{
            id: 'game-1',
            gameNumber: 1,
            type: 'INDIVIDUAL',
            result: 'loss',
            homeSetsWon: 1,
            awaySetsWon: 3,
            opponents: [{
              playerId: 'opponent-contradiction',
              name: 'Opponent Contradiction',
              available: true,
              source: 'FCTT',
              season: '2024-2025',
            }],
          }],
        }],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage('/players/player-id?view=matches')
    const resultRow = screen.getByRole('table').querySelector('.match-result-list .match-game-row')
    expect(resultRow).toHaveClass('match-result-loss')
    cleanup()

    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [{
          ...details.matches[0],
          result: 'win',
          games: [{
            id: 'game-1',
            gameNumber: 1,
            type: 'INDIVIDUAL',
            result: 'loss',
            homeSetsWon: 1,
            awaySetsWon: 3,
            opponents: [{
              playerId: 'opponent-contradiction',
              name: 'Opponent Contradiction',
              available: true,
              source: 'FCTT',
              season: '2024-2025',
            }],
          }],
        }],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })
    renderPage('/players/player-id?view=opponents&opponentView=categorization')

    expect(screen.getByText('No hi ha oponents favorables per als filtres seleccionats.')).toBeInTheDocument()
    expect(screen.getByText('No hi ha oponents difícils per als filtres seleccionats.')).toBeInTheDocument()
    const problemTable = screen.getByRole('table')
    expect(problemTable).toHaveTextContent('Opponent Contradiction')
    expect(problemTable).toHaveTextContent('0.0%')
  })

  it('counts only the first game result when the same opponent appears twice in one match', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [{
          ...details.matches[0],
          result: 'win',
          games: [
            {
              id: 'game-1',
              gameNumber: 1,
              type: 'DOUBLES',
              result: 'win',
              homeSetsWon: 3,
              awaySetsWon: 1,
              opponents: [{
                playerId: 'opponent-repeat',
                name: 'Opponent Repeat',
                available: true,
                source: 'FCTT',
                season: '2024-2025',
              }],
            },
            {
              id: 'game-2',
              gameNumber: 2,
              type: 'DOUBLES',
              result: 'loss',
              homeSetsWon: 1,
              awaySetsWon: 3,
              opponents: [{
                playerId: 'opponent-repeat',
                name: 'Opponent Repeat',
                available: true,
                source: 'FCTT',
                season: '2024-2025',
              }],
            },
          ],
        }],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage('/players/player-id?view=opponents&opponentView=categorization')

    const favorableTable = screen.getAllByRole('table')[0]
    expect(favorableTable).toHaveTextContent('Opponent Repeat')
    const row = [...favorableTable.querySelectorAll('tbody tr')].find((tr) => tr.textContent.includes('Opponent Repeat'))
    expect(row.querySelectorAll('td')[1]).toHaveTextContent('1')
    expect(row).toHaveTextContent('100.0%')
  })

  it('sorts every categorized table by percentage, matches, then deterministic name', () => {
    usePlayerDetails.mockReturnValue({ data: sortingDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=categorization')

    const tables = screen.getAllByRole('table')
    const names = (table) => [...table.querySelectorAll('tbody tr td:first-child')].map((cell) => cell.textContent)

    fireEvent.click(screen.getByRole('button', { name: 'Mostra 1 oponents més' }))

    expect(names(tables[0])).toEqual(['Club Beta', 'Club Gamma', 'Club Alfa', 'Club Omega'])
    expect(names(tables[1])).toEqual(['Club Epsilon', 'Club Delta'])
    expect(names(tables[2])).toEqual(['Club Zeta', 'Club Eta'])
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
    const moreButton = screen.getByRole('button', { name: 'Mostra 1 oponents més' })
    expect(moreButton).toBeInTheDocument()

    fireEvent.click(moreButton)
    expect(favorableTable.querySelectorAll('tbody tr')).toHaveLength(4)
    expect(screen.queryByRole('button', { name: 'Mostra 1 oponents més' })).not.toBeInTheDocument()
  })

  it('sorts statistics by descending season', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        statistics: [
          { source: 'FCTT', season: '2022-2023', matchesPlayed: 1, wins: 1, losses: 0, winPercentage: 100 },
          { source: 'FCTT', season: '2024-2025', matchesPlayed: 3, wins: 2, losses: 1, winPercentage: 66.7 },
        ],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })
    renderPage('/players/player-id')

    const rows = screen.getByRole('table').querySelectorAll('tbody tr')
    expect(rows[0]).toHaveTextContent('2024-2025')
    expect(rows[1]).toHaveTextContent('2022-2023')
    const chartGroups = document.querySelectorAll('.chart-season-label')
    expect(chartGroups[0]).toHaveTextContent('2022-2023')
    expect(chartGroups[1]).toHaveTextContent('2024-2025')
  })

  it('keeps the played-match count visible when a season has a percentage', () => {
    renderPage('/players/player-id')

    const row = screen.getByRole('table').querySelector('tbody tr')
    expect(row).toHaveTextContent('2024-2025')
    expect(row).toHaveTextContent('3')
    expect(row).toHaveTextContent('50.0%')
  })

  it('shows an average score column sourced from the per-season statistics', () => {
    renderPage('/players/player-id')

    const row = screen.getByRole('table').querySelector('tbody tr')
    expect(row).toHaveTextContent('3.0')
  })

  it('computes and renders a career summary from singles and doubles matches across seasons', () => {
    const careerMatches = [
      matchWithGames('career-m1', '2024-03-01T12:00:00Z', 'win', '2024-2025', [{ type: 'INDIVIDUAL', result: 'win', homeSetsWon: 3, awaySetsWon: 1 }]),
      matchWithGames('career-m2', '2024-02-01T12:00:00Z', 'win', '2024-2025', [{ type: 'DOUBLES', result: 'win', homeSetsWon: 3, awaySetsWon: 0 }]),
      matchWithGames('career-m3', '2024-01-01T12:00:00Z', 'loss', '2024-2025', [{ type: 'INDIVIDUAL', result: 'loss', homeSetsWon: 1, awaySetsWon: 3 }]),
      matchWithGames('career-m4', '2023-05-01T12:00:00Z', 'win', '2023-2024', [{ type: 'DOUBLES', result: 'win', homeSetsWon: 3, awaySetsWon: 2 }]),
    ]
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: careerMatches,
        statistics: [
          { source: 'FCTT', season: '2024-2025', matchesPlayed: 3, wins: 2, losses: 1, winPercentage: 66.7, averageScore: 3.2 },
          { source: 'FCTT', season: '2023-2024', matchesPlayed: 1, wins: 1, losses: 0, winPercentage: 100, averageScore: 4 },
        ],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })
    renderPage('/players/player-id')

    const summary = document.querySelector('.career-summary')
    expect(summary).toHaveTextContent('4')
    expect(summary).toHaveTextContent('75.0%')
    expect(summary).toHaveTextContent('50.0%')
    expect(summary).toHaveTextContent('100.0%')
    expect(summary).toHaveTextContent('1.0')
    expect(summary).toHaveTextContent('Ratxa de 2 victòries')
  })

  it('updates the career summary when the source filter changes', () => {
    usePlayerDetails.mockImplementation((playerId, source) => ({
      data: source === 'RFETM'
        ? {
            ...details,
            matches: [details.matches[3]],
            statistics: [{ source: 'RFETM', season: '2023-2024', matchesPlayed: 1, wins: 1, losses: 0, winPercentage: 100, averageScore: 3 }],
          }
        : details,
      loading: false,
      error: null,
      retry: vi.fn(),
    }))
    renderPage('/players/player-id')

    expect(document.querySelector('.career-summary')).toHaveTextContent('4')

    fireEvent.click(screen.getByRole('radio', { name: 'RFETM' }))

    expect(document.querySelector('.career-summary')).toHaveTextContent('1')
  })

  it('shows a sensible empty state with no career summary when there are no statistics', () => {
    usePlayerDetails.mockReturnValue({
      data: { ...details, matches: [], statistics: [] },
      loading: false,
      error: null,
      retry: vi.fn(),
    })
    renderPage('/players/player-id')

    expect(screen.getByText('No hi ha dades estadístiques disponibles per als filtres seleccionats.')).toBeInTheDocument()
    expect(document.querySelector('.career-summary')).not.toBeInTheDocument()
  })

  it('renders one match result spectrum point per filtered match with the expected quality tier, based on the individual game score', () => {
    const spectrumMatches = [
      matchWithGames('spectrum-strong-win', '2024-01-01T12:00:00Z', 'win', '2024-2025', [{ type: 'INDIVIDUAL', result: 'win', homeSetsWon: 3, awaySetsWon: 0 }], { homeGamesWon: 4, awayGamesWon: 2 }),
      matchWithGames('spectrum-close-win', '2024-01-08T12:00:00Z', 'win', '2024-2025', [{ type: 'INDIVIDUAL', result: 'win', homeSetsWon: 3, awaySetsWon: 2 }], { homeGamesWon: 3, awayGamesWon: 2 }),
      matchWithGames('spectrum-loss', '2024-01-15T12:00:00Z', 'loss', '2024-2025', [{ type: 'INDIVIDUAL', result: 'loss', homeSetsWon: 1, awaySetsWon: 3 }], { homeGamesWon: 1, awayGamesWon: 4 }),
    ]
    usePlayerDetails.mockReturnValue({
      data: { ...details, matches: spectrumMatches },
      loading: false,
      error: null,
      retry: vi.fn(),
    })
    renderPage('/players/player-id')

    const points = document.querySelectorAll('.chart-match-spectrum .spectrum-point')
    expect(points).toHaveLength(3)
    expect([...points].map((point) => point.getAttribute('class'))).toEqual([
      'spectrum-point quality-fill-strong-win',
      'spectrum-point quality-fill-close-win',
      'spectrum-point quality-fill-loss',
    ])

    const axisTicks = [...document.querySelectorAll('.chart-match-spectrum .spectrum-axis-tick')].map((tick) => tick.firstChild.textContent)
    expect(axisTicks).toEqual(['3-0', 'Victòria clara', '3-2', 'Empat', 'Derrota ajustada', '1-3', 'Derrota contundent'])
  })

  it('shows a date timeline on the match result spectrum x-axis', () => {
    const timelineMatches = [
      matchWithGames('timeline-1', '2024-01-01T12:00:00Z', 'win', '2024-2025', [{ type: 'INDIVIDUAL', result: 'win', homeSetsWon: 3, awaySetsWon: 0 }]),
      matchWithGames('timeline-2', '2024-06-15T12:00:00Z', 'loss', '2024-2025', [{ type: 'INDIVIDUAL', result: 'loss', homeSetsWon: 1, awaySetsWon: 3 }]),
    ]
    usePlayerDetails.mockReturnValue({
      data: { ...details, matches: timelineMatches },
      loading: false,
      error: null,
      retry: vi.fn(),
    })
    renderPage('/players/player-id')

    const dateTicks = [...document.querySelectorAll('.chart-match-spectrum .spectrum-date-tick')].map((tick) => tick.textContent)
    expect(dateTicks).toEqual(['1/1/2024', '15/6/2024'])
  })

  it('deduplicates repeated competition matches before aggregating statistics', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [
          details.matches[0],
          details.matches[0],
          details.matches[1],
        ],
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })
    renderPage('/players/player-id?competition=Preferent')

    const row = screen.getByRole('table').querySelector('tbody tr')
    expect(row).toHaveTextContent('2024-2025')
    expect(row).toHaveTextContent('2')
    expect(row).toHaveTextContent('50.0%')
  })

  it('uses the connected scatter chart with a percentage scale by default', () => {
    renderPage('/players/player-id')

    const chart = document.querySelector('.history-connected-chart')
    expect(chart).toHaveAttribute('aria-label', expect.stringContaining('Escala vertical de partits jugats i escala de percentatge de victòries del 0% al 100%'))
    expect(chart.querySelector('.matches-axis-label')).toHaveTextContent('Partits jugats')
    expect(chart.querySelector('.percentage-axis-label')).toHaveTextContent('Victòries (%)')
    expect([...chart.querySelectorAll('.percentage-axis-tick')].map((label) => label.textContent))
      .toEqual(['0%', '25%', '50%', '75%', '100%'])
    expect([...chart.querySelectorAll('.matches-axis-tick')].map((label) => label.textContent))
      .toEqual(['0', '1', '2', '3'])
    expect(chart.querySelectorAll('.percentage-grid-line')).toHaveLength(5)
    expect(chart.querySelectorAll('.matches-point')).toHaveLength(1)
    expect(chart.querySelectorAll('.matches-point')[0].tagName).toBe('path')
    expect(chart.querySelectorAll('.matches-point')[0]).toHaveAttribute('d', 'M 317 10 L 329 22 M 329 10 L 317 22')
    expect(chart.querySelectorAll('.wins-point')).toHaveLength(1)
    expect(chart.querySelectorAll('.wins-point')[0].tagName).toBe('polygon')
    expect(chart.querySelectorAll('.wins-point')[0]).toHaveAttribute('points', '323,94 317,106 329,106')
    expect(screen.queryByLabelText('Tipus de gràfic')).not.toBeInTheDocument()
  })

  it('does not expose a chart type selector for connected scatter charts', () => {
    renderPage('/players/player-id?chart=connected-scatter')

    const chart = document.querySelector('.history-connected-chart')
    expect(chart).toHaveAttribute('aria-label', expect.stringContaining('Escala vertical de partits jugats i escala de percentatge de victòries del 0% al 100%'))
    expect(chart.querySelector('.matches-axis-label')).toHaveTextContent('Partits jugats')
    expect(chart.querySelector('.percentage-axis-label')).toHaveTextContent('Victòries (%)')
    expect(chart.querySelectorAll('.percentage-grid-line')).toHaveLength(5)
    expect([...chart.querySelectorAll('.percentage-axis-tick')].map((label) => label.textContent))
      .toEqual(['0%', '25%', '50%', '75%', '100%'])
    expect(screen.queryByLabelText('Tipus de gràfic')).not.toBeInTheDocument()
  })

  it('sorts matches newest first and paginates after ten rows', () => {
    const matches = Array.from({ length: 11 }, (_, index) => ({
      ...details.matches[0],
      id: `match-${index}`,
      dateTime: new Date(Date.UTC(2025, 0, index + 1)).toISOString(),
      awayTeam: `Club ${index}`,
      games: [{
        id: `game-${index}`,
        gameNumber: 1,
        type: 'INDIVIDUAL',
        result: 'win',
        homeSetsWon: 3,
        awaySetsWon: 1,
        opponents: [{
          playerId: `opponent-${index}`,
          name: `Opponent ${index}`,
          available: true,
          source: 'FCTT',
          season: '2024-2025',
        }],
      }],
    }))
    usePlayerDetails.mockReturnValue({ data: { ...details, matches }, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=matches')

    expect(screen.getByRole('table').querySelectorAll('tbody tr')).toHaveLength(10)
    expect(screen.getByRole('table').querySelector('tbody tr')).toHaveTextContent('Opponent 10')
    expect(screen.getByText('Pàgina 1 de 2')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Següent' }))
    expect(screen.getByRole('table').querySelectorAll('tbody tr')).toHaveLength(1)
    expect(screen.getByRole('table').querySelector('tbody tr')).toHaveTextContent('Opponent 0')
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

  it('uses a contrasted opponent search input', () => {
    usePlayerDetails.mockReturnValue({ data: accentedDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=search')

    expect(screen.getByRole('searchbox', { name: 'Cerca un oponent' }))
      .toHaveClass('opponent-search-input')
  })

  function matchWithGames(id, dateTime, result, season, gamesSpec, gamesWon = {}) {
    return {
      id,
      source: 'FCTT',
      season,
      competition: 'Preferent',
      dateTime,
      homeTeam: 'Club Terrassa',
      awayTeam: 'Club Beta',
      playerTeam: 'Club Terrassa',
      homeGamesWon: gamesWon.homeGamesWon ?? null,
      awayGamesWon: gamesWon.awayGamesWon ?? null,
      result,
      games: gamesSpec.map((spec, index) => ({
        id: `${id}-g${index}`,
        gameNumber: index + 1,
        type: spec.type,
        result: spec.result,
        homeSetsWon: spec.homeSetsWon,
        awaySetsWon: spec.awaySetsWon,
        opponents: [{
          playerId: `${id}-opp-${index}`,
          name: 'Opponent',
          available: true,
          source: 'FCTT',
          season,
        }],
      })),
    }
  }

  function gameMatch(id, dateTime, result, homeSetsWon, awaySetsWon, opponentName = 'Club Beta', opponentId = 'beta-player') {
    return {
      id,
      source: 'FCTT',
      season: '2024-2025',
      competition: 'Preferent',
      dateTime,
      homeTeam: 'Club Terrassa',
      awayTeam: opponentName,
      playerTeam: 'Club Terrassa',
      result,
      games: [{
        id: `${id}-game`,
        gameNumber: 1,
        type: 'INDIVIDUAL',
        result,
        homeSetsWon,
        awaySetsWon,
        opponents: [{
          playerId: opponentId,
          name: opponentName,
          available: true,
          source: 'FCTT',
          season: '2024-2025',
        }],
      }],
    }
  }

  it('shows a quality indicator for each recent result based on the set-score margin', () => {
    const qualityDetails = {
      ...details,
      matches: [
        gameMatch('quality-1', '2025-01-01T12:00:00Z', 'win', 3, 0),
        gameMatch('quality-2', '2025-01-08T12:00:00Z', 'win', 3, 2),
        gameMatch('quality-3', '2025-01-15T12:00:00Z', 'loss', 1, 3),
      ],
    }
    usePlayerDetails.mockReturnValue({ data: qualityDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=search')

    const chips = screen.getByRole('table').querySelectorAll('.opponent-form-chip')
    expect([...chips].map((chip) => chip.getAttribute('title'))).toEqual([
      'Victòria contundent',
      'Victòria ajustada',
      'Derrota clara',
    ])
  })

  it('shows the current streak against an opponent', () => {
    const streakDetails = {
      ...details,
      matches: [
        gameMatch('streak-1', '2025-01-01T12:00:00Z', 'win', 3, 1),
        gameMatch('streak-2', '2025-01-08T12:00:00Z', 'win', 3, 0),
        gameMatch('streak-3', '2025-01-15T12:00:00Z', 'win', 3, 2),
      ],
    }
    usePlayerDetails.mockReturnValue({ data: streakDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=search')

    expect(screen.getByRole('table')).toHaveTextContent('Ratxa de 3 victòries')
  })

  it('sorts opponents by win percentage when the sort control changes', () => {
    usePlayerDetails.mockReturnValue({ data: sortingDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=search')

    fireEvent.change(screen.getByRole('combobox', { name: 'Ordena per' }), { target: { value: 'winPercentage' } })

    expect(screen.getByTestId('location')).toHaveTextContent('opponentSort=winPercentage')
    fireEvent.click(screen.getByRole('button', { name: 'Mostra 5 oponents més' }))
    const names = [...screen.getByRole('table').querySelectorAll('tbody tr td:first-child')].map((cell) => cell.textContent)
    expect(names).toEqual(['Club Beta', 'Club Gamma', 'Club Alfa', 'Club Omega', 'Club Epsilon', 'Club Delta', 'Club Zeta', 'Club Eta'])
  })

  it('expands a head-to-head history for an opponent and hides it again', () => {
    const headToHeadDetails = {
      ...details,
      matches: [
        gameMatch('h2h-1', '2025-01-01T12:00:00Z', 'win', 3, 1),
        gameMatch('h2h-2', '2025-01-08T12:00:00Z', 'loss', 1, 3),
      ],
    }
    usePlayerDetails.mockReturnValue({ data: headToHeadDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=search')

    expect(screen.queryByText('Historial cara a cara amb Club Beta')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Mostra l’historial cara a cara' }))

    const heading = screen.getByText('Historial cara a cara amb Club Beta')
    expect(heading).toBeInTheDocument()
    const detailTable = heading.closest('.opponent-history-detail').querySelector('table')
    const rows = [...detailTable.tBodies[0].rows]
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('1 — 3')
    expect(rows[1]).toHaveTextContent('3 — 1')

    fireEvent.click(screen.getByRole('button', { name: 'Amaga l’historial cara a cara' }))
    expect(screen.queryByText('Historial cara a cara amb Club Beta')).not.toBeInTheDocument()
  })

  it('labels every match row cell for the stacked mobile layout', () => {
    renderPage('/players/player-id?view=matches')

    const row = screen.getByRole('table').querySelector('tbody tr')
    const labels = [...row.querySelectorAll('td')].map((cell) => cell.getAttribute('data-label'))
    expect(labels).toEqual(['Data', 'Font', 'Temporada', 'Competició', 'Jornada', 'Grup', 'Fase', 'Oponent', 'Resultat', 'Marcador', 'Equip oponent'])
  })

  it('orders favorable opponents by win quality, dominant wins first', () => {
    const favorableQualityDetails = {
      ...details,
      matches: [
        gameMatch('close-1', '2025-01-01T12:00:00Z', 'win', 3, 2, 'Club Close', 'close-player'),
        gameMatch('dominant-1', '2025-01-02T12:00:00Z', 'win', 3, 0, 'Club Dominant', 'dominant-player'),
      ],
    }
    usePlayerDetails.mockReturnValue({ data: favorableQualityDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=categorization')

    const favorableTable = screen.getAllByRole('table')[0]
    const names = [...favorableTable.querySelectorAll('tbody tr.opponent-row td:first-child')].map((cell) => cell.textContent)
    expect(names).toEqual(['Club Dominant', 'Club Close'])
  })

  it('orders difficult opponents by loss severity, worst losses first', () => {
    const difficultQualityDetails = {
      ...details,
      matches: [
        gameMatch('narrow-1', '2025-01-01T12:00:00Z', 'loss', 2, 3, 'Club Narrow', 'narrow-player'),
        gameMatch('heavy-1', '2025-01-02T12:00:00Z', 'loss', 0, 3, 'Club Heavy', 'heavy-player'),
      ],
    }
    usePlayerDetails.mockReturnValue({ data: difficultQualityDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=categorization')

    const hardTable = screen.getAllByRole('table')[0]
    const names = [...hardTable.querySelectorAll('tbody tr.opponent-row td:first-child')].map((cell) => cell.textContent)
    expect(names).toEqual(['Club Heavy', 'Club Narrow'])
  })

  it('labels every opponent row cell for the stacked mobile layout', () => {
    usePlayerDetails.mockReturnValue({ data: accentedDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&opponentView=search')

    const row = screen.getByRole('table').querySelector('tbody tr')
    const labels = [...row.querySelectorAll('td')].map((cell) => cell.getAttribute('data-label'))
    expect(labels).toEqual(['Oponent', 'Partits jugats', 'Victòries', 'Empats', 'Derrotes', 'Victòries (%)', 'Categoria', 'Forma recent', 'Ratxa', null])
  })
})
