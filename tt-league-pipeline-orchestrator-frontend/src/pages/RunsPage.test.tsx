import { act, render, screen } from '@testing-library/react'
import type { Api } from '../api/bindApi'
import type { Page, RunSummary } from '../api/types'
import { AuthContext } from '../auth/authContext'
import type { AuthContextValue } from '../auth/authContext'
import { RunEventsContext } from '../events/eventsContext'
import type { PipelineEventListener } from '../events/eventsContext'
import { TestApiProvider } from '../test/TestApiProvider'
import RunsPage from './RunsPage'

function run(id: string, source: RunSummary['source']): RunSummary {
  return {
    id,
    source,
    season: '2025-2026',
    filters: [],
    fullSeason: false,
    trigger: 'MANUAL',
    requestedBy: 'ana',
    force: false,
    status: 'SUCCEEDED',
    createdAt: '2026-10-01T10:00:00Z',
    startedAt: null,
    finishedAt: null,
    durationMs: null,
    error: null,
    ingestRunId: null,
    importJobId: null,
    retryOfRunId: null,
  }
}

function page(...items: RunSummary[]): Page<RunSummary> {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1 }
}

function setup(permissions: string[], listRuns: ReturnType<typeof vi.fn>) {
  const listeners = new Set<PipelineEventListener>()
  const auth: AuthContextValue = {
    status: 'signed-in',
    token: 't',
    user: { username: 'u', roles: [], permissions, expiresAt: Date.now() + 60_000 },
    signIn: async () => undefined,
    signOut: () => undefined,
    getToken: () => 't',
  }
  const api = { runs: { listRuns } } as unknown as Partial<Api>
  render(
    <AuthContext.Provider value={auth}>
      <RunEventsContext.Provider
        value={{
          connection: { state: 'open' },
          subscribe: (listener) => {
            listeners.add(listener)
            return () => {
              listeners.delete(listener)
            }
          },
        }}
      >
        <TestApiProvider api={api}>
          <RunsPage />
        </TestApiProvider>
      </RunEventsContext.Provider>
    </AuthContext.Provider>,
  )
  return {
    emit: (event: Parameters<PipelineEventListener>[0]) =>
      act(async () => {
        listeners.forEach((listener) => listener(event))
      }),
  }
}

describe('RunsPage', () => {
  it('lists the latest runs', async () => {
    const listRuns = vi.fn().mockResolvedValue(page(run('1', 'RFETM'), run('2', 'FCTT')))
    setup([], listRuns)

    expect(await screen.findByText('RFETM')).toBeInTheDocument()
    expect(screen.getByText('FCTT')).toBeInTheDocument()
    expect(listRuns).toHaveBeenCalledWith({ page: 0, size: 20 }, expect.any(AbortSignal))
  })

  it('refetches on a run event and after a reconnect, but not on step events', async () => {
    const listRuns = vi
      .fn()
      .mockResolvedValueOnce(page(run('1', 'RFETM')))
      .mockResolvedValue(page(run('1', 'RFETM'), run('2', 'BCNESA')))
    const { emit } = setup([], listRuns)
    await screen.findByText('RFETM')

    await emit({ type: 'run', payload: run('2', 'BCNESA') })
    expect(await screen.findByText('BCNESA')).toBeInTheDocument()
    expect(listRuns).toHaveBeenCalledTimes(2)

    await emit({ type: 'reconnected' })
    expect(listRuns).toHaveBeenCalledTimes(3)

    await emit({ type: 'step', payload: {} as never })
    expect(listRuns).toHaveBeenCalledTimes(3)
  })

  it('disables Run now without matches:write', async () => {
    setup(['other'], vi.fn().mockResolvedValue(page()))
    expect(await screen.findByRole('button', { name: 'Run now' })).toBeDisabled()
  })

  it('enables Run now with matches:write', async () => {
    setup(['matches:write'], vi.fn().mockResolvedValue(page()))
    expect(await screen.findByRole('button', { name: 'Run now' })).toBeEnabled()
  })

  it('shows an error when the runs cannot be loaded', async () => {
    setup([], vi.fn().mockRejectedValue(new Error('Cannot reach the server')))
    expect(await screen.findByText('Cannot reach the server')).toBeInTheDocument()
  })
})
