import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ConnectionState } from '../events/eventsContext'
import { createEventBus } from '../test/eventBus'
import { FakeEvents } from '../test/FakeEvents'
import { makeRun, makeStep } from '../test/runFixtures'
import { RunActivityLog } from './RunActivityLog'

function setup(runId?: string, state: ConnectionState = 'open') {
  const bus = createEventBus()
  render(
    <MemoryRouter>
      <FakeEvents bus={bus} state={state}>
        <RunActivityLog runId={runId} />
      </FakeEvents>
    </MemoryRouter>,
  )
  return bus
}

function lines(): string[] {
  const log = screen.getByRole('log')
  return Array.from(log.children).map((child) => child.textContent ?? '')
}

describe('RunActivityLog', () => {
  it('logs a run event only when its status changes', async () => {
    const bus = setup()
    await bus.emit({ type: 'run', payload: makeRun('r1', { status: 'QUEUED' }) })
    await bus.emit({ type: 'run', payload: makeRun('r1', { status: 'QUEUED' }) })
    await bus.emit({ type: 'run', payload: makeRun('r1', { status: 'RUNNING' }) })

    expect(lines()).toHaveLength(2)
    expect(lines()[0]).toContain('RFETM Run running')
    expect(lines()[1]).toContain('RFETM Run queued')
  })

  it('includes the error code and message for a failed run', async () => {
    const bus = setup()
    await bus.emit({
      type: 'run',
      payload: makeRun('r1', { status: 'FAILED', error: { code: 'IMPORT_FAILED', message: 'boom' } }),
    })

    expect(lines()[0]).toContain('Run failed — IMPORT_FAILED: boom')
  })

  it('logs step transitions once per kind, attempt and status', async () => {
    const bus = setup()
    await bus.emit({ type: 'run', payload: makeRun('r1', { status: 'RUNNING' }) })
    const running = makeStep('r1', { kind: 'INGEST', status: 'RUNNING', attempt: 2 })
    await bus.emit({ type: 'step', payload: running })
    await bus.emit({ type: 'step', payload: running })
    await bus.emit({ type: 'step', payload: makeStep('r1', { kind: 'FETCH_PACKAGE', outcome: 'PACKAGE_READY' }) })
    await bus.emit({
      type: 'step',
      payload: makeStep('r1', { kind: 'IMPORT', status: 'FAILED', error: { code: 'E1', message: 'bad' } }),
    })

    const text = lines().join('\n')
    expect(text).toContain('Ingest started (attempt 2)')
    expect(text).toContain('Fetch package succeeded — PACKAGE_READY')
    expect(text).toContain('Import failed — E1: bad')
    expect(lines()).toHaveLength(4)
  })

  it('keeps only the entries of the given run', async () => {
    const bus = setup('r1')
    await bus.emit({ type: 'run', payload: makeRun('r2', { status: 'QUEUED' }) })
    await bus.emit({ type: 'step', payload: makeStep('r2') })
    await bus.emit({ type: 'run', payload: makeRun('r1', { status: 'QUEUED' }) })

    expect(lines()).toHaveLength(1)
    expect(lines()[0]).toContain('Run queued')
  })

  it('caps the log at 200 entries, newest first', async () => {
    const bus = setup()
    await bus.emitAll(
      Array.from({ length: 205 }, (_, index) => ({
        type: 'step' as const,
        payload: makeStep('r1', { attempt: index + 1 }),
      })),
    )

    expect(lines()).toHaveLength(200)
    expect(lines()[0]).toContain('(attempt 205)')
  })

  it('describes pending-trigger changes', async () => {
    const bus = setup()
    await bus.emit({ type: 'pending-trigger', payload: { source: 'FCTT', state: 'QUEUED', requestedBy: 'ana' } })
    await bus.emit({
      type: 'pending-trigger',
      payload: { source: 'FCTT', state: 'LAUNCHED', requestedBy: 'ana', runId: 'abcdef1234' },
    })
    await bus.emit({
      type: 'pending-trigger',
      payload: { source: 'FCTT', state: 'DROPPED', requestedBy: 'ana', code: 'NO_OPEN_MATCH_DAYS' },
    })

    const text = lines().join('\n')
    expect(text).toContain('Trigger queued by ana')
    expect(text).toContain('Queued trigger launched run')
    expect(text).toContain('Queued trigger dropped — NO_OPEN_MATCH_DAYS')
    expect(screen.getByRole('link', { name: 'run abcdef12' })).toHaveAttribute('href', '/runs/abcdef1234')
  })

  it('logs a warning when the connection is restored', async () => {
    const bus = setup()
    await bus.emit({ type: 'reconnected' })

    expect(lines()[0]).toContain('Connection restored; events sent while disconnected are not shown')
  })

  it.each([
    ['reconnecting', 'Live updates paused — reconnecting…'],
    ['stopped', 'Live updates stopped.'],
  ] as const)('shows a warning while the connection is %s', (state, message) => {
    setup(undefined, state)
    expect(screen.getByText(message)).toBeInTheDocument()
  })

  it('shows no warning while the connection is open', () => {
    setup()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByText('Showing events received since this page was opened.')).toBeInTheDocument()
  })
})
