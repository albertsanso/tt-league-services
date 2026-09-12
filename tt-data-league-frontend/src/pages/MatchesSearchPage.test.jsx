import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MatchesSearchPage from './MatchesSearchPage.jsx'
import { getMatchDetails, getMatchOptions, searchMatches } from '../api/matches.js'
import { useAuth } from '../context/useAuth.js'

vi.mock('../context/useAuth.js', () => ({
  useAuth: vi.fn(),
}))

vi.mock('../api/matches.js', () => ({
  getMatchOptions: vi.fn(),
  searchMatches: vi.fn(),
  getMatchDetails: vi.fn(),
}))

function renderPage(path = '/partits?source=RFETM&season=2024-2025&competition=Preferent') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/partits" element={<MatchesSearchPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('MatchesSearchPage', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn() })
    getMatchOptions.mockResolvedValue({ seasons: ['2024-2025'], competitions: ['Preferent'] })
  })

  afterEach(cleanup)

  it('shows an acta action only for matches that have a played score, and no longer links to a detail page', async () => {
    searchMatches.mockResolvedValue({
      matches: [
        { id: 'match-with-acta', homeTeam: 'Club A', awayTeam: 'Club B', homeGamesWon: 4, awayGamesWon: 1 },
        { id: 'match-without-acta', homeTeam: 'Club C', awayTeam: 'Club D', homeGamesWon: null, awayGamesWon: null },
      ],
      page: 0,
      hasNext: false,
    })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Cercar' }))

    const actaButtons = await screen.findAllByRole('button', { name: 'Veure acta' })
    expect(actaButtons).toHaveLength(1)
    expect(screen.queryAllByRole('link')).toHaveLength(0)
  })

  it('reuses the shared search-result list/card styling used by Players and Clubs search', async () => {
    searchMatches.mockResolvedValue({
      matches: [{ id: 'match-1', homeTeam: 'Club A', awayTeam: 'Club B', homeGamesWon: 4, awayGamesWon: 1 }],
      page: 0,
      hasNext: false,
    })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Cercar' }))

    const item = (await screen.findAllByRole('listitem'))[0]
    expect(item).toHaveClass('club-result', 'card')
    expect(item.querySelector('.club-result-link')).toBeInTheDocument()
  })

  it('opens the acta in a dialog instead of navigating to another page', async () => {
    searchMatches.mockResolvedValue({
      matches: [{ id: 'match-1', homeTeam: 'Club A', awayTeam: 'Club B', homeGamesWon: 4, awayGamesWon: 1 }],
      page: 0,
      hasNext: false,
    })
    getMatchDetails.mockResolvedValue({
      id: 'match-1',
      round: 1,
      homeTeam: { id: 'home', name: 'Club A' },
      awayTeam: { id: 'away', name: 'Club B' },
      homeGamesWon: 4,
      awayGamesWon: 1,
      lineups: [],
      games: [],
    })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Cercar' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Veure acta' }))

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(getMatchDetails).toHaveBeenCalledWith('match-1', 'token', expect.anything(), expect.anything())
    // still on the matches search page, no navigation happened
    expect(screen.getByRole('heading', { name: 'Cerca de partits' })).toBeInTheDocument()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
