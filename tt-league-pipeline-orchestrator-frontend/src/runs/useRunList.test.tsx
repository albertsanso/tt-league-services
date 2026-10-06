import { act, render, screen } from '@testing-library/react'
import type { RunListQuery } from '../api/runs'
import type { Page, RunSummary } from '../api/types'
import { createEventBus } from '../test/eventBus'
import { FakeEvents } from '../test/FakeEvents'
import { makePage, makeRun, makeStep, makeUnit } from '../test/runFixtures'
import { TestApiProvider } from '../test/TestApiProvider'
import { useRunList } from './useRunList'

function Harness({ query }: { query: RunListQuery | null }) {
  const { page, loading, error, newRunsAvailable } = useRunList(query)
  return (
    <div>
      <div data-testid="loading">{String(loading)}</div>
      <div data-testid="error">{error ?? ''}</div>
      <div data-testid="new">{String(newRunsAvailable)}</div>
      <ul>
        {page?.items.map((run) => (
          <li key={run.id}>
            {run.id}:{run.status}:{run.steps?.map((step) => `${step.kind}=${step.status}`).join(',')}
            <span data-testid={`units-${run.id}`}>
              {run.units?.map((unit) => `${unit.id}=${unit.status}${unit.progress?.itemsProcessed ?? ''}`).join(',')}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function setup(listRuns: ReturnType<typeof vi.fn>, query: RunListQuery | null = { page: 0, size: 20 }) {
  const bus = createEventBus()
  const view = (q: RunListQuery | null) => (
    <FakeEvents bus={bus}>
      <TestApiProvider api={{ runs: { listRuns } as never }}>
        <Harness query={q} />
      </TestApiProvider>
    </FakeEvents>
  )
  const { rerender } = render(view(query))
  return { bus, rerender: (q: RunListQuery | null) => rerender(view(q)) }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

async function flush() {
  await act(async () => {
    await Promise.resolve()
  })
}

afterEach(() => {
  vi.useRealTimers()
})

describe('useRunList', () => {
  it('loads the first page and aborts the in-flight request when the query changes', async () => {
    const signals: AbortSignal[] = []
    const pending = deferred<Page<RunSummary>>()
    const listRuns = vi.fn((query: RunListQuery, signal: AbortSignal) => {
      signals.push(signal)
      return query.page === 0 ? pending.promise : Promise.resolve(makePage([makeRun('p1')]))
    })
    const { rerender } = setup(listRuns)
    expect(screen.getByTestId('loading')).toHaveTextContent('true')

    rerender({ page: 1, size: 20 })
    expect(await screen.findByText(/^p1:/)).toBeInTheDocument()
    expect(listRuns).toHaveBeenCalledTimes(2)
    expect(signals[0].aborted).toBe(true)

    await act(async () => {
      pending.resolve(makePage([makeRun('late')]))
    })
    expect(screen.queryByText(/^late:/)).not.toBeInTheDocument()
  })

  it('patches a row from a run event without a request', async () => {
    const listRuns = vi.fn().mockResolvedValue(makePage([makeRun('a', { status: 'QUEUED' })]))
    const { bus } = setup(listRuns)
    await screen.findByText(/^a:QUEUED/)

    await bus.emit({ type: 'run', payload: makeRun('a', { status: 'RUNNING' }) })

    expect(screen.getByText(/^a:RUNNING/)).toBeInTheDocument()
    expect(listRuns).toHaveBeenCalledTimes(1)
  })

  it('patches the unit of a row from a unit event without a request', async () => {
    const listRuns = vi
      .fn()
      .mockResolvedValue(makePage([makeRun('a', { status: 'RUNNING', units: [makeUnit('u1', { status: 'PENDING' }), makeUnit('u2', { ordinal: 1 })] })]))
    const { bus } = setup(listRuns)
    await screen.findByText(/^a:/)

    await bus.emit({ type: 'unit', payload: makeUnit('u1', { runId: 'a', status: 'RUNNING_INGEST' }) })

    expect(screen.getByTestId('units-a')).toHaveTextContent('u1=RUNNING_INGEST,u2=SUCCEEDED')
    expect(listRuns).toHaveBeenCalledTimes(1)
  })

  it('ignores a unit event of a run that is not on the page, and a late progress event', async () => {
    const progress = (itemsProcessed: number, updatedAt: string) => ({
      step: 'INGEST' as const,
      stage: null,
      itemsProcessed,
      itemsTotal: null,
      percent: null,
      currentItem: null,
      updatedAt,
    })
    const listRuns = vi.fn().mockResolvedValue(
      makePage([
        makeRun('a', {
          status: 'RUNNING',
          units: [makeUnit('u1', { status: 'RUNNING_INGEST', progress: progress(5, '2026-10-01T10:00:10Z') })],
        }),
      ]),
    )
    const { bus } = setup(listRuns)
    await screen.findByText(/^a:/)

    await bus.emit({ type: 'unit', payload: makeUnit('u9', { runId: 'other', status: 'FAILED' }) })
    await bus.emit({
      type: 'unit',
      payload: makeUnit('u1', { runId: 'a', status: 'RUNNING_INGEST', progress: progress(3, '2026-10-01T10:00:05Z') }),
    })

    expect(screen.getByTestId('units-a')).toHaveTextContent('u1=RUNNING_INGEST5')
    expect(listRuns).toHaveBeenCalledTimes(1)
  })

  it('refetches once for a burst of unknown runs on the first page', async () => {
    const listRuns = vi.fn().mockResolvedValue(makePage([makeRun('a')]))
    const { bus } = setup(listRuns)
    await screen.findByText(/^a:/)
    vi.useFakeTimers()

    await bus.emit({ type: 'run', payload: makeRun('x') })
    await bus.emit({ type: 'run', payload: makeRun('y') })
    await bus.emit({ type: 'run', payload: makeRun('z') })
    expect(listRuns).toHaveBeenCalledTimes(1)
    await act(async () => {
      vi.advanceTimersByTime(300)
    })

    expect(listRuns).toHaveBeenCalledTimes(2)
  })

  it('flags new runs on a later page instead of refetching', async () => {
    const listRuns = vi.fn().mockResolvedValue(makePage([makeRun('a')], 2, 50))
    const { bus } = setup(listRuns, { page: 2, size: 20 })
    await screen.findByText(/^a:/)
    vi.useFakeTimers()

    await bus.emit({ type: 'run', payload: makeRun('new') })
    await act(async () => {
      vi.advanceTimersByTime(1000)
    })

    expect(screen.getByTestId('new')).toHaveTextContent('true')
    expect(listRuns).toHaveBeenCalledTimes(1)
  })

  it('updates step badges from step events', async () => {
    const listRuns = vi.fn().mockResolvedValue(makePage([makeRun('a', { status: 'RUNNING' })]))
    const { bus } = setup(listRuns)
    await screen.findByText(/^a:/)

    await bus.emit({ type: 'step', payload: makeStep('a', { kind: 'INGEST', status: 'RUNNING' }) })

    expect(screen.getByText('a:RUNNING:INGEST=RUNNING')).toBeInTheDocument()
    expect(listRuns).toHaveBeenCalledTimes(1)
  })

  it('refetches after a reconnect', async () => {
    const listRuns = vi.fn().mockResolvedValue(makePage([makeRun('a')]))
    const { bus } = setup(listRuns)
    await screen.findByText(/^a:/)

    await bus.emit({ type: 'reconnected' })

    expect(listRuns).toHaveBeenCalledTimes(2)
  })

  it('does not let a response that predates an event overwrite it', async () => {
    const first = deferred<Page<RunSummary>>()
    const listRuns = vi
      .fn()
      .mockReturnValueOnce(first.promise)
      .mockResolvedValue(makePage([makeRun('a', { status: 'RUNNING' })]))
    const { bus } = setup(listRuns)
    expect(screen.getByTestId('loading')).toHaveTextContent('true')

    // The event arrives while the first request is in flight; its (older) answer must be discarded.
    await bus.emit({ type: 'reconnected' })
    await act(async () => {
      first.resolve(makePage([makeRun('a', { status: 'QUEUED' })]))
    })
    await flush()

    expect(await screen.findByText(/^a:RUNNING/)).toBeInTheDocument()
    expect(screen.queryByText(/^a:QUEUED/)).not.toBeInTheDocument()
    expect(listRuns).toHaveBeenCalledTimes(2)
  })

  it('sends no request for a null query and reports load errors', async () => {
    const listRuns = vi.fn().mockRejectedValue(new Error('Cannot reach the server'))
    const { rerender } = setup(listRuns, null)
    expect(listRuns).not.toHaveBeenCalled()

    rerender({ page: 0, size: 20 })
    expect(await screen.findByText('Cannot reach the server')).toBeInTheDocument()
  })
})
