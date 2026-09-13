import { cleanup, render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClubSummaryPanel from './ClubSummaryPanel.jsx'
import { routePaths } from '../config/routes.js'
import { useClubMatches } from '../hooks/useClubs.js'
import i18n from '../i18n/index.js'

function renderPanel(element) {
  return render(<MemoryRouter>{element}</MemoryRouter>)
}

vi.mock('../hooks/useClubs.js', () => ({
  useClubMatches: vi.fn(),
}))

const club = {
  id: 'club-id',
  source: 'RFETM',
  playerCount: 38,
  seasons: ['2023-2024', '2024-2025'],
}

const competitions = [
  { name: 'Preferent', season: '2024-2025', matchCount: 5, resultTotals: { wins: 3, draws: 0, losses: 2 } },
]

const players = [
  {
    playerSeasonId: 'p1',
    playerName: 'Marc Roig',
    registrationName: 'Marc Roig',
    competitions: ['Preferent', 'Copa'],
    matchCount: 8,
    resultTotals: { wins: 6, draws: 0, losses: 2 },
  },
  {
    playerSeasonId: 'p2',
    playerName: 'Laia Puig',
    registrationName: 'Laia Puig',
    competitions: ['Preferent'],
    matchCount: 1,
    resultTotals: { wins: 1, draws: 0, losses: 0 },
  },
]

function t(key, options) {
  return i18n.t(key, options)
}

describe('ClubSummaryPanel', () => {
  afterEach(() => cleanup())

  beforeEach(() => {
    useClubMatches.mockReturnValue({
      data: [{
        competition: 'Preferent',
        season: '2024-2025',
        source: 'RFETM',
        matches: [{
          id: 'm1',
          homeTeam: 'Club A',
          awayTeam: 'Club B',
          homeGamesWon: 5,
          awayGamesWon: 3,
          result: 'win',
          round: 12,
          dateTime: '2024-03-08T00:00:00Z',
        }],
      }],
      loading: false,
      error: null,
    })
  })

  it('renders stat tiles from the provided competitions and players', () => {
    renderPanel(
      <ClubSummaryPanel
        club={club}
        competitions={competitions}
        players={players}
        season="2024-2025"
        onSeeMatches={vi.fn()}
        onSeePlayers={vi.fn()}
        t={t}
      />,
    )

    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('60%')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('38 de sempre')).toBeInTheDocument()
  })

  it('shows the recent match and ranks players by competitions played', () => {
    renderPanel(
      <ClubSummaryPanel
        club={club}
        competitions={competitions}
        players={players}
        season="2024-2025"
        onSeeMatches={vi.fn()}
        onSeePlayers={vi.fn()}
        t={t}
      />,
    )

    expect(screen.getByText('Club A — Club B')).toBeInTheDocument()
    expect(screen.getAllByText('Marc Roig').length).toBeGreaterThan(0)
    expect(screen.getByText('2 competicions')).toBeInTheDocument()
  })

  it('calls the tab-switch callbacks from the "See all" links', () => {
    const onSeeMatches = vi.fn()
    const onSeePlayers = vi.fn()
    renderPanel(
      <ClubSummaryPanel
        club={club}
        competitions={competitions}
        players={players}
        season="2024-2025"
        onSeeMatches={onSeeMatches}
        onSeePlayers={onSeePlayers}
        t={t}
      />,
    )

    const seeAllButtons = screen.getAllByRole('button', { name: /Veure-ho tot/ })
    fireEvent.click(seeAllButtons[0])
    fireEvent.click(seeAllButtons[1])

    expect(onSeeMatches).toHaveBeenCalledTimes(1)
    expect(onSeePlayers).toHaveBeenCalledTimes(1)
  })

  it('ranks the Top performer card by win rate, excluding players below the match floor', () => {
    renderPanel(
      <ClubSummaryPanel
        club={club}
        competitions={competitions}
        players={players}
        season="2024-2025"
        onSeeMatches={vi.fn()}
        onSeePlayers={vi.fn()}
        t={t}
      />,
    )

    expect(screen.getByText('75% · 6V-0E-2D')).toBeInTheDocument()
    expect(screen.queryByText('100% · 1V-0E-0D')).not.toBeInTheDocument()
  })

  it('shows the Top performer empty state when no player meets the match floor', () => {
    renderPanel(
      <ClubSummaryPanel
        club={club}
        competitions={competitions}
        players={[players[1]]}
        season="2024-2025"
        onSeeMatches={vi.fn()}
        onSeePlayers={vi.fn()}
        t={t}
      />,
    )

    expect(screen.getByText('Cap jugador té encara prou partits per aparèixer en aquest rànquing.')).toBeInTheDocument()
  })

  it('links the recent match and top players/performers to their details pages', () => {
    renderPanel(
      <ClubSummaryPanel
        club={club}
        competitions={competitions}
        players={players}
        season="2024-2025"
        returnSearch="season=2024-2025"
        onSeeMatches={vi.fn()}
        onSeePlayers={vi.fn()}
        t={t}
      />,
    )

    expect(screen.getByText('Club A — Club B').closest('a')).toHaveAttribute(
      'href',
      routePaths.matchSummary('m1', 'season=2024-2025'),
    )
  })

  it('shows empty states when there are no matches or players', () => {
    useClubMatches.mockReturnValue({ data: [], loading: false, error: null })
    renderPanel(
      <ClubSummaryPanel
        club={club}
        competitions={[]}
        players={[]}
        season="2024-2025"
        onSeeMatches={vi.fn()}
        onSeePlayers={vi.fn()}
        t={t}
      />,
    )

    expect(screen.getByText('No hi ha partits disponibles per als filtres seleccionats.')).toBeInTheDocument()
    expect(screen.getByText('No hi ha jugadors registrats per als filtres seleccionats.')).toBeInTheDocument()
  })
})
