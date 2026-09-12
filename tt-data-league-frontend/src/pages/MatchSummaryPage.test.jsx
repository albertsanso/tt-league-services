import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MatchSummaryPage from './MatchSummaryPage.jsx'
import { useMatchSummary } from '../hooks/useMatches.js'

vi.mock('../hooks/useMatches.js', () => ({
  useMatchSummary: vi.fn(),
}))

vi.mock('../context/useAuth.js', () => ({
  useAuth: () => ({ token: 'token', clearSession: vi.fn() }),
}))

function renderPage(matchId = 'match-1', search = '') {
  return render(
    <MemoryRouter initialEntries={[`/partits/${matchId}${search}`]}>
      <Routes>
        <Route path="/partits/:matchId" element={<MatchSummaryPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

const baseMatch = {
  id: 'match-1',
  competition: 'Primera Catalana',
  round: 12,
  dateTime: '2026-03-08T18:00:00Z',
  venue: 'Pavelló Municipal',
  refereeName: 'J. Ferrer',
  homeTeam: { id: 'home-team', name: 'CT Sant Cugat A' },
  awayTeam: { id: 'away-team', name: 'CT Rubí B' },
  homeGamesWon: 5,
  awayGamesWon: 3,
  lineups: [
    {
      id: 'lineup-home-a',
      team: { id: 'home-team' },
      letter: 'A',
      ranking: 2465.1,
      player: { playerSeasonId: 'ps-home-a', canonicalPlayerId: 'canonical-home-a', name: 'Marc Roig' },
    },
    {
      id: 'lineup-away-a',
      team: { id: 'away-team' },
      letter: 'A',
      ranking: 2310.4,
      player: { playerSeasonId: 'ps-away-a', canonicalPlayerId: null, name: 'Pol Serra' },
    },
  ],
  games: [],
  homeTeamForm: {
    lastResults: [
      { matchId: 'm1', result: 'loss', opponent: 'X', score: '1-5' },
      { matchId: 'm2', result: 'win', opponent: 'Y', score: '5-2' },
    ],
    lastWinRate: 60,
    previousWinRate: 40,
    overallWinRate: 55,
  },
  awayTeamForm: {
    lastResults: [],
    lastWinRate: null,
    previousWinRate: null,
    overallWinRate: null,
  },
  playerForm: [
    { playerSeasonId: 'ps-home-a', canonicalPlayerId: 'canonical-home-a', lastResults: [{ matchId: 'm3', result: 'win' }], winRate: 100 },
  ],
  homeAlignmentStability: { timesFielded: 7, wins: 5, draws: 1, losses: 1, winRate: 71.4, teamOverallWinRate: 60 },
  awayAlignmentStability: { timesFielded: 1, wins: 0, draws: 0, losses: 1, winRate: null, teamOverallWinRate: 20 },
}

describe('MatchSummaryPage', () => {
  beforeEach(() => {
    useMatchSummary.mockReturnValue({ data: baseMatch, loading: false, error: null, retry: vi.fn() })
  })

  afterEach(cleanup)

  it('shows the header with the score, competition and a View acta action', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: /CT Sant Cugat A.*5.*3.*CT Rubí B/ })).toBeInTheDocument()
    expect(screen.getByText(/Primera Catalana/)).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Veure acta' }).length).toBeGreaterThan(0)
  })

  it("shows both team panels' form strips and win rates", () => {
    renderPage()

    expect(screen.getByText('CT Sant Cugat A')).toBeInTheDocument()
    expect(screen.getByText('CT Rubí B')).toBeInTheDocument()
    expect(screen.getAllByText(/60%/).length).toBeGreaterThan(0)
  })

  it('links a lineup player to the player detail page only when a canonical id exists', () => {
    renderPage()

    const marcLink = screen.getByRole('link', { name: 'Marc Roig' })
    expect(marcLink.getAttribute('href')).toContain('canonical-home-a')
    expect(screen.getByText('Pol Serra')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Pol Serra' })).not.toBeInTheDocument()
  })

  it('shows the regular-lineup alignment badge when the exact lineup has been fielded 3+ times', () => {
    renderPage()
    expect(screen.getByText('Alineació habitual')).toBeInTheDocument()
  })

  it('shows the new-combination alignment badge when the lineup has no prior history', () => {
    renderPage()
    expect(screen.getByText('Combinació nova')).toBeInTheDocument()
  })

  it('shows the rarely-used alignment badge when the exact lineup has been fielded exactly twice', () => {
    useMatchSummary.mockReturnValue({
      data: {
        ...baseMatch,
        homeAlignmentStability: { timesFielded: 2, wins: 1, draws: 0, losses: 1, winRate: 50, teamOverallWinRate: 60 },
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })
    renderPage()
    expect(screen.getByText('Poc habitual')).toBeInTheDocument()
  })

  it('shows a not-found message for a missing match', () => {
    useMatchSummary.mockReturnValue({ data: null, loading: false, error: { status: 404 }, retry: vi.fn() })
    renderPage()
    expect(screen.getByRole('heading', { name: 'Partit no trobat' })).toBeInTheDocument()
  })

  it('links team names and the competition when a club id is available', () => {
    useMatchSummary.mockReturnValue({
      data: {
        ...baseMatch,
        season: '2025',
        homeTeam: { id: 'home-team', name: 'CT Sant Cugat A', clubId: 'club-home' },
        awayTeam: { id: 'away-team', name: 'CT Rubí B', clubId: 'club-away' },
      },
      loading: false,
      error: null,
      retry: vi.fn(),
    })
    renderPage()

    const homeTeamLinks = screen.getAllByRole('link', { name: 'CT Sant Cugat A' })
    expect(homeTeamLinks.length).toBeGreaterThan(0)
    homeTeamLinks.forEach((link) => expect(link.getAttribute('href')).toContain('/clubs/club-home'))
    const awayTeamLinks = screen.getAllByRole('link', { name: 'CT Rubí B' })
    expect(awayTeamLinks.length).toBeGreaterThan(0)
    awayTeamLinks.forEach((link) => expect(link.getAttribute('href')).toContain('/clubs/club-away'))

    const competitionLink = screen.getByRole('link', { name: 'Primera Catalana' })
    expect(competitionLink.getAttribute('href')).toContain('/clubs/club-home/competition/2025/Primera%20Catalana')
  })

  it('shows team names and the competition as plain text when no club id is available', () => {
    renderPage()

    expect(screen.queryByRole('link', { name: 'CT Sant Cugat A' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'CT Rubí B' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Primera Catalana' })).not.toBeInTheDocument()
    expect(screen.getByText(/Primera Catalana/)).toBeInTheDocument()
  })
})
