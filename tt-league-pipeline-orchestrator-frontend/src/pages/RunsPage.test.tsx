import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { ApiError } from '../api/ApiError'
import type { Api } from '../api/bindApi'
import { AuthContext } from '../auth/authContext'
import type { AuthContextValue } from '../auth/authContext'
import { createEventBus } from '../test/eventBus'
import { FakeEvents } from '../test/FakeEvents'
import { makePage, makeRun, makeStep } from '../test/runFixtures'
import { TestApiProvider } from '../test/TestApiProvider'
import RunsPage from './RunsPage'

function Location() {
  const location = useLocation()
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>
}

function setup(options: { permissions?: string[]; listRuns: ReturnType<typeof vi.fn>; triggerRun?: ReturnType<typeof vi.fn>; path?: string }) {
  const bus = createEventBus()
  const auth: AuthContextValue = {
    status: 'signed-in',
    token: 't',
    user: { username: 'u', roles: [], permissions: options.permissions ?? [], expiresAt: Date.now() + 60_000 },
    signIn: async () => undefined,
    signOut: () => undefined,
    getToken: () => 't',
  }
  const api = { runs: { listRuns: options.listRuns, triggerRun: options.triggerRun } } as unknown as Partial<Api>
  render(
    <MemoryRouter initialEntries={[options.path ?? '/runs']}>
      <AuthContext.Provider value={auth}>
        <FakeEvents bus={bus}>
          <TestApiProvider api={api}>
            <Routes>
              <Route path="/runs" element={<RunsPage />} />
              <Route path="/runs/:runId" element={<div>detail page</div>} />
            </Routes>
            <Location />
          </TestApiProvider>
        </FakeEvents>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
  return { bus, user: userEvent.setup() }
}

const representative = [
  makeRun('full', { source: 'RFETM', fullSeason: true, trigger: 'SCHEDULED', requestedBy: null }),
  makeRun('group', {
    source: 'FCTT',
    filters: [{ category: 'Senior', group: 'A', phase: null, territory: null, gender: null, matchDays: [3, 4] }],
    force: true,
  }),
  makeRun('failed', {
    source: 'BCNESA',
    status: 'FAILED',
    error: { code: 'IMPORT_FAILED', message: 'The import blew up' },
    steps: [{ kind: 'IMPORT', status: 'FAILED', attempt: 2 }],
  }),
  makeRun('active', {
    status: 'RUNNING',
    finishedAt: null,
    durationMs: null,
    steps: [{ kind: 'INGEST', status: 'RUNNING', attempt: 1 }],
  }),
]

describe('RunsPage', () => {
  it('renders trigger, scope, duration, step badges and outcome in API order', async () => {
    setup({ listRuns: vi.fn().mockResolvedValue(makePage(representative)) })

    const table = await screen.findByRole('table', { name: 'Runs' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(4)

    expect(within(rows[0]).getByText('Scheduled')).toBeInTheDocument()
    expect(within(rows[0]).getByText('Full season')).toBeInTheDocument()
    expect(within(rows[0]).getByText('1 min 00 s')).toBeInTheDocument()

    expect(within(rows[1]).getByText(/Manual · ana/)).toBeInTheDocument()
    expect(within(rows[1]).getByText('forced')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Senior · A · md 3, 4')).toBeInTheDocument()
    expect(within(rows[1]).getByLabelText('Ingest: not started')).toBeInTheDocument()

    expect(within(rows[2]).getByText('Failed')).toBeInTheDocument()
    expect(within(rows[2]).getByText('IMPORT_FAILED')).toBeInTheDocument()
    expect(within(rows[2]).getByLabelText('Import: failed, attempt 2')).toBeInTheDocument()

    expect(within(rows[3]).getByText('Running')).toBeInTheDocument()
    expect(within(rows[3]).getByLabelText('Ingest: running, attempt 1')).toBeInTheDocument()
  })

  it('sends the URL filters to the API', async () => {
    const listRuns = vi.fn().mockResolvedValue(makePage([]))
    setup({
      listRuns,
      path: '/runs?source=RFETM&source=FCTT&status=FAILED&from=2026-10-01&to=2026-10-05&page=3',
    })
    await screen.findByText('No runs match these filters.')

    expect(listRuns).toHaveBeenCalledWith(
      {
        source: ['RFETM', 'FCTT'],
        status: ['FAILED'],
        from: new Date(2026, 9, 1).toISOString(),
        to: new Date(2026, 9, 6).toISOString(),
        page: 2,
        size: 20,
      },
      expect.any(AbortSignal),
    )
  })

  it('updates the URL and resets the page when a filter changes', async () => {
    const listRuns = vi.fn().mockResolvedValue(makePage([makeRun('a')], 1, 40))
    const { user } = setup({ listRuns, path: '/runs?page=2' })
    await screen.findByRole('table', { name: 'Runs' })

    await user.click(screen.getByRole('combobox', { name: 'Source' }))
    await user.click(await screen.findByRole('option', { name: 'FCTT' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/runs?source=FCTT')
    expect(screen.getByTestId('location').textContent).not.toContain('page=')
  })

  it('sends no request and warns when from is after to', async () => {
    const listRuns = vi.fn().mockResolvedValue(makePage([]))
    setup({ listRuns, path: '/runs?from=2026-10-05&to=2026-10-01' })

    expect(await screen.findByText(/"from" date is after the "to" date/)).toBeInTheDocument()
    expect(listRuns).not.toHaveBeenCalled()
  })

  it('shows an empty state without filters', async () => {
    setup({ listRuns: vi.fn().mockResolvedValue(makePage([])) })
    expect(await screen.findByText('No runs yet.')).toBeInTheDocument()
  })

  it('applies live run and step events to a row and the activity log', async () => {
    const { bus } = setup({
      listRuns: vi.fn().mockResolvedValue(makePage([makeRun('a', { status: 'QUEUED', startedAt: null, finishedAt: null })])),
    })
    await screen.findByRole('table', { name: 'Runs' })

    await bus.emit({ type: 'run', payload: makeRun('a', { status: 'RUNNING', finishedAt: null }) })
    await bus.emit({ type: 'step', payload: makeStep('a', { kind: 'INGEST', status: 'RUNNING' }) })

    const row = within(screen.getByRole('table', { name: 'Runs' })).getAllByRole('row')[1]
    expect(within(row).getByText('Running')).toBeInTheDocument()
    expect(within(row).getByLabelText('Ingest: running, attempt 1')).toBeInTheDocument()
    const log = screen.getByRole('log')
    expect(within(log).getByText(/Run running/)).toBeInTheDocument()
    expect(within(log).getByText(/Ingest started/)).toBeInTheDocument()
  })

  it('ticks the duration of an active row', async () => {
    const started = new Date(Date.now() - 5000).toISOString()
    setup({
      listRuns: vi
        .fn()
        .mockResolvedValue(makePage([makeRun('a', { status: 'RUNNING', startedAt: started, finishedAt: null, durationMs: null })])),
    })
    await screen.findByRole('table', { name: 'Runs' })
    const before = within(screen.getByRole('table', { name: 'Runs' })).getAllByRole('row')[1].textContent

    await act(async () => {
      await new Promise((done) => setTimeout(done, 2200))
    })

    expect(within(screen.getByRole('table', { name: 'Runs' })).getAllByRole('row')[1].textContent).not.toBe(before)
  })

  it('disables Run now without matches:write and opens the dialog with it', async () => {
    const first = setup({ permissions: ['other'], listRuns: vi.fn().mockResolvedValue(makePage([])) })
    expect(await screen.findByRole('button', { name: 'Run now' })).toBeDisabled()
    expect(first.user).toBeDefined()
  })

  it('opens the Run now dialog with matches:write and reports a successful trigger', async () => {
    const listRuns = vi.fn().mockResolvedValue(makePage([makeRun('a')]))
    const triggerRun = vi.fn().mockResolvedValue({
      status: 201,
      response: { results: [{ source: 'RFETM', outcome: 'CREATED', run: makeRun('new') }] },
    })
    const { user } = setup({ permissions: ['matches:write'], listRuns, triggerRun })
    await screen.findByRole('table', { name: 'Runs' })

    await user.click(screen.getByRole('button', { name: 'Run now' }))
    await user.click(screen.getByRole('radio', { name: 'RFETM' }))
    await user.type(screen.getByLabelText('Season'), '2025-2026')
    await user.click(screen.getByRole('radio', { name: 'Open match days' }))
    await user.click(screen.getByRole('button', { name: 'Start run' }))

    expect(await screen.findByRole('link', { name: 'Open run' })).toHaveAttribute('href', '/runs/new')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(listRuns).toHaveBeenCalledTimes(2)
  })

  it('opens the detail with the list filters as back target', async () => {
    const { user } = setup({
      listRuns: vi.fn().mockResolvedValue(makePage([makeRun('a')])),
      path: '/runs?source=RFETM',
    })
    await screen.findByRole('table', { name: 'Runs' })

    await user.click(within(screen.getByRole('table', { name: 'Runs' })).getAllByRole('row')[1])

    expect(await screen.findByText('detail page')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/runs/a')
  })

  it('shows an error when the runs cannot be loaded', async () => {
    setup({ listRuns: vi.fn().mockRejectedValue(new ApiError(0, null, 'Cannot reach the server')) })
    expect(await screen.findByText('Cannot reach the server')).toBeInTheDocument()
  })
})
