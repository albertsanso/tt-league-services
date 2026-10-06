import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { ApiError } from '../api/ApiError'
import type { Api } from '../api/bindApi'
import type { MatchDayDetail } from '../api/types'
import { AuthContext } from '../auth/authContext'
import type { AuthContextValue } from '../auth/authContext'
import { createEventBus } from '../test/eventBus'
import { FakeEvents } from '../test/FakeEvents'
import { makeMatchDayDetail, makeResults, makeSummary } from '../test/matchDayFixtures'
import { makeRun } from '../test/runFixtures'
import { TestApiProvider } from '../test/TestApiProvider'
import MatchDayDetailPage from './MatchDayDetailPage'

function Location() {
  const location = useLocation()
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>
}

type Mock = ReturnType<typeof vi.fn>

interface Mocks {
  getMatchDay: Mock
  getMatchDayResults: Mock
  closeMatchDay: Mock
  reopenMatchDay: Mock
  ignoreMatch: Mock
  unignoreMatch: Mock
  addMatchDayNote: Mock
  refreshMatchDay: Mock
}

function setup(options: { permissions?: string[]; mocks?: Partial<Mocks>; state?: unknown } = {}) {
  const bus = createEventBus()
  const mocks: Mocks = {
    getMatchDay: vi.fn().mockResolvedValue(makeMatchDayDetail()),
    getMatchDayResults: vi.fn().mockResolvedValue(makeResults()),
    closeMatchDay: vi.fn(),
    reopenMatchDay: vi.fn(),
    ignoreMatch: vi.fn(),
    unignoreMatch: vi.fn(),
    addMatchDayNote: vi.fn(),
    refreshMatchDay: vi.fn(),
    ...options.mocks,
  }
  const auth: AuthContextValue = {
    status: 'signed-in',
    token: 't',
    user: { username: 'u', roles: [], permissions: options.permissions ?? ['matches:write'], expiresAt: Date.now() + 60_000 },
    signIn: async () => undefined,
    signOut: () => undefined,
    getToken: () => 't',
  }
  render(
    <MemoryRouter initialEntries={[{ pathname: '/calendar/match-days/day-1', state: options.state }]}>
      <AuthContext.Provider value={auth}>
        <FakeEvents bus={bus}>
          <TestApiProvider api={{ matchDays: mocks } as unknown as Partial<Api>}>
            <Routes>
              <Route path="/calendar/match-days/:matchDayId" element={<MatchDayDetailPage />} />
              <Route path="/calendar" element={<div>calendar page</div>} />
              <Route path="/runs/:runId" element={<div>run page</div>} />
            </Routes>
            <Location />
          </TestApiProvider>
        </FakeEvents>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
  return { bus, mocks, user: userEvent.setup() }
}

const closedDetail = (): MatchDayDetail => {
  const base = makeMatchDayDetail()
  return { ...base, matchDay: { ...base.matchDay, state: 'CLOSED', closeReason: 'MANUAL', closedBy: 'ana', closedAt: '2026-10-05T08:00:00Z' } }
}

function problem(status: number, message: string, extra: Record<string, unknown> = {}) {
  return new ApiError(status, { status, detail: message, ...extra }, message)
}

describe('MatchDayDetailPage', () => {
  it('shows the key, state, completion, progress and the counts per status', async () => {
    setup()

    expect(await screen.findByRole('heading', { name: 'TERCERA-masculino · G2 · 1a Fase · J3' })).toBeInTheDocument()
    expect(screen.getByText('Has overdue')).toBeInTheDocument()
    expect(screen.getByText('Open')).toBeInTheDocument()
    expect(screen.getByText('2 / 4 reported')).toBeInTheDocument()
    expect(screen.getByText(/FCTT · 2026-2027 · 2026-10-04 – 2026-10-05 \(grace until 2026-10-07\)/)).toBeInTheDocument()
    const counts = screen.getByRole('list', { name: 'Matches per status' })
    expect(within(counts).getByText('Reported: 2')).toBeInTheDocument()
    expect(within(counts).getByText('Overdue: 2')).toBeInTheDocument()
    expect(within(counts).getByText('Postponed: 1')).toBeInTheDocument()
    expect(within(counts).getByText('Ignored: 1')).toBeInTheDocument()
  })

  it('lists the matches with status, result and reported-at', async () => {
    setup()

    const table = await screen.findByRole('table', { name: 'Matches' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(5)
    const reported = rows[0]
    expect(within(reported).getByText('Home m-reported')).toBeInTheDocument()
    expect(within(reported).getByText('Reported')).toBeInTheDocument()
    expect(await within(reported).findByText('3 – 1')).toBeInTheDocument()
    expect(within(reported).getByRole('link', { name: 'Run' })).toHaveAttribute('href', '/runs/run-1')
    expect(within(rows[1]).getByText('2 – 3')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Postponed')).toBeInTheDocument()
    expect(within(rows[2]).getAllByRole('cell')[4]).toHaveTextContent('—')
    expect(within(rows[3]).getByText('Overdue')).toBeInTheDocument()
    expect(within(rows[4]).getByText(/ana ·/)).toBeInTheDocument()
    expect(within(rows[4]).getByRole('button', { name: 'Stop ignoring' })).toBeInTheDocument()
  })

  it('warns when the results cannot be read and shows them as unavailable without blocking the page', async () => {
    const { mocks, user } = setup({
      mocks: { getMatchDayResults: vi.fn().mockRejectedValue(problem(502, 'The platform rejected the request; check the configured API key')) },
    })

    expect(await screen.findByText(/Results could not be read from the platform: The platform rejected the request/)).toBeInTheDocument()
    const rows = within(screen.getByRole('table', { name: 'Matches' })).getAllByRole('row').slice(1)
    expect(within(rows[0]).getByText('unavailable')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Timeline' })).toBeInTheDocument()
    mocks.getMatchDayResults.mockResolvedValue(makeResults())
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('3 – 1')).toBeInTheDocument()
    expect(screen.queryByText(/Results could not be read/)).not.toBeInTheDocument()
  })

  it('shows the timeline newest first with run links and status chips', async () => {
    setup()

    const timeline = await screen.findByRole('list', { name: 'Timeline' })
    const items = within(timeline).getAllByRole('listitem')
    expect(items).toHaveLength(11)
    expect(items[0]).toHaveTextContent('Match day reopened by ana')
    expect(items[1]).toHaveTextContent('Match day closed by ana')
    expect(items[1]).toHaveTextContent('League decision')
    const refresh = items.find((item) => item.textContent?.includes('Refresh requested by ana'))
    expect(refresh).toBeDefined()
    expect(within(refresh!).getByRole('link', { name: 'Open run' })).toHaveAttribute('href', '/runs/run-2')
    const runItem = items.find((item) => item.textContent?.includes('Run run-2'))
    expect(runItem).toHaveTextContent('Manual by ana')
    expect(within(runItem!).getByRole('link', { name: 'Run run-2' })).toHaveAttribute('href', '/runs/run-2')
    expect(runItem).toHaveTextContent('Running')
    expect(items.some((item) => item.textContent?.includes('Match reported · Home m-reported'))).toBe(true)
    expect(items.some((item) => item.textContent?.includes('Called the club'))).toBe(true)
  })

  it('goes back to the calendar with the search it came from', async () => {
    const { user } = setup({ state: { backTo: '?view=week&date=2026-10-05' } })

    await user.click(await screen.findByRole('link', { name: 'Calendar' }))

    expect(screen.getByText('calendar page')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/calendar')
  })

  it('shows not found with the way back', async () => {
    setup({ mocks: { getMatchDay: vi.fn().mockRejectedValue(problem(404, 'Match day not found', { code: 'MATCH_DAY_NOT_FOUND' })) } })

    expect(await screen.findByRole('heading', { name: 'Match day not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Calendar' })).toHaveAttribute('href', '/calendar')
  })

  it('shows a load error with Retry', async () => {
    const getMatchDay = vi.fn().mockRejectedValueOnce(new Error('Cannot reach the server'))
    getMatchDay.mockResolvedValue(makeMatchDayDetail())
    const { user } = setup({ mocks: { getMatchDay } })

    expect(await screen.findByText('Cannot reach the server')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByRole('heading', { name: /J3/ })).toBeInTheDocument()
  })

  describe('actions', () => {
    it('closes the match day with a note and shows the returned detail', async () => {
      const { mocks, user } = setup()
      mocks.closeMatchDay.mockResolvedValue(closedDetail())

      await user.click(await screen.findByRole('button', { name: 'Close match day' }))
      const dialog = await screen.findByRole('dialog', { name: 'Close match day' })
      await user.type(within(dialog).getByRole('textbox', { name: 'Note (optional)' }), 'League decision')
      await user.click(within(dialog).getByRole('button', { name: 'Close match day' }))

      expect(mocks.closeMatchDay).toHaveBeenCalledWith('day-1', 'League decision')
      expect(await screen.findByText(/Closed .* by ana — closed by an operator/)).toBeInTheDocument()
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Reopen match day' })).toBeInTheDocument()
    })

    it('reopens a closed match day without a note', async () => {
      const { mocks, user } = setup({ mocks: { getMatchDay: vi.fn().mockResolvedValue(closedDetail()) } })
      mocks.reopenMatchDay.mockResolvedValue(makeMatchDayDetail())

      await user.click(await screen.findByRole('button', { name: 'Reopen match day' }))
      await user.click(within(await screen.findByRole('dialog', { name: 'Reopen match day' })).getByRole('button', { name: 'Reopen match day' }))

      expect(mocks.reopenMatchDay).toHaveBeenCalledWith('day-1', undefined)
      expect(await screen.findByRole('button', { name: 'Close match day' })).toBeInTheDocument()
    })

    it('ignores and stops ignoring a match', async () => {
      const { mocks, user } = setup()
      mocks.ignoreMatch.mockResolvedValue(makeMatchDayDetail())
      mocks.unignoreMatch.mockResolvedValue(makeMatchDayDetail())
      const rows = within(await screen.findByRole('table', { name: 'Matches' })).getAllByRole('row').slice(1)

      await user.click(within(rows[3]).getByRole('button', { name: 'Ignore' }))
      const ignoreDialog = await screen.findByRole('dialog', { name: 'Ignore match' })
      expect(ignoreDialog).toHaveTextContent('Home m-overdue – Away m-overdue')
      await user.type(within(ignoreDialog).getByRole('textbox'), 'forfeit')
      await user.click(within(ignoreDialog).getByRole('button', { name: 'Ignore match' }))
      expect(mocks.ignoreMatch).toHaveBeenCalledWith('day-1', 'm-overdue', 'forfeit')

      await user.click(within(rows[4]).getByRole('button', { name: 'Stop ignoring' }))
      await user.click(within(await screen.findByRole('dialog', { name: 'Stop ignoring match' })).getByRole('button', { name: 'Stop ignoring' }))
      expect(mocks.unignoreMatch).toHaveBeenCalledWith('day-1', 'm-ignored', undefined)
    })

    it('adds a note to the match day and to a match', async () => {
      const { mocks, user } = setup()
      mocks.addMatchDayNote.mockResolvedValue(makeMatchDayDetail())

      await screen.findByRole('table', { name: 'Matches' })
      await user.click(screen.getAllByRole('button', { name: 'Add note' })[0])
      const dayDialog = await screen.findByRole('dialog', { name: 'Add note' })
      expect(within(dayDialog).getByRole('combobox', { name: 'Applies to' })).toHaveTextContent('The match day')
      await user.type(within(dayDialog).getByRole('textbox', { name: /^Note/ }), 'Rescheduled by phone')
      await user.click(within(dayDialog).getByRole('button', { name: 'Add note' }))
      expect(mocks.addMatchDayNote).toHaveBeenLastCalledWith('day-1', 'Rescheduled by phone', undefined)

      const rows = within(screen.getByRole('table', { name: 'Matches' })).getAllByRole('row').slice(1)
      await user.click(within(rows[2]).getByRole('button', { name: 'Add note' }))
      const matchDialog = await screen.findByRole('dialog', { name: 'Add note' })
      expect(within(matchDialog).getByRole('combobox', { name: 'Applies to' })).toHaveTextContent('Home m-postponed – Away m-postponed')
      await user.type(within(matchDialog).getByRole('textbox', { name: /^Note/ }), 'Moved to Friday')
      await user.click(within(matchDialog).getByRole('button', { name: 'Add note' }))
      expect(mocks.addMatchDayNote).toHaveBeenLastCalledWith('day-1', 'Moved to Friday', 'm-postponed')
    })

    it('shows the server message of a conflict and reloads the match day', async () => {
      const { mocks, user } = setup()
      mocks.closeMatchDay.mockRejectedValue(problem(409, 'Match day is already closed', { code: 'ILLEGAL_TRANSITION' }))

      await user.click(await screen.findByRole('button', { name: 'Close match day' }))
      const dialog = await screen.findByRole('dialog', { name: 'Close match day' })
      await user.click(within(dialog).getByRole('button', { name: 'Close match day' }))

      expect(await within(dialog).findByText('Match day is already closed')).toBeInTheDocument()
      expect(mocks.getMatchDay).toHaveBeenCalledTimes(2)
      expect(mocks.closeMatchDay).toHaveBeenCalledTimes(1)
    })

    it('refreshes the group, reporting the created run', async () => {
      const { mocks, user } = setup()
      mocks.refreshMatchDay.mockResolvedValue({
        status: 201,
        response: { results: [{ source: 'FCTT', outcome: 'CREATED', run: makeRun('run-9') }] },
      })

      await user.click(await screen.findByRole('button', { name: 'Refresh group' }))
      const dialog = await screen.findByRole('dialog', { name: 'Refresh group' })
      expect(dialog).toHaveTextContent('this round of the group')
      await user.click(within(dialog).getByRole('checkbox', { name: 'Ignore the ingest no-change check' }))
      await user.click(within(dialog).getByRole('button', { name: 'Start refresh' }))

      expect(mocks.refreshMatchDay).toHaveBeenCalledWith('day-1', true)
      expect(await screen.findByText(/refresh run created/)).toBeInTheDocument()
      expect(within(screen.getByRole('alert')).getByRole('link', { name: 'Open run' })).toHaveAttribute('href', '/runs/run-9')
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('reports a queued refresh', async () => {
      const { mocks, user } = setup()
      mocks.refreshMatchDay.mockResolvedValue({
        status: 202,
        response: { results: [{ source: 'FCTT', outcome: 'QUEUED', activeRunId: 'run-5' }] },
      })

      await user.click(await screen.findByRole('button', { name: 'Refresh group' }))
      await user.click(within(await screen.findByRole('dialog', { name: 'Refresh group' })).getByRole('button', { name: 'Start refresh' }))

      expect(mocks.refreshMatchDay).toHaveBeenCalledWith('day-1', false)
      expect(await screen.findByText(/refresh queued/)).toBeInTheDocument()
    })

    it.each([
      [409, 'FCTT already has an active run', 'ACTIVE_RUN', 'REJECTED'],
      [422, 'The ingest has no match-day status', 'NO_INGEST_STATUS', 'UNAVAILABLE'],
    ] as const)('keeps the dialog open with the results on %i', async (status, message, code, outcome) => {
      const { mocks, user } = setup()
      mocks.refreshMatchDay.mockRejectedValue(
        problem(status, message, { code, results: [{ source: 'FCTT', outcome, code, message, activeRunId: status === 409 ? 'run-5' : undefined }] }),
      )

      await user.click(await screen.findByRole('button', { name: 'Refresh group' }))
      const dialog = await screen.findByRole('dialog', { name: 'Refresh group' })
      await user.click(within(dialog).getByRole('button', { name: 'Start refresh' }))

      expect(await within(dialog).findByText(new RegExp(`FCTT — ${message}`))).toBeInTheDocument()
      expect(within(dialog).getByText(new RegExp(`\\(${code}\\)`))).toBeInTheDocument()
      if (status === 409) {
        expect(within(dialog).getByRole('link', { name: 'Open active run' })).toHaveAttribute('href', '/runs/run-5')
      }
      expect(screen.getByRole('dialog', { name: 'Refresh group' })).toBeInTheDocument()
    })

    it('refreshes a whole category for RFETM and says so', async () => {
      const rfetm = makeMatchDayDetail()
      const { user } = setup({
        mocks: { getMatchDay: vi.fn().mockResolvedValue({ ...rfetm, matchDay: { ...rfetm.matchDay, source: 'RFETM' } }) },
      })

      await user.click(await screen.findByRole('button', { name: 'Refresh group' }))

      expect(await screen.findByRole('dialog', { name: 'Refresh group' })).toHaveTextContent('whole category')
    })
  })

  describe('permissions', () => {
    it('disables every action without matches:write', async () => {
      setup({ permissions: [] })

      expect(await screen.findByRole('button', { name: 'Refresh group' })).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Close match day' })).toBeDisabled()
      const addNotes = screen.getAllByRole('button', { name: 'Add note' })
      addNotes.forEach((button) => expect(button).toBeDisabled())
      screen.getAllByRole('button', { name: /^(Ignore|Stop ignoring)$/ }).forEach((button) => expect(button).toBeDisabled())
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  describe('live updates', () => {
    it('refetches when a match-days event names this match day', async () => {
      const { bus, mocks } = setup()
      await screen.findByRole('table', { name: 'Matches' })
      mocks.getMatchDay.mockResolvedValue(closedDetail())

      await bus.emit({ type: 'match-days', payload: { source: 'FCTT', season: '2026-2027', matchDayId: 'day-1', cause: 'ACTION' } })

      expect(await screen.findByText(/Closed .* by ana/)).toBeInTheDocument()
      expect(mocks.getMatchDay).toHaveBeenCalledTimes(2)
    })

    it('refetches after a recompute of its source and season but not for another match day or season', async () => {
      const { bus, mocks } = setup()
      await screen.findByRole('table', { name: 'Matches' })

      await bus.emit({ type: 'match-days', payload: { source: 'FCTT', season: '2026-2027', matchDayId: 'other', cause: 'ACTION' } })
      await bus.emit({ type: 'match-days', payload: { source: 'RFETM', season: '2026-2027', matchDayId: null, cause: 'RECOMPUTED' } })
      await bus.emit({ type: 'match-days', payload: { source: 'FCTT', season: '2025-2026', matchDayId: null, cause: 'RECOMPUTED' } })
      expect(mocks.getMatchDay).toHaveBeenCalledTimes(1)

      await bus.emit({ type: 'match-days', payload: { source: 'FCTT', season: '2026-2027', matchDayId: null, cause: 'RECOMPUTED' } })
      await vi.waitFor(() => expect(mocks.getMatchDay).toHaveBeenCalledTimes(2))
    })

    it('updates a run of the timeline from a run event without a request', async () => {
      const { bus, mocks } = setup()
      const timeline = await screen.findByRole('list', { name: 'Timeline' })
      expect(within(timeline).getByText('Running')).toBeInTheDocument()

      await bus.emit({ type: 'run', payload: { ...makeRun('run-2', { createdAt: '2026-10-05T07:30:00Z', requestedBy: 'ana' }), status: 'SUCCEEDED' } })

      expect(await within(timeline).findAllByText('Succeeded')).toHaveLength(2)
      expect(within(timeline).queryByText('Running')).not.toBeInTheDocument()
      expect(mocks.getMatchDay).toHaveBeenCalledTimes(1)
    })

    it('reads the results again when the number of reported matches changes', async () => {
      const { bus, mocks } = setup()
      await screen.findByText('3 – 1')
      expect(mocks.getMatchDayResults).toHaveBeenCalledTimes(1)
      const more = makeMatchDayDetail()
      mocks.getMatchDay.mockResolvedValue({ ...more, matchDay: { ...more.matchDay, ...makeSummary('day-1', { reportedMatches: 3, totalMatches: 4 }) } })

      await bus.emit({ type: 'reconnected' })

      await vi.waitFor(() => expect(mocks.getMatchDayResults).toHaveBeenCalledTimes(2))
    })

    it('refetches after a reconnect', async () => {
      const { bus, mocks } = setup()
      await screen.findByRole('table', { name: 'Matches' })

      await bus.emit({ type: 'reconnected' })

      await vi.waitFor(() => expect(mocks.getMatchDay).toHaveBeenCalledTimes(2))
    })
  })
})
