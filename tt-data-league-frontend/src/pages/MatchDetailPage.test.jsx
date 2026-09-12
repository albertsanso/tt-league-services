import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MatchDetailPage from './MatchDetailPage.jsx'
import { getMatchDetails } from '../api/matches.js'
import { useAuth } from '../context/useAuth.js'

vi.mock('../context/useAuth.js', () => ({
  useAuth: vi.fn(),
}))

vi.mock('../api/matches.js', () => ({
  getMatchDetails: vi.fn(),
}))

const baseMatch = {
  id: 'match-1',
  homeTeam: { id: 'home', name: 'Club Terrassa' },
  awayTeam: { id: 'away', name: 'Club Beta' },
  dateTime: '2025-09-26T18:00:00+02:00',
  competition: 'Preferent',
  round: 1,
  lineups: [],
  games: [],
}

function renderPage(matchId = 'match-1') {
  return render(
    <MemoryRouter initialEntries={[`/partits/${matchId}`]}>
      <Routes>
        <Route path="/partits/:matchId" element={<MatchDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('MatchDetailPage', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn() })
  })

  afterEach(cleanup)

  it('shows a link to the acta when the match has games', async () => {
    getMatchDetails.mockResolvedValue({
      ...baseMatch,
      games: [{ id: 'g1', gameNumber: 1, type: 'INDIVIDUAL', crossover: 'A vs Y', sets: [] }],
    })
    renderPage()

    const link = await screen.findByRole('link', { name: 'Veure acta' })
    expect(link).toHaveAttribute('href', '/partits/match-1/acta')
  })

  it('hides the acta link when the match has no games', async () => {
    getMatchDetails.mockResolvedValue(baseMatch)
    renderPage()

    await screen.findByRole('heading', { name: 'Club Terrassa – Club Beta' })
    expect(screen.queryByRole('link', { name: 'Veure acta' })).not.toBeInTheDocument()
  })
})
