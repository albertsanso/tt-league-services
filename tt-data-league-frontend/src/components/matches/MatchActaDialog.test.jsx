import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MatchActaDialog from './MatchActaDialog.jsx'
import { getMatchDetails } from '../../api/matches.js'
import { useAuth } from '../../context/useAuth.js'

vi.mock('../../context/useAuth.js', () => ({
  useAuth: vi.fn(),
}))

vi.mock('../../api/matches.js', () => ({
  getMatchDetails: vi.fn(),
}))

const homeTeam = { id: 'home-team', name: 'ARTEAL SANTIAGO' }
const awayTeam = { id: 'away-team', name: 'UBU TPF BURGOS 2031' }

function player(name, license, playerSeasonId) {
  return { playerSeasonId, federatedPlayerId: null, canonicalPlayerId: null, name, license }
}

function lineup(team, letter, position, playerRef, ranking) {
  return { id: `${team.id}-${letter}`, team, letter, position, player: playerRef, ranking }
}

function game(id, gameNumber, crossover, homePlayer, awayPlayer, sets, homeSetsWon, awaySetsWon, winnerSide, cumulativeHomeSetsWon, cumulativeAwaySetsWon) {
  return {
    id,
    gameNumber,
    type: 'INDIVIDUAL',
    crossover,
    homePlayer,
    awayPlayer,
    homeSetsWon,
    awaySetsWon,
    winnerSide,
    cumulativeHomeSetsWon,
    cumulativeAwaySetsWon,
    notPlayed: false,
    reason: null,
    sets: sets.map(([home, away], index) => ({ id: `${id}-set-${index}`, setNumber: index + 1, homePoints: home, awayPoints: away })),
    doublesPlayers: [],
  }
}

const sgouropoulos = player('SGOUROPOULOS, IOANNIS', '44380', 'p-a')
const besnier = player('BESNIER, CELIAN', '47130', 'p-y')
const molina = player('MOLINA SOTO, MIGUEL', '16161', 'p-b')
const belik = player('BELIK, SIMON', '44659', 'p-x')
const manhani = player('MANHANI JUNIOR, HUMBERTO', '31328', 'p-c')
const sanchez = player('SANCHEZ TERRONES, JUAN PEDRO', '18622', 'p-z')

const fullMatch = {
  id: 'match-1',
  round: 1,
  dateTime: '2025-09-26T18:00:00+02:00',
  city: 'Santiago de Compostela (A Coruña)',
  venue: 'CAMPO DE FUTBOL SAN LÁZARO',
  homeTeam,
  awayTeam,
  refereeName: 'NATALIA FERNANDEZ LORENZO',
  homeGamesWon: 4,
  awayGamesWon: 1,
  lineups: [
    lineup(homeTeam, 'A', 1, sgouropoulos, 2465.1),
    lineup(homeTeam, 'B', 2, molina, 1998.7),
    lineup(homeTeam, 'C', 3, manhani, 2293.1),
    lineup(awayTeam, 'Y', 1, besnier, 2448.0),
    lineup(awayTeam, 'X', 2, belik, 2136.9),
    lineup(awayTeam, 'Z', 3, sanchez, 2070.7),
  ],
  games: [
    game('g1', 1, 'A vs Y', sgouropoulos, besnier, [[11, 8], [13, 15], [11, 4], [11, 8]], 3, 1, 'HOME', 1, 0),
    game('g2', 2, 'B vs X', molina, belik, [[9, 11], [11, 8], [8, 11], [6, 11]], 1, 3, 'AWAY', 1, 1),
    game('g3', 3, 'C vs Z', manhani, sanchez, [[11, 6], [11, 7], [7, 11], [8, 11], [15, 13]], 3, 2, 'HOME', 2, 1),
    game('g4', 4, 'A vs X', sgouropoulos, belik, [[8, 11], [11, 9], [11, 8], [2, 11], [11, 7]], 3, 2, 'HOME', 3, 1),
    game('g5', 5, 'C vs Y', manhani, besnier, [[13, 11], [11, 5], [11, 9]], 3, 0, 'HOME', 4, 1),
  ],
}

describe('MatchActaDialog', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn() })
  })

  afterEach(cleanup)

  it('renders as a modal dialog with the score header and all individual match rows', async () => {
    getMatchDetails.mockResolvedValue(fullMatch)
    render(<MatchActaDialog matchId="match-1" onClose={vi.fn()} />)

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(await screen.findByText('Jornada 1')).toBeInTheDocument()
    expect(screen.getAllByText('ARTEAL SANTIAGO').length).toBeGreaterThan(0)
    expect(screen.getAllByText('UBU TPF BURGOS 2031').length).toBeGreaterThan(0)
    expect(screen.getByText('4')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(6) // header + 5 games
  })

  it('renders blank trailing set columns for a match decided before 5 sets', async () => {
    getMatchDetails.mockResolvedValue(fullMatch)
    render(<MatchActaDialog matchId="match-1" onClose={vi.fn()} />)

    const lastRow = (await screen.findAllByRole('row')).at(-1)
    const cells = [...lastRow.querySelectorAll('td')]
    // position, home player, position, away player, J1..J5, parcial, acumulado
    expect(cells[4]).toHaveTextContent('13-11')
    expect(cells[5]).toHaveTextContent('11-5')
    expect(cells[6]).toHaveTextContent('11-9')
    expect(cells[7]).toHaveTextContent('')
    expect(cells[8]).toHaveTextContent('')
  })

  it('computes the games total from the sets data', async () => {
    getMatchDetails.mockResolvedValue(fullMatch)
    render(<MatchActaDialog matchId="match-1" onClose={vi.fn()} />)

    await screen.findByText('Jornada 1')
    expect(screen.getByText('13 / 8')).toBeInTheDocument()
  })

  it('shows venue and referee lines', async () => {
    getMatchDetails.mockResolvedValue(fullMatch)
    render(<MatchActaDialog matchId="match-1" onClose={vi.fn()} />)

    expect(await screen.findByText(/CAMPO DE FUTBOL SAN LÁZARO - Santiago de Compostela \(A Coruña\)/)).toBeInTheDocument()
    expect(screen.getByText(/NATALIA FERNANDEZ LORENZO/)).toBeInTheDocument()
  })

  it('shows a no-acta message when the match has no games', async () => {
    getMatchDetails.mockResolvedValue({ ...fullMatch, games: [], lineups: [] })
    render(<MatchActaDialog matchId="match-1" onClose={vi.fn()} />)

    expect(await screen.findByText('No hi ha dades d’acta disponibles per a aquest partit.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('calls onClose when Escape is pressed or when the backdrop is clicked', async () => {
    getMatchDetails.mockResolvedValue(fullMatch)
    const onClose = vi.fn()
    render(<MatchActaDialog matchId="match-1" onClose={onClose} />)
    await screen.findByRole('dialog')

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(1)

    fireEvent.mouseDown(screen.getByRole('presentation'))
    expect(onClose).toHaveBeenCalledTimes(2)
  })

  it('does not close when clicking inside the dialog panel', async () => {
    getMatchDetails.mockResolvedValue(fullMatch)
    const onClose = vi.fn()
    render(<MatchActaDialog matchId="match-1" onClose={onClose} />)

    fireEvent.mouseDown(await screen.findByRole('dialog'))
    expect(onClose).not.toHaveBeenCalled()
  })
})
