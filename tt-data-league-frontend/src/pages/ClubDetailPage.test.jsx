import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClubDetailPage from './ClubDetailPage.jsx'
import { routePaths } from '../config/routes.js'
import { useAuth } from '../context/useAuth.js'
import { useClubDetails, useClubMatches } from '../hooks/useClubs.js'

vi.mock('../context/useAuth.js', () => ({
  useAuth: vi.fn(),
}))

vi.mock('../hooks/useClubs.js', () => ({
  useClubDetails: vi.fn(),
  useClubMatches: vi.fn(),
}))

const club = {
  id: 'club-id',
  name: 'Club Terrassa',
  source: 'RFETM',
  teams: [
    { id: 'team-23', name: 'Sènior', source: 'RFETM', season: '2023-2024' },
    { id: 'team-24', name: 'Sènior', source: 'RFETM', season: '2024-2025' },
  ],
  players: [{
    playerSeasonId: 'player-season-id',
    playerId: 'player-id',
    canonicalPlayerId: 'canonical-player-id',
    playerName: 'Maria Player',
    registrationName: 'Maria Player',
    license: '123',
    source: 'RFETM',
    season: '2024-2025',
    competitions: ['Preferent'],
  }, {
    playerSeasonId: 'other-player-season-id',
    playerId: 'other-player-id',
    canonicalPlayerId: null,
    playerName: 'Joan Player',
    registrationName: 'Joan Player',
    license: '456',
    source: 'RFETM',
    season: '2024-2025',
    competitions: ['Copa'],
  }],
  competitions: [
    {
      name: 'Preferent',
      season: '2023-2024',
      matchCount: 4,
      resultTotals: { wins: 2, draws: 1, losses: 1 },
    },
    {
      name: 'Preferent',
      season: '2024-2025',
      matchCount: 5,
      resultTotals: { wins: 3, draws: 0, losses: 2 },
    },
  ],
}

function renderPage(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/clubs/:clubId" element={<ClubDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

function matchGroupsFor(competitions) {
  return competitions.map((competition) => ({
    competition: competition.name,
    season: competition.season,
    source: competition.source ?? 'RFETM',
    matches: [{
      id: `${competition.name}-${competition.season}-match`,
      homeTeam: 'Sènior',
      awayTeam: 'Rival TT',
      homeGamesWon: 3,
      awayGamesWon: 1,
      result: 'win',
      round: 1,
      venue: null,
    }],
  }))
}

function expandNode(name) {
  fireEvent.click(screen.getByRole('button', { name: new RegExp(`^${name} `) }))
}

describe('ClubDetailPage', () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    useAuth.mockReturnValue({ hasRole: () => false })
    useClubDetails.mockReturnValue({ data: club, loading: false, error: null, retry: vi.fn() })
    useClubMatches.mockImplementation((clubId, competitions) => ({
      data: matchGroupsFor(competitions),
      loading: false,
      error: null,
      retry: vi.fn(),
    }))
  })

  it('renders every group collapsed by default, showing only the top-level source', () => {
    renderPage('/clubs/club-id?view=matches&season=2024-2025&competition=Preferent')

    const sourceToggle = screen.getByRole('button', { name: /^RFETM/ })
    expect(sourceToggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('button', { name: /^2024-2025/ })).not.toBeInTheDocument()
    expect(screen.queryByText('Sènior — Rival TT')).not.toBeInTheDocument()
  })

  it('reveals season, competition, team, then matches as each level is expanded', () => {
    renderPage('/clubs/club-id?view=matches&season=all')

    expandNode('RFETM')
    const seasonToggle = screen.getByRole('button', { name: /^2024-2025/ })
    expect(seasonToggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('button', { name: /^Preferent/ })).not.toBeInTheDocument()

    expandNode('2024-2025')
    const competitionToggle = screen.getByRole('button', { name: /^Preferent/ })
    expect(competitionToggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('button', { name: /^Sènior/ })).not.toBeInTheDocument()

    expandNode('Preferent')
    const teamToggle = screen.getByRole('button', { name: /^Sènior/ })
    expect(teamToggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Sènior — Rival TT')).not.toBeInTheDocument()

    expandNode('Sènior')
    expect(screen.getByText('Sènior — Rival TT')).toBeInTheDocument()
    expect(screen.getByText('Jornada 1')).toBeInTheDocument()
    expect(screen.getByText('3 — 1')).toBeInTheDocument()

    fireEvent.click(teamToggle)
    expect(teamToggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Sènior — Rival TT')).not.toBeInTheDocument()
  })

  it('sorts seasons within a source descending (most recent first)', () => {
    renderPage('/clubs/club-id?view=matches&season=all')

    expandNode('RFETM')
    const seasonToggles = screen.getAllByRole('button', { name: /^20\d{2}-20\d{2}/ })
    expect(seasonToggles.map((toggle) => toggle.textContent)).toEqual([
      expect.stringContaining('2024-2025'),
      expect.stringContaining('2023-2024'),
    ])
  })

  it('omits the Season and Competition levels once both filters select a specific value', () => {
    renderPage('/clubs/club-id?view=matches&season=2024-2025&competition=Preferent')

    expandNode('RFETM')

    expect(screen.queryByRole('button', { name: /^2024-2025/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Preferent/ })).not.toBeInTheDocument()

    const teamToggle = screen.getByRole('button', { name: /^Sènior/ })
    expect(teamToggle).toHaveAttribute('aria-expanded', 'false')

    fireEvent.click(teamToggle)
    expect(screen.getByText('Sènior — Rival TT')).toBeInTheDocument()
  })

  it('restores the Season and Competition groupings once their filters go back to "all"', () => {
    renderPage('/clubs/club-id?view=matches&season=2024-2025&competition=Preferent')

    expandNode('RFETM')
    expect(screen.getByRole('button', { name: /^Sènior/ })).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Temporada'), { target: { value: 'all' } })
    fireEvent.change(screen.getByLabelText('Competició'), { target: { value: '' } })

    expect(screen.getByRole('button', { name: /^2024-2025/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^2023-2024/ })).toBeInTheDocument()
  })

  it('keeps filters interdependent and preserves them in the view-competition link', () => {
    renderPage('/clubs/club-id?view=matches&season=2024-2025&competition=Preferent')

    expandNode('RFETM')

    expect(screen.getByRole('link', { name: 'Veure la competició' })).toHaveAttribute(
      'href',
      '/clubs/club-id/competition/2024-2025/Preferent?view=matches&season=2024-2025&competition=Preferent',
    )

    fireEvent.change(screen.getByLabelText('Temporada'), { target: { value: '2023-2024' } })

    expect(screen.getByLabelText('Competició')).toHaveValue('Preferent')

    expect(screen.getByRole('link', { name: 'Veure la competició' })).toHaveAttribute(
      'href',
      '/clubs/club-id/competition/2023-2024/Preferent?view=matches&season=2023-2024&competition=Preferent',
    )
  })

  it('shows the club-level empty state when no competitions match the current filters', () => {
    useClubDetails.mockReturnValue({
      data: { ...club, competitions: [] },
      loading: false,
      error: null,
      retry: vi.fn(),
    })

    renderPage('/clubs/club-id?view=matches')

    expect(screen.getByText('No hi ha partits disponibles per als filtres seleccionats.')).toBeInTheDocument()
  })

  it('switches to the roster tab using an accessible tab', () => {
    renderPage('/clubs/club-id?season=2024-2025')

    fireEvent.click(screen.getAllByRole('tab', { name: /Jugadors/ })[0])

    expect(screen.getByRole('tabpanel', { name: 'Jugadors' })).toHaveTextContent('Maria Player')
    expect(screen.getAllByText(/Temporada: 2024-2025/)).toHaveLength(2)
    expect(screen.getByRole('tab', { name: /Jugadors/ })).toHaveAttribute('aria-selected', 'true')
  })

  it('shows both competition groups nested under one source when all seasons are selected', () => {
    renderPage('/clubs/club-id?view=matches&season=2024-2025')

    const seasonSelect = screen.getByLabelText('Temporada')
    expect(screen.getByRole('option', { name: 'Totes les temporades' })).toBeInTheDocument()

    fireEvent.change(seasonSelect, { target: { value: 'all' } })
    expect(seasonSelect).toHaveValue('all')

    expandNode('RFETM')
    expect(screen.getByRole('button', { name: /^2024-2025/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^2023-2024/ })).toBeInTheDocument()
  })

  it('omits the Source level and resets dependent filters when a specific source is selected', () => {
    renderPage('/clubs/club-id?view=matches&season=2024-2025&competition=Preferent&source=all')

    const sourceSelect = screen.getByLabelText('Font')
    expect(sourceSelect).toHaveValue('all')
    expect(screen.getByRole('option', { name: 'RFETM' })).toBeInTheDocument()

    fireEvent.change(sourceSelect, { target: { value: 'RFETM' } })

    expect(screen.getByLabelText('Temporada')).toHaveValue('all')
    expect(screen.getByLabelText('Competició')).toHaveValue('')

    expect(screen.queryByRole('button', { name: /^RFETM/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^2024-2025/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^2023-2024/ })).toBeInTheDocument()
  })

  it('limits season choices to the selected competition seasons', () => {
    renderPage('/clubs/club-id?view=matches&season=2024-2025')

    fireEvent.change(screen.getByLabelText('Competició'), { target: { value: 'Preferent' } })

    expect(screen.getByRole('option', { name: 'Totes les temporades' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '2023-2024' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '2024-2025' })).toBeInTheDocument()
  })

  it('filters players by the selected competition', () => {
    renderPage('/clubs/club-id?view=players&season=2024-2025')

    expect(screen.getByText('Maria Player')).toBeInTheDocument()
    expect(screen.getByText('Joan Player')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Competició'), { target: { value: 'Preferent' } })

    expect(screen.getByText('Maria Player')).toBeInTheDocument()
    expect(screen.queryByText('Joan Player')).not.toBeInTheDocument()
  })

  it('orders tabs Summary, Stats, Players, Matches and defaults to Summary', () => {
    renderPage('/clubs/club-id')

    const tabs = screen.getAllByRole('tab')
    expect(tabs.map((tab) => tab.textContent)).toEqual([
      expect.stringContaining('Resum'),
      expect.stringContaining('Estadístiques'),
      expect.stringContaining('Jugadors'),
      expect.stringContaining('Partits'),
    ])
    expect(screen.getByRole('tab', { name: /Resum/ })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('heading', { name: 'Resum' })).toBeInTheDocument()
  })

  it('deep-links directly to the Stats tab via the view query param and shows competition records', () => {
    renderPage('/clubs/club-id?view=stats&season=2024-2025')

    expect(screen.getByRole('tab', { name: /Estadístiques/ })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('heading', { name: 'Balanç per competició' })).toBeInTheDocument()
    expect(screen.getByText('60%')).toBeInTheDocument()
  })

  it('switches to the Matches tab from the Summary "See all" link, preserving filters', () => {
    renderPage('/clubs/club-id?season=2024-2025&competition=Preferent')

    fireEvent.click(screen.getAllByRole('button', { name: /Veure-ho tot/ })[0])

    expect(screen.getByRole('tab', { name: /Partits/ })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByLabelText('Competició')).toHaveValue('Preferent')
  })

  it('switches to the Players tab from the Summary "See all" link', () => {
    renderPage('/clubs/club-id?season=2024-2025')

    fireEvent.click(screen.getAllByRole('button', { name: /Veure-ho tot/ })[1])

    expect(screen.getByRole('tab', { name: /Jugadors/ })).toHaveAttribute('aria-selected', 'true')
  })

  it('links players with a canonicalPlayerId to their player details page', () => {
    renderPage('/clubs/club-id?view=players&season=2024-2025')

    const linkedPlayerName = screen.getByText('Maria Player')
    const linkedCard = linkedPlayerName.closest('a')
    expect(linkedCard).toHaveAttribute(
      'href',
      routePaths.playerDetails('canonical-player-id', 'source=RFETM&season=2024-2025'),
    )

    const unlinkedPlayerName = screen.getByText('Joan Player')
    expect(unlinkedPlayerName.closest('a')).toBeNull()
  })
})
