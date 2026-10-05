import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ApiError } from '../api/ApiError'
import type { TriggerRunOutcome } from '../api/runs'
import { makeRun } from '../test/runFixtures'
import { TestApiProvider } from '../test/TestApiProvider'
import { RunNowDialog } from './RunNowDialog'

function setup(triggerRun: ReturnType<typeof vi.fn>) {
  const onClose = vi.fn()
  const onTriggered = vi.fn()
  render(
    <MemoryRouter>
      <TestApiProvider api={{ runs: { triggerRun } as never }}>
        <RunNowDialog seasonSuggestions={['2025-2026']} onClose={onClose} onTriggered={onTriggered} />
      </TestApiProvider>
    </MemoryRouter>,
  )
  return { onClose, onTriggered, user: userEvent.setup() }
}

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('radio', { name: 'RFETM' }))
  await user.type(screen.getByLabelText('Season'), '2025-2026')
  await user.click(screen.getByRole('radio', { name: 'Open match days' }))
}

function rejection(status: number, detail: string, results: unknown[], field?: string) {
  return new ApiError(status, { detail, results: results as never, field }, detail)
}

describe('RunNowDialog', () => {
  it('starts empty and does not submit an invalid form', async () => {
    const triggerRun = vi.fn()
    const { user } = setup(triggerRun)
    expect(screen.getByRole('dialog', { name: 'Run now' })).toBeInTheDocument()
    expect(screen.getByLabelText('Season')).toHaveValue('')

    await user.click(screen.getByRole('button', { name: 'Start run' }))

    expect(triggerRun).not.toHaveBeenCalled()
    expect(screen.getByText('Choose a source')).toBeInTheDocument()
    expect(screen.getByText('Enter the season')).toBeInTheDocument()
    expect(screen.getByText('Choose a scope')).toBeInTheDocument()
  })

  it('disables Group while All sources is selected', async () => {
    const { user } = setup(vi.fn())
    await user.click(screen.getByRole('radio', { name: 'All sources' }))
    expect(screen.getByRole('radio', { name: 'Group' })).toBeDisabled()
  })

  it('sends the request and reports a 201', async () => {
    const outcome: TriggerRunOutcome = {
      status: 201,
      response: { results: [{ source: 'RFETM', outcome: 'CREATED', run: makeRun('r1') }] },
    }
    const triggerRun = vi.fn().mockResolvedValue(outcome)
    const { user, onTriggered } = setup(triggerRun)
    await fillValid(user)
    await user.click(screen.getByRole('checkbox', { name: 'Ignore the ingest no-change check' }))

    await user.click(screen.getByRole('button', { name: 'Start run' }))

    expect(triggerRun).toHaveBeenCalledWith({
      source: 'RFETM',
      season: '2025-2026',
      scopeType: 'OPEN_MATCH_DAYS',
      force: true,
    })
    expect(onTriggered).toHaveBeenCalledWith(outcome)
  })

  it('reports a 202 as queued through the same callback', async () => {
    const outcome: TriggerRunOutcome = {
      status: 202,
      response: { results: [{ source: 'RFETM', outcome: 'QUEUED' }] },
    }
    const { user, onTriggered } = setup(vi.fn().mockResolvedValue(outcome))
    await fillValid(user)
    await user.click(screen.getByRole('button', { name: 'Start run' }))
    expect(onTriggered).toHaveBeenCalledWith(outcome)
  })

  it('shows the 409 message with the active run link and keeps the entered values', async () => {
    const triggerRun = vi.fn().mockRejectedValue(
      rejection(409, 'A run is already active', [
        {
          source: 'RFETM',
          outcome: 'REJECTED',
          code: 'RUN_ACTIVE',
          message: 'Source RFETM already has an active run',
          activeRunId: 'active-1',
        },
      ]),
    )
    const { user, onTriggered } = setup(triggerRun)
    await fillValid(user)

    await user.click(screen.getByRole('button', { name: 'Start run' }))

    expect(await screen.findByText('A run is already active')).toBeInTheDocument()
    expect(screen.getByText(/Source RFETM already has an active run/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open active run' })).toHaveAttribute('href', '/runs/active-1')
    expect(screen.getByLabelText('Season')).toHaveValue('2025-2026')
    expect(screen.getByRole('radio', { name: 'RFETM' })).toBeChecked()
    expect(onTriggered).not.toHaveBeenCalled()
  })

  it('shows the code of a 422', async () => {
    const triggerRun = vi.fn().mockRejectedValue(
      rejection(422, 'The scope is unavailable', [
        { source: 'RFETM', outcome: 'UNAVAILABLE', code: 'NO_OPEN_MATCH_DAYS', message: 'No open match days' },
      ]),
    )
    const { user } = setup(triggerRun)
    await fillValid(user)

    await user.click(screen.getByRole('button', { name: 'Start run' }))

    expect(await screen.findByText(/NO_OPEN_MATCH_DAYS/)).toBeInTheDocument()
  })

  it('marks the season field for a 400 with field season', async () => {
    const triggerRun = vi.fn().mockRejectedValue(rejection(400, 'Season is not valid', [], 'season'))
    const { user } = setup(triggerRun)
    await fillValid(user)

    await user.click(screen.getByRole('button', { name: 'Start run' }))

    expect(await screen.findByText('Season is not valid')).toBeInTheDocument()
    expect(screen.getByLabelText('Season')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows a network error in the dialog without retrying', async () => {
    const triggerRun = vi.fn().mockRejectedValue(new ApiError(0, null, 'Cannot reach the server'))
    const { user } = setup(triggerRun)
    await fillValid(user)

    await user.click(screen.getByRole('button', { name: 'Start run' }))

    expect(await screen.findByText('Cannot reach the server')).toBeInTheDocument()
    expect(triggerRun).toHaveBeenCalledTimes(1)
  })

  it('disables the buttons while submitting', async () => {
    let finish: (outcome: TriggerRunOutcome) => void = () => undefined
    const triggerRun = vi.fn().mockReturnValue(new Promise<TriggerRunOutcome>((done) => (finish = done)))
    const { user } = setup(triggerRun)
    await fillValid(user)

    await user.click(screen.getByRole('button', { name: 'Start run' }))

    expect(screen.getByRole('button', { name: 'Start run' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    finish({ status: 201, response: { results: [] } })
  })

  it('edits group filters and sends them without blank fields', async () => {
    const triggerRun = vi.fn().mockResolvedValue({ status: 201, response: { results: [] } })
    const { user } = setup(triggerRun)
    await user.click(screen.getByRole('radio', { name: 'FCTT' }))
    await user.type(screen.getByLabelText('Season'), '2025-2026')
    await user.click(screen.getByRole('radio', { name: 'Group' }))
    await user.type(screen.getByLabelText('Group 1'), 'A')
    await user.type(screen.getByLabelText('Match days 1'), '3, 4')

    await user.click(screen.getByRole('button', { name: 'Start run' }))

    expect(triggerRun).toHaveBeenCalledWith({
      source: 'FCTT',
      season: '2025-2026',
      scopeType: 'GROUP',
      force: false,
      filters: [{ group: 'A', matchDays: [3, 4] }],
    })
  })
})
