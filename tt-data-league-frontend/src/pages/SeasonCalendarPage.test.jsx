import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SeasonCalendarPage from './SeasonCalendarPage.jsx'
import {
  clearMatchOverdueMark,
  getMatchOptions,
  getSeasonCalendar,
  markMatchOverdue,
} from '../api/matches.js'
import { useAuth } from '../context/useAuth.js'

vi.mock('../context/useAuth.js', () => ({ useAuth: vi.fn() }))
vi.mock('../api/matches.js', () => ({
  getMatchOptions: vi.fn(),
  getSeasonCalendar: vi.fn(),
  markMatchOverdue: vi.fn(),
  clearMatchOverdueMark: vi.fn(),
  searchMatches: vi.fn(),
  getMatchDetails: vi.fn(),
}))

const scheduled = (overrides = {}) => ({
  id: 'match-1',
  dateTime: '2026-09-05T18:30:00+02:00',
  venue: 'Pavelló',
  homeTeamName: 'Club A',
  awayTeamName: 'Club B',
  homeGamesWon: null,
  awayGamesWon: null,
  status: 'SCHEDULED',
  calendarState: 'AWAITING_RESULT',
  overdueMarked: false,
  overdueMarkedAt: null,
  overdueMarkedBy: null,
  ...overrides,
})

const calendarWith = (matches) => ({
  source: 'FCTT',
  season: '2026-2027',
  competition: 'tercera',
  today: '2026-09-08',
  overdueGraceDays: 7,
  groups: [{
    groupNumber: 1,
    phase: '1a Fase',
    currentRound: 1,
    lastCompleteRound: null,
    scheduledMatches: 2,
    playedMatches: 1,
    overdueMatches: 1,
    postponedMatches: 0,
    rounds: [
      {
        round: 1,
        firstDate: '2026-09-05',
        lastDate: '2026-09-05',
        scheduledMatches: 2,
        playedMatches: 0,
        complete: false,
        current: true,
        matches,
      },
    ],
  }],
})

function renderPage(path = '/calendari?source=FCTT&season=2026-2027&competition=tercera') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/calendari" element={<SeasonCalendarPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('SeasonCalendarPage', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn(), hasPermission: () => false })
    getMatchOptions.mockResolvedValue({ seasons: ['2026-2027'], competitions: ['tercera'] })
  })
  afterEach(cleanup)

  it('shows group progress, counts and state badges', async () => {
    getSeasonCalendar.mockResolvedValue(calendarWith([
      scheduled({ overdueMarked: true, overdueMarkedBy: 'admin', overdueMarkedAt: '2026-09-08T10:00:00+02:00', calendarState: 'OVERDUE' }),
      scheduled({ id: 'match-2' }),
    ]))

    renderPage()

    expect(await screen.findByText(/Endarrerit \(manual\)/)).toBeInTheDocument()
    expect(await screen.findByText(/marcat per admin/)).toBeInTheDocument()
    expect(await screen.findByText('Pendent d’acta')).toBeInTheDocument()
  })

  it('offers the mark action only to matches:write users', async () => {
    getSeasonCalendar.mockResolvedValue(calendarWith([scheduled()]))
    renderPage()

    await screen.findByText('Club A – Club B')
    expect(screen.queryByRole('button', { name: /Marcar com a endarrerit/ })).toBeNull()
  })

  it('marks a scheduled match and clears it for a write user', async () => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn(), hasPermission: () => true })
    getSeasonCalendar.mockResolvedValue(calendarWith([scheduled()]))
    markMatchOverdue.mockResolvedValue(null)
    clearMatchOverdueMark.mockResolvedValue(null)

    renderPage()

    const markButton = await screen.findByRole('button', { name: /Marcar com a endarrerit/ })
    fireEvent.click(markButton)

    await waitFor(() => expect(markMatchOverdue).toHaveBeenCalledWith(
      'match-1', 'token', undefined, expect.any(Function),
    ))

    getSeasonCalendar.mockResolvedValue(calendarWith([
      scheduled({ overdueMarked: true, calendarState: 'OVERDUE', overdueMarkedBy: 'admin', overdueMarkedAt: '2026-09-08T10:00:00+02:00' }),
    ]))
    await screen.findByRole('button', { name: /Treure la marca d’endarrerit/ })
    fireEvent.click(screen.getByRole('button', { name: /Treure la marca d’endarrerit/ }))

    await waitFor(() => expect(clearMatchOverdueMark).toHaveBeenCalledWith(
      'match-1', 'token', undefined, expect.any(Function),
    ))
  })

  it('shows an empty state when the calendar has no groups', async () => {
    getSeasonCalendar.mockResolvedValue({ ...calendarWith([]), groups: [] })

    renderPage()

    expect(await screen.findByText('No hi ha partits per a aquesta selecció.')).toBeInTheDocument()
  })
})