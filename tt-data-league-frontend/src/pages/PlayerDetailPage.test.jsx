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
      .querySelectorAll('.match-card-game-row')).toHaveLength(0)
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

    const card = document.querySelector('.match-card')
    expect(card).toHaveTextContent('Opponent Player')
    expect(card.querySelector('.match-card-opponent')).toHaveTextContent('Club Beta')
    expect(card.querySelectorAll('.match-card-game-row')).toHaveLength(1)
    const gameResult = card.querySelector('.match-card-game-row .match-card-game-result')
    expect(gameResult).toHaveTextContent('3-1')
    expect(gameResult).toHaveTextContent('Victòria')
    expect(gameResult).toHaveClass('match-result-win')
    const matchScore = card.querySelector('.match-card-score')
    expect(matchScore).toHaveTextContent('4 — 2')
    expect(matchScore).not.toHaveTextContent('Derrota')
    expect(card.querySelector('.match-card-badge')).toHaveClass('match-result-loss')
  })

  it('links a match card to the match detail page and links an opponent with a canonical id', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [{
          ...details.matches[0],
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

    renderPage('/players/player-id?view=matches&season=2024-2025')

    const matchLink = screen.getByRole('link', { name: 'Veure el partit' })
    expect(matchLink.getAttribute('href')).toContain('/partits/match-win')
    expect(matchLink.getAttribute('href')).toContain('season=2024-2025')

    const opponentLink = screen.getByRole('link', { name: 'Opponent Player' })
    expect(opponentLink.getAttribute('href')).toContain('/jugadors/opponent-id')

    const matchCard = document.querySelector('.match-card')
    expect(matchCard.hasAttribute('open')).toBe(false)
    fireEvent.click(matchLink)
    expect(matchCard.hasAttribute('open')).toBe(false)
  })

  it('shows an unlinked opponent name when the opponent has no canonical id', () => {
    usePlayerDetails.mockReturnValue({
      data: {
        ...details,
        matches: [{
          ...details.matches[0],
          games: [{
            id: 'game-1',
            gameNumber: 1,
            type: 'INDIVIDUAL',
            result: 'win',
            homeSetsWon: 3,
            awaySetsWon: 1,
            opponents: [{
              playerId: null,
              federatedPlayerId: null,
              playerSeasonId: null,
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

    expect(screen.getByText('Opponent Player')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Opponent Player' })).not.toBeInTheDocument()
  })

  it('shows round, group, and phase as chips only when present, omitting them when missing', () => {
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

    const cards = document.querySelectorAll('.match-card')
    // cards[0] is the most recent match (matches[1]: round 4, no group/phase).
    expect(cards[0]).toHaveTextContent('Jornada 4')
    expect(cards[0].querySelectorAll('.match-card-chips .chip')).toHaveLength(2)
    // cards[1] is the older match (matches[0]: round 3, group 2, phase Regular Season).
    expect(cards[1]).toHaveTextContent('Jornada 3')
    expect(cards[1].querySelectorAll('.match-card-chips .chip')).toHaveLength(4)
    expect(cards[1]).toHaveTextContent('2')
    expect(cards[1]).toHaveTextContent('Regular Season')
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

    const gameResult = document.querySelector('.match-card-game-row .match-card-game-result')
    expect(gameResult).toHaveTextContent('1-3')
    expect(gameResult).toHaveTextContent('Derrota')
    expect(gameResult).toHaveClass('match-result-loss')
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

    const card = document.querySelector('.match-card')
    expect(card).toHaveTextContent('Opponent A, Opponent B')
    expect(card.querySelectorAll('.match-card-game-row')).toHaveLength(1)
    const gameResult = card.querySelector('.match-card-game-row .match-card-game-result')
    expect(gameResult).toHaveTextContent('3-2')
    expect(gameResult).toHaveTextContent('Victòria')
    expect(gameResult).toHaveClass('match-result-win')
    expect(card.querySelector('.match-card-opponent')).toHaveTextContent('Club Beta')
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

    const card = document.querySelector('.match-card')
    expect(card.querySelectorAll('.match-card-game-row')).toHaveLength(0)
    expect(card.querySelector('.match-card-opponent')).toHaveTextContent('Club Beta')
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

  it('defaults the opponent filter to All', () => {
    renderPage('/players/player-id?view=opponents')

    expect(screen.getByRole('button', { name: /Tots, \d+ oponents/ })).toHaveClass('is-active')
  })

  it('switches the opponent category filter without resetting player filters', () => {
    renderPage('/players/player-id?view=opponents&source=FCTT&season=2024-2025&competition=Preferent&chart=bar')

    fireEvent.click(screen.getByRole('button', { name: /Problemàtic, \d+ oponents/ }))

    expect(screen.getByTestId('location')).toHaveTextContent('?view=opponents&source=FCTT&season=2024-2025&competition=Preferent&chart=bar&opponentFilter=problem')
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

  it('ignores an invalid opponent filter value in the URL and falls back to All', () => {
    renderPage('/players/player-id?view=opponents&opponentFilter=invalid')

    expect(screen.getByRole('button', { name: /Tots, \d+ oponents/ })).toHaveClass('is-active')
  })

  it('categorizes opponents with a badge on every row and counts draw-only records as uncategorized', () => {
    usePlayerDetails.mockReturnValue({ data: categoryDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    fireEvent.click(screen.getByRole('button', { name: 'Mostra 1 oponents més' }))
    const table = screen.getByRole('table')
    expect(table).toHaveTextContent('Club Beta')
    expect(table).toHaveTextContent('66.7%')
    expect(table).toHaveTextContent('Club Gamma')
    expect(table).toHaveTextContent('Club Delta')
    expect(table).toHaveTextContent('Club Alfa')

    const badgeFor = (name) => [...table.querySelectorAll('tbody tr.opponent-row')]
      .find((row) => row.textContent.includes(name))
      .querySelector('.opponent-category-badge')
    expect(badgeFor('Club Beta')).toHaveTextContent('Favorable')
    expect(badgeFor('Club Gamma')).toHaveTextContent('Difícil')
    expect(badgeFor('Club Delta')).toHaveTextContent('Problemàtic')
    expect(badgeFor('Club Alfa')).toHaveTextContent('Sense categoria')

    fireEvent.click(screen.getByRole('button', { name: /Favorable, \d+ oponents/ }))
    const favorableTable = screen.getByRole('table')
    expect(favorableTable).toHaveTextContent('Club Beta')
    expect(favorableTable).not.toHaveTextContent('Club Alfa')
    expect(favorableTable).not.toHaveTextContent('Club Gamma')
    expect(favorableTable).not.toHaveTextContent('Club Delta')
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
    const gameResult = document.querySelector('.match-card-game-row .match-card-game-result')
    expect(gameResult).toHaveClass('match-result-loss')
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
    renderPage('/players/player-id?view=opponents')

    fireEvent.click(screen.getByRole('button', { name: /Favorable, \d+ oponents/ }))
    expect(screen.getByText('Cap oponent coincideix amb els filtres.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Difícil, \d+ oponents/ }))
    expect(screen.getByText('Cap oponent coincideix amb els filtres.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Problemàtic, \d+ oponents/ }))
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

    renderPage('/players/player-id?view=opponents')

    const table = screen.getByRole('table')
    expect(table).toHaveTextContent('Opponent Repeat')
    const row = [...table.querySelectorAll('tbody tr')].find((tr) => tr.textContent.includes('Opponent Repeat'))
    expect(row.querySelectorAll('td')[2]).toHaveTextContent('1')
    expect(row).toHaveTextContent('100.0%')
  })

  it('links a head-to-head history entry to its match detail page', () => {
    usePlayerDetails.mockReturnValue({ data: details, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents&season=2024-2025')

    const row = [...document.querySelectorAll('tbody tr.opponent-row')]
      .find((tr) => tr.textContent.includes('Club Beta'))
    fireEvent.click(row)

    const matchLink = screen.getByRole('link', { name: '1/1/2025' })
    expect(matchLink.getAttribute('href')).toContain('/partits/match-win')
    expect(matchLink.getAttribute('href')).toContain('season=2024-2025')
  })

  it('sorts the unified opponent list by percentage, matches, then deterministic name', () => {
    usePlayerDetails.mockReturnValue({ data: sortingDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    fireEvent.click(screen.getByRole('button', { name: 'Mostra 5 oponents més' }))

    const names = [...screen.getByRole('table').querySelectorAll('tbody tr td:first-child')].map((cell) => cell.textContent)
    expect(names).toEqual(['Club Beta', 'Club Gamma', 'Club Alfa', 'Club Omega', 'Club Epsilon', 'Club Delta', 'Club Zeta', 'Club Eta'])
  })

  it('shows filter chip counts per category and a shared empty state for an empty filter', () => {
    usePlayerDetails.mockReturnValue({ data: drawOnlyDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    expect(screen.getByRole('button', { name: /Favorable, 0 oponents/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Difícil, 0 oponents/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Problemàtic, 0 oponents/ })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Favorable, 0 oponents/ }))
    expect(screen.getByText('Cap oponent coincideix amb els filtres.')).toBeInTheDocument()
  })

  it('shows only three rows and exposes the remaining rows on demand', () => {
    usePlayerDetails.mockReturnValue({ data: manyOpponentsDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    const table = screen.getByRole('table')
    expect(table.querySelectorAll('tbody tr')).toHaveLength(3)
    const moreButton = screen.getByRole('button', { name: 'Mostra 4 oponents més' })
    expect(moreButton).toBeInTheDocument()

    fireEvent.click(moreButton)
    expect(table.querySelectorAll('tbody tr')).toHaveLength(7)
    expect(screen.queryByRole('button', { name: /Mostra .* oponents més/ })).not.toBeInTheDocument()
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

    expect(document.querySelectorAll('.match-card')).toHaveLength(10)
    expect(document.querySelector('.match-card')).toHaveTextContent('Opponent 10')
    expect(screen.getByText('Pàgina 1 de 2')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Següent' }))
    expect(document.querySelectorAll('.match-card')).toHaveLength(1)
    expect(document.querySelector('.match-card')).toHaveTextContent('Opponent 0')
  })

  it('removes the legacy detail sections from the player detail view', () => {
    renderPage('/players/player-id')

    expect(screen.queryByText('Registres federats')).not.toBeInTheDocument()
    expect(screen.queryByText('Inscripcions per temporada')).not.toBeInTheDocument()
    expect(screen.queryByText('Clubs associats')).not.toBeInTheDocument()
    expect(screen.queryByText('Competicions')).not.toBeInTheDocument()
  })

  it('describes the opponent table for non-visual table readers', () => {
    usePlayerDetails.mockReturnValue({ data: categoryDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    fireEvent.click(screen.getByRole('button', { name: /Favorable, \d+ oponents/ }))
    const table = screen.getByRole('table')
    const description = document.getElementById(table.getAttribute('aria-describedby'))
    expect(description).toHaveTextContent('1 oponents.')
  })

  it('filters opponents by an accented substring and keeps it when switching category filters', () => {
    usePlayerDetails.mockReturnValue({ data: accentedDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    const search = screen.getByRole('searchbox', { name: 'Cerca un oponent' })
    fireEvent.change(search, { target: { value: 'òrr' } })
    expect(screen.getByRole('table')).toHaveTextContent('Club Òrrius')

    fireEvent.click(screen.getByRole('button', { name: /Tots, \d+ oponents/ }))
    expect(screen.getByRole('searchbox', { name: 'Cerca un oponent' })).toHaveValue('òrr')
  })

  it('uses a contrasted opponent search input', () => {
    usePlayerDetails.mockReturnValue({ data: accentedDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

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
    renderPage('/players/player-id?view=opponents')

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
    renderPage('/players/player-id?view=opponents')

    expect(screen.getByRole('table')).toHaveTextContent('Ratxa de 3 victòries')
  })

  it('sorts opponents by win percentage when the sort control changes', () => {
    usePlayerDetails.mockReturnValue({ data: sortingDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

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
    renderPage('/players/player-id?view=opponents')

    expect(screen.queryByText('Historial cara a cara amb Club Beta')).not.toBeInTheDocument()
    const opponentRow = document.querySelector('.opponent-table .table-wrap > table tbody tr.opponent-row')
    fireEvent.click(opponentRow)

    const heading = screen.getByText('Historial cara a cara amb Club Beta')
    expect(heading).toBeInTheDocument()
    expect(opponentRow).toHaveAttribute('aria-expanded', 'true')
    const rows = heading.closest('.opponent-history-detail').querySelectorAll('.match-card-game-row')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('1 — 3')
    expect(rows[1]).toHaveTextContent('3 — 1')

    fireEvent.click(opponentRow)
    expect(screen.queryByText('Historial cara a cara amb Club Beta')).not.toBeInTheDocument()
    expect(opponentRow).toHaveAttribute('aria-expanded', 'false')
  })

  it('renders each match as a self-contained card with no table markup, so nothing needs a mobile column collapse', () => {
    renderPage('/players/player-id?view=matches')

    expect(document.querySelector('.match-history table')).not.toBeInTheDocument()
    const card = document.querySelector('.match-card')
    expect(card.tagName).toBe('DETAILS')
    expect(card.querySelector('.match-card-badge')).toBeInTheDocument()
    expect(card.querySelector('.match-card-opponent')).toBeInTheDocument()
    expect(card.querySelector('.match-card-score')).toBeInTheDocument()
    expect(card.querySelector('.match-card-date')).toBeInTheDocument()
  })

  it('labels every opponent row cell for the stacked mobile layout', () => {
    usePlayerDetails.mockReturnValue({ data: accentedDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    const row = screen.getByRole('table').querySelector('tbody tr')
    const labels = [...row.querySelectorAll('td')].map((cell) => cell.getAttribute('data-label'))
    expect(labels).toEqual(['Oponent', 'Categoria', 'Partits jugats', 'Victòries', 'Empats', 'Derrotes', 'Victòries (%)', 'Forma recent', 'Ratxa'])
  })

  function expandOpponentRow(name) {
    const table = document.querySelector('.opponent-table .table-wrap > table')
    const row = [...table.querySelectorAll(':scope > tbody > tr.opponent-row')].find((tr) => tr.textContent.includes(name))
    fireEvent.click(row)
    return row.nextElementSibling
  }

  it('buckets opponent closeness by the average set margin', () => {
    const closenessDetails = {
      ...details,
      matches: [
        gameMatch('decisive-1', '2025-01-01T12:00:00Z', 'win', 3, 0, 'Club Decisive', 'decisive-opponent'),
        gameMatch('competitive-1', '2025-01-01T12:00:00Z', 'win', 3, 2, 'Club Competitive', 'competitive-opponent'),
        gameMatch('nail-1', '2025-01-01T12:00:00Z', 'win', 3, 2, 'Club NailBiter', 'nail-opponent'),
        gameMatch('nail-2', '2025-01-08T12:00:00Z', 'loss', 2, 3, 'Club NailBiter', 'nail-opponent'),
      ],
    }
    usePlayerDetails.mockReturnValue({ data: closenessDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    expect(expandOpponentRow('Club Decisive').querySelector('.opponent-insight-closeness')).toHaveTextContent('Decisiu')
    expect(expandOpponentRow('Club Competitive').querySelector('.opponent-insight-closeness')).toHaveTextContent('Competitiu')
    expect(expandOpponentRow('Club NailBiter').querySelector('.opponent-insight-closeness')).toHaveTextContent('Molt ajustat')
  })

  it('shows the singles or doubles insight tile only when that split has matches', () => {
    const splitDetails = {
      ...details,
      matches: [
        {
          id: 'singles-match', source: 'FCTT', season: '2024-2025', competition: 'Preferent',
          dateTime: '2025-01-01T12:00:00Z', homeTeam: 'Club Terrassa', awayTeam: 'Club Singles', playerTeam: 'Club Terrassa', result: 'win',
          games: [{
            id: 'singles-game', gameNumber: 1, type: 'INDIVIDUAL', result: 'win', homeSetsWon: 3, awaySetsWon: 1,
            opponents: [{ playerId: 'singles-opponent', name: 'Club Singles', available: true, source: 'FCTT', season: '2024-2025' }],
          }],
        },
        {
          id: 'doubles-match', source: 'FCTT', season: '2024-2025', competition: 'Preferent',
          dateTime: '2025-01-01T12:00:00Z', homeTeam: 'Club Terrassa', awayTeam: 'Club Doubles', playerTeam: 'Club Terrassa', result: 'win',
          games: [{
            id: 'doubles-game', gameNumber: 1, type: 'DOUBLES', result: 'win', homeSetsWon: 3, awaySetsWon: 1,
            opponents: [{ playerId: 'doubles-opponent', name: 'Club Doubles', available: true, source: 'FCTT', season: '2024-2025' }],
          }],
        },
      ],
    }
    usePlayerDetails.mockReturnValue({ data: splitDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    const singlesPanel = expandOpponentRow('Club Singles')
    expect(singlesPanel.textContent).toContain('Individual')
    expect(singlesPanel.textContent).not.toContain('Dobles')

    const doublesPanel = expandOpponentRow('Club Doubles')
    expect(doublesPanel.textContent).toContain('Dobles')
    expect(doublesPanel.textContent).not.toContain('Individual')
  })

  it('shows the home or away insight tile only for the side actually played', () => {
    const homeAwayDetails = {
      ...details,
      matches: [
        { id: 'home-match', source: 'FCTT', season: '2024-2025', competition: 'Preferent', dateTime: '2025-01-01T12:00:00Z', homeTeam: 'Club Terrassa', awayTeam: 'Club HomeOnly', playerTeam: 'Club Terrassa', result: 'win' },
        { id: 'away-match', source: 'FCTT', season: '2024-2025', competition: 'Preferent', dateTime: '2025-01-01T12:00:00Z', homeTeam: 'Club AwayOnly', awayTeam: 'Club Terrassa', playerTeam: 'Club Terrassa', result: 'win' },
      ],
    }
    usePlayerDetails.mockReturnValue({ data: homeAwayDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    const homePanel = expandOpponentRow('Club HomeOnly')
    expect(homePanel.textContent).toContain('L 100%')
    expect(homePanel.textContent).not.toContain('V 100%')

    const awayPanel = expandOpponentRow('Club AwayOnly')
    expect(awayPanel.textContent).toContain('V 100%')
    expect(awayPanel.textContent).not.toContain('L 100%')
  })

  it('shows the longest streak alongside a shorter current streak against an opponent', () => {
    const streakDivergenceDetails = {
      ...details,
      matches: [
        gameMatch('streak-1', '2025-01-01T12:00:00Z', 'win', 3, 0),
        gameMatch('streak-2', '2025-01-08T12:00:00Z', 'win', 3, 0),
        gameMatch('streak-3', '2025-01-15T12:00:00Z', 'win', 3, 0),
        gameMatch('streak-4', '2025-01-22T12:00:00Z', 'loss', 0, 3),
        gameMatch('streak-5', '2025-01-29T12:00:00Z', 'win', 3, 0),
      ],
    }
    usePlayerDetails.mockReturnValue({ data: streakDivergenceDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    expect(screen.getByRole('table')).toHaveTextContent('Ratxa de 1 victòries')
    const panel = expandOpponentRow('Club Beta')
    expect(panel.textContent).toContain('V3 millor · D1 pitjor')
  })

  it('shows a competition breakdown tile only for opponents faced across more than one competition', () => {
    const competitionDetails = {
      ...details,
      matches: [
        { id: 'multi-1', source: 'FCTT', season: '2024-2025', competition: 'Preferent', dateTime: '2025-01-01T12:00:00Z', homeTeam: 'Club Terrassa', awayTeam: 'Club Multi', playerTeam: 'Club Terrassa', result: 'win' },
        { id: 'multi-2', source: 'FCTT', season: '2024-2025', competition: 'Divisió', dateTime: '2025-02-01T12:00:00Z', homeTeam: 'Club Terrassa', awayTeam: 'Club Multi', playerTeam: 'Club Terrassa', result: 'loss' },
        { id: 'single-1', source: 'FCTT', season: '2024-2025', competition: 'Preferent', dateTime: '2025-01-01T12:00:00Z', homeTeam: 'Club Terrassa', awayTeam: 'Club Single', playerTeam: 'Club Terrassa', result: 'win' },
      ],
    }
    usePlayerDetails.mockReturnValue({ data: competitionDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    const multiPanel = expandOpponentRow('Club Multi')
    expect(multiPanel.textContent).toContain('Per competició')
    expect(multiPanel.textContent).toContain('Divisió')

    const singlePanel = expandOpponentRow('Club Single')
    expect(singlePanel.textContent).not.toContain('Per competició')
  })

  it('shows an opponent trend only when both the recent and previous windows have at least two matches', () => {
    function trendMatches(count) {
      const windowSize = Math.min(5, Math.floor(count / 2))
      return [...Array(count)].map((_, index) => gameMatch(
        `trend-${index}`,
        new Date(Date.UTC(2025, 0, index + 1)).toISOString(),
        index < count - windowSize ? 'loss' : 'win',
        index < count - windowSize ? 1 : 3,
        index < count - windowSize ? 3 : 0,
        'Club Trend',
        'trend-opponent',
      ))
    }

    usePlayerDetails.mockReturnValue({ data: { ...details, matches: trendMatches(3) }, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')
    expect(expandOpponentRow('Club Trend').textContent).not.toContain('Tendència')
    cleanup()

    usePlayerDetails.mockReturnValue({ data: { ...details, matches: trendMatches(4) }, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')
    expect(expandOpponentRow('Club Trend').textContent).toContain('Tendència')
  })

  it('sorts opponents by closeness when the sort control changes', () => {
    const closenessSortDetails = {
      ...details,
      matches: [
        gameMatch('sort-a', '2025-01-01T12:00:00Z', 'win', 4, 1, 'Club A', 'a-opp'),
        gameMatch('sort-b', '2025-01-01T12:00:00Z', 'win', 3, 2, 'Club B', 'b-opp'),
        gameMatch('sort-c', '2025-01-01T12:00:00Z', 'win', 3, 1, 'Club C', 'c-opp'),
        { id: 'sort-d', source: 'FCTT', season: '2024-2025', competition: 'Preferent', dateTime: '2025-01-01T12:00:00Z', homeTeam: 'Club Terrassa', awayTeam: 'Club D', playerTeam: 'Club Terrassa', result: 'win' },
      ],
    }
    usePlayerDetails.mockReturnValue({ data: closenessSortDetails, loading: false, error: null, retry: vi.fn() })
    renderPage('/players/player-id?view=opponents')

    fireEvent.change(screen.getByRole('combobox', { name: 'Ordena per' }), { target: { value: 'closeness' } })
    expect(screen.getByTestId('location')).toHaveTextContent('opponentSort=closeness')

    fireEvent.click(screen.getByRole('button', { name: 'Mostra 1 oponents més' }))
    const names = [...screen.getByRole('table').querySelectorAll('tbody tr td:first-child')].map((cell) => cell.textContent)
    expect(names).toEqual(['Club B', 'Club C', 'Club A', 'Club D'])
  })
})
