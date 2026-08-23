import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClubDetailPage from './ClubDetailPage.jsx'
import { useAuth } from '../context/useAuth.js'
import { useClubDetails } from '../hooks/useClubs.js'

vi.mock('../context/useAuth.js', () => ({
  useAuth: vi.fn(),
}))

vi.mock('../hooks/useClubs.js', () => ({
  useClubDetails: vi.fn(),
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
    playerName: 'Maria Player',
    registrationName: 'Maria Player',
    license: '123',
    season: '2024-2025',
    competitions: ['Preferent'],
  }, {
    playerSeasonId: 'other-player-season-id',
    playerId: 'other-player-id',
    playerName: 'Joan Player',
    registrationName: 'Joan Player',
    license: '456',
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

describe('ClubDetailPage', () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    useAuth.mockReturnValue({ hasRole: () => false })
    useClubDetails.mockReturnValue({ data: club, loading: false, error: null, retry: vi.fn() })
  })

  it('keeps filters interdependent and preserves them in competition links', () => {
    renderPage('/clubs/club-id?view=matches&season=2024-2025&competition=Preferent')

    expect(screen.getByRole('link', { name: /Preferent/ })).toHaveAttribute(
      'href',
      '/clubs/club-id/competition/2024-2025/Preferent?view=matches&season=2024-2025&competition=Preferent',
    )

    fireEvent.change(screen.getByLabelText('Temporada'), { target: { value: '2023-2024' } })

    expect(screen.getByLabelText('Competició')).toHaveValue('Preferent')
    expect(screen.getByRole('link', { name: /Preferent/ })).toHaveAttribute(
      'href',
      '/clubs/club-id/competition/2023-2024/Preferent?view=matches&season=2023-2024&competition=Preferent',
    )
  })

  it('switches to the roster tab using an accessible tab', () => {
    renderPage('/clubs/club-id?season=2024-2025')

    fireEvent.click(screen.getAllByRole('tab', { name: /Jugadors/ })[0])

    expect(screen.getByRole('tabpanel', { name: 'Jugadors' })).toHaveTextContent('Maria Player')
    expect(screen.getAllByText(/Temporada: 2024-2025/)).toHaveLength(2)
    expect(screen.getByRole('tab', { name: /Jugadors/ })).toHaveAttribute('aria-selected', 'true')
  })

  it('shows competition summaries across all seasons when selected', () => {
    renderPage('/clubs/club-id?view=matches&season=2024-2025')

    const seasonSelect = screen.getByLabelText('Temporada')
    expect(screen.getByRole('option', { name: 'Totes les temporades' })).toBeInTheDocument()

    fireEvent.change(seasonSelect, { target: { value: 'all' } })

    expect(seasonSelect).toHaveValue('all')
    expect(screen.getAllByRole('link', { name: /Preferent/ })).toHaveLength(2)
  })

  it('offers all sources and resets dependent filters when a source changes', () => {
    renderPage('/clubs/club-id?view=matches&season=2024-2025&competition=Preferent&source=all')

    const sourceSelect = screen.getByLabelText('Font')
    expect(sourceSelect).toHaveValue('all')
    expect(screen.getByRole('option', { name: 'RFETM' })).toBeInTheDocument()

    fireEvent.change(sourceSelect, { target: { value: 'RFETM' } })

    expect(screen.getByLabelText('Temporada')).toHaveValue('all')
    expect(screen.getByLabelText('Competició')).toHaveValue('')
    expect(screen.getAllByRole('link', { name: /Preferent/ })).toHaveLength(2)
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
})
