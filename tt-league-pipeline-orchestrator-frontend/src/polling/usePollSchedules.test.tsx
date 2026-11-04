import { act, render, screen } from '@testing-library/react'
import type { PipelineSource, PollSchedule } from '../api/types'
import { createEventBus } from '../test/eventBus'
import { FakeEvents } from '../test/FakeEvents'
import { makeSchedule } from '../test/pollingFixtures'
import { makeRun } from '../test/runFixtures'
import { TestApiProvider } from '../test/TestApiProvider'
import { usePollSchedules } from './usePollSchedules'

type ListFn = (query: { source?: PipelineSource; season?: string }, signal: AbortSignal) => Promise<PollSchedule[]>

function Harness({ source, season }: { source: PipelineSource; season: string | null }) {
  const { schedules, loading, error } = usePollSchedules(source, season)
  return (
    <div>
      <div data-testid="loading">{String(loading)}</div>
      <div data-testid="error">{error ?? ''}</div>
      <div data-testid="ids">{schedules.map((schedule) => schedule.id).join(',')}</div>
    </div>
  )
}

function setup(listPollSchedules: ReturnType<typeof vi.fn<ListFn>>, source: PipelineSource, season: string | null) {
  const bus = createEventBus()
  const view = (s: PipelineSource, y: string | null) => (
    <FakeEvents bus={bus}>
      <TestApiProvider api={{ polling: { listPollSchedules } as never }}>
        <Harness source={s} season={y} />
      </TestApiProvider>
    </FakeEvents>
  )
  const { rerender } = render(view(source, season))
  return { bus, rerender: (s: PipelineSource, y: string | null) => rerender(view(s, y)) }
}

async function flush() {
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
  })
}

const runEvent = (source: PipelineSource) => ({ type: 'run', payload: makeRun('r1', { source }) }) as const

describe('usePollSchedules', () => {
  it('loads the schedules of the source and season', async () => {
    const list = vi.fn<ListFn>(async () => [makeSchedule('s1'), makeSchedule('s2')])
    setup(list, 'FCTT', '2026-2027')
    await flush()

    expect(list).toHaveBeenCalledWith({ source: 'FCTT', season: '2026-2027' }, expect.any(AbortSignal))
    expect(screen.getByTestId('ids')).toHaveTextContent('s1,s2')
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
  })

  it('sends nothing while the season is unknown', async () => {
    const list = vi.fn<ListFn>(async () => [])
    setup(list, 'FCTT', null)
    await flush()

    expect(list).not.toHaveBeenCalled()
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
  })

  it('refetches on a run event of the selected source only', async () => {
    const list = vi.fn<ListFn>(async () => [makeSchedule('s1')])
    const { bus } = setup(list, 'FCTT', '2026-2027')
    await flush()
    expect(list).toHaveBeenCalledTimes(1)

    await bus.emit(runEvent('RFETM'))
    await flush()
    expect(list).toHaveBeenCalledTimes(1)

    await bus.emit({ type: 'step', payload: { runId: 'r1' } as never })
    await flush()
    expect(list).toHaveBeenCalledTimes(1)

    await bus.emit(runEvent('FCTT'))
    await flush()
    expect(list).toHaveBeenCalledTimes(2)
  })

  it('refetches after a reconnect', async () => {
    const list = vi.fn<ListFn>(async () => [])
    const { bus } = setup(list, 'FCTT', '2026-2027')
    await flush()

    await bus.emit({ type: 'reconnected' })
    await flush()

    expect(list).toHaveBeenCalledTimes(2)
  })

  it('shows the failure and does not show the list of another source', async () => {
    const list = vi.fn<ListFn>(async (query) => {
      if (query.source === 'RFETM') {
        throw new Error('boom')
      }
      return [makeSchedule('s1')]
    })
    const { rerender } = setup(list, 'FCTT', '2026-2027')
    await flush()
    expect(screen.getByTestId('ids')).toHaveTextContent('s1')

    rerender('RFETM', '2026-2027')
    expect(screen.getByTestId('ids')).toBeEmptyDOMElement()
    await flush()

    expect(screen.getByTestId('error')).toHaveTextContent('boom')
    expect(screen.getByTestId('ids')).toBeEmptyDOMElement()
  })
})
