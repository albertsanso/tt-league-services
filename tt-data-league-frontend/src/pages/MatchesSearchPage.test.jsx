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

  it('sends the club name filter as a clubName query param', async () => {
    searchMatches.mockResolvedValue({ matches: [], page: 0, hasNext: false })
    renderPage()

    fireEvent.change(screen.getByLabelText('Nom del club'), { target: { value: 'Terrassa' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cercar' }))

    await screen.findByRole('status')
    expect(searchMatches).toHaveBeenCalledWith(
      expect.objectContaining({ clubName: 'Terrassa' }),
      'token',
      expect.anything(),
      expect.anything(),
    )
  })

  it('does not render a Phase filter field', () => {
    renderPage()
    expect(screen.queryByLabelText('Fase')).not.toBeInTheDocument()
  })

  it('enables search with only source and season set, without a competition', async () => {
    searchMatches.mockResolvedValue({ matches: [], page: 0, hasNext: false })
    renderPage('/partits?source=RFETM&season=2024-2025')

    const searchButton = screen.getByRole('button', { name: 'Cercar' })
    expect(searchButton).not.toBeDisabled()

    fireEvent.click(searchButton)

    await screen.findByRole('status')
    expect(searchMatches).toHaveBeenCalledWith(
      expect.objectContaining({ source: 'RFETM', season: '2024-2025', competition: '' }),
      'token',
      expect.anything(),
      expect.anything(),
    )
  })

  it('keeps search disabled until source and season are both set', () => {
    renderPage('/partits?source=RFETM')
    expect(screen.getByRole('button', { name: 'Cercar' })).toBeDisabled()
  })

  it('does not render pagination controls when everything fits on one page', async () => {
    searchMatches.mockResolvedValue({
      matches: [{ id: 'match-1', homeTeam: 'Club A', awayTeam: 'Club B', homeGamesWon: 4, awayGamesWon: 1 }],
      total: 1,
      page: 0,
      pageSize: 10,
      hasNext: false,
    })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Cercar' }))

    await screen.findAllByRole('listitem')
    expect(screen.queryAllByRole('navigation', { name: 'Paginació de partits' })).toHaveLength(0)
  })

  it('duplicates the pagination controls above and below the results, with First/Previous disabled and Next/Last enabled on the first page', async () => {
    searchMatches.mockResolvedValue({
      matches: [{ id: 'match-1', homeTeam: 'Club A', awayTeam: 'Club B', homeGamesWon: 4, awayGamesWon: 1 }],
      total: 25,
      page: 0,
      pageSize: 10,
      hasNext: true,
    })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Cercar' }))

    await screen.findAllByRole('listitem')
    expect(screen.getAllByRole('navigation', { name: 'Paginació de partits' })).toHaveLength(2)
    expect(screen.getAllByText('Pàgina 1 de 3')).toHaveLength(2)
    for (const button of screen.getAllByRole('button', { name: 'Primera' })) expect(button).toBeDisabled()
    for (const button of screen.getAllByRole('button', { name: 'Anterior' })) expect(button).toBeDisabled()
    for (const button of screen.getAllByRole('button', { name: 'Següent' })) expect(button).not.toBeDisabled()
    for (const button of screen.getAllByRole('button', { name: 'Última' })) expect(button).not.toBeDisabled()
  })

  it('navigates to the next page and requests it from the API', async () => {
    searchMatches.mockResolvedValue({
      matches: [{ id: 'match-1', homeTeam: 'Club A', awayTeam: 'Club B', homeGamesWon: 4, awayGamesWon: 1 }],
      total: 25,
      page: 0,
      pageSize: 10,
      hasNext: true,
    })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Cercar' }))
    await screen.findAllByRole('listitem')

    searchMatches.mockResolvedValue({
      matches: [{ id: 'match-2', homeTeam: 'Club C', awayTeam: 'Club D', homeGamesWon: 3, awayGamesWon: 2 }],
      total: 25,
      page: 1,
      pageSize: 10,
      hasNext: true,
    })
    fireEvent.click(screen.getAllByRole('button', { name: 'Següent' })[0])

    await screen.findAllByText('Pàgina 2 de 3')
    expect(searchMatches).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1 }),
      'token',
      expect.anything(),
      expect.anything(),
    )
  })

  it('jumps to the last page and disables Next/Last there', async () => {
    searchMatches.mockResolvedValue({
      matches: [{ id: 'match-1', homeTeam: 'Club A', awayTeam: 'Club B', homeGamesWon: 4, awayGamesWon: 1 }],
      total: 25,
      page: 0,
      pageSize: 10,
      hasNext: true,
    })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Cercar' }))
    await screen.findAllByRole('listitem')

    searchMatches.mockResolvedValue({
      matches: [{ id: 'match-3', homeTeam: 'Club E', awayTeam: 'Club F', homeGamesWon: 1, awayGamesWon: 1 }],
      total: 25,
      page: 2,
      pageSize: 10,
      hasNext: false,
    })
    fireEvent.click(screen.getAllByRole('button', { name: 'Última' })[0])

    await screen.findAllByText('Pàgina 3 de 3')
    expect(searchMatches).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 2 }),
      'token',
      expect.anything(),
      expect.anything(),
    )
    for (const button of screen.getAllByRole('button', { name: 'Següent' })) expect(button).toBeDisabled()
    for (const button of screen.getAllByRole('button', { name: 'Última' })) expect(button).toBeDisabled()
    for (const button of screen.getAllByRole('button', { name: 'Primera' })) expect(button).not.toBeDisabled()
    for (const button of screen.getAllByRole('button', { name: 'Anterior' })) expect(button).not.toBeDisabled()
  })
})
