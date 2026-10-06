import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ApiError } from '../api/ApiError'
import type { Api } from '../api/bindApi'
import type { RunDetail } from '../api/types'
import { AuthContext } from '../auth/authContext'
import type { AuthContextValue } from '../auth/authContext'
import { createEventBus } from '../test/eventBus'
import { FakeEvents } from '../test/FakeEvents'
import { makeDetail, makeRun, makeStep, makeUnit, makeUnitDetail } from '../test/runFixtures'
import { TestApiProvider } from '../test/TestApiProvider'
import RunDetailPage from './RunDetailPage'

function setup(
  getRun: ReturnType<typeof vi.fn>,
  backTo?: string,
  options: {
    permissions?: string[]
    replayRun?: ReturnType<typeof vi.fn>
    retryUnit?: ReturnType<typeof vi.fn>
    downloadUnitPackage?: ReturnType<typeof vi.fn>
  } = {},
) {
  const bus = createEventBus()
  const auth: AuthContextValue = {
    status: 'signed-in',
    token: 't',
    user: { username: 'u', roles: [], permissions: options.permissions ?? ['matches:write'], expiresAt: Date.now() + 60_000 },
    signIn: async () => undefined,
    signOut: () => undefined,
    getToken: () => 't',
  }
  const api = {
    runs: {
      getRun,
      replayRun: options.replayRun,
      retryUnit: options.retryUnit,
      downloadUnitPackage: options.downloadUnitPackage,
    },
  } as unknown as Partial<Api>
  render(
    <MemoryRouter initialEntries={[{ pathname: '/runs/r1', state: backTo === undefined ? null : { backTo } }]}>
      <AuthContext.Provider value={auth}>
        <FakeEvents bus={bus}>
          <TestApiProvider api={api}>
            <Routes>
              <Route path="/runs/:runId" element={<RunDetailPage />} />
              <Route path="/runs/r2" element={<div>replay page</div>} />
              <Route path="/runs/r9" element={<div>retry page</div>} />
            </Routes>
          </TestApiProvider>
        </FakeEvents>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
  return bus
}

const report = {
  status: 'COMPLETED',
  filesSeen: 11,
  itemsPersisted: 22,
  skipped: 3,
  processorFailures: 0,
  scheduledCreated: 4,
  upgradedToPlayed: 5,
  rescheduled: 6,
  partialActas: 7,
  invalidActas: 8,
  unresolvedPendingFixtures: 9,
  amendedPlayed: 10,
  receivedAt: '2026-10-01T10:02:00Z',
}

describe('RunDetailPage', () => {
  it('renders steps with every attempt, issues, artifacts and the import report', async () => {
    const detail = makeDetail('r1', {
      steps: [
        makeStep('r1', { kind: 'INGEST', attempt: 1 }),
        makeStep('r1', { kind: 'IMPORT', attempt: 1, status: 'FAILED', error: { code: 'E1', message: 'bad zip' } }),
        makeStep('r1', { kind: 'IMPORT', attempt: 2, outcome: 'IMPORTED' }),
      ],
      issues: ['Acta 12 is partial'],
      artifacts: [
        { unitId: 'u1', kind: 'UPLOAD_ZIP', sha256: 'abc123def456', sizeBytes: 2048, createdAt: '2026-10-01T10:00:40Z', purgedAt: null },
      ],
      importReport: report,
    })
    setup(vi.fn().mockResolvedValue(detail))

    const steps = await screen.findByRole('table', { name: 'Steps' })
    expect(within(steps).getAllByRole('row')).toHaveLength(4)
    expect(within(steps).getByText('E1: bad zip')).toBeInTheDocument()
    expect(screen.getByText('Acta 12 is partial')).toBeInTheDocument()
    const artifacts = screen.getByRole('table', { name: 'Artifacts' })
    expect(within(artifacts).getByText('2.0 KiB')).toBeInTheDocument()
    expect(within(artifacts).getByText('abc123def456')).toBeInTheDocument()
    expect(screen.getByText('Files seen')).toBeInTheDocument()
    expect(screen.getByText('22')).toBeInTheDocument()
    expect(screen.getByText('COMPLETED')).toBeInTheDocument()
  })

  it('shows the amended count of the import report and the health of ingest attempts', async () => {
    const detail = makeDetail('r1', {
      steps: [
        makeStep('r1', { kind: 'INGEST', attempt: 1, health: { httpErrors: 3, timeouts: 2, parseErrors: 1 } }),
        makeStep('r1', { kind: 'INGEST', attempt: 2, health: null }),
        makeStep('r1', { kind: 'IMPORT', attempt: 1 }),
      ],
      importReport: report,
    })
    setup(vi.fn().mockResolvedValue(detail))

    const steps = await screen.findByRole('table', { name: 'Steps' })
    expect(within(steps).getByText('HTTP 3 · timeouts 2 · parse 1')).toBeInTheDocument()
    // an attempt without health data and a non-ingest step show a dash
    const rows = within(steps).getAllByRole('row').slice(1)
    expect(within(rows[1]).getAllByRole('cell')[8]).toHaveTextContent('—')
    expect(within(rows[2]).getAllByRole('cell')[8]).toHaveTextContent('—')
    expect(screen.getByText('Amended played').nextSibling).toHaveTextContent('10')
  })

  it('explains a missing import report for active and terminal runs', async () => {
    setup(vi.fn().mockResolvedValue(makeDetail('r1', { status: 'RUNNING', finishedAt: null })))
    expect(await screen.findByText('No import report yet.')).toBeInTheDocument()
  })

  it('says there is no import report once the run is terminal', async () => {
    setup(vi.fn().mockResolvedValue(makeDetail('r1', { status: 'NO_CHANGES' })))
    expect(await screen.findByText('No import report.')).toBeInTheDocument()
    expect(screen.getByText('No issues')).toBeInTheDocument()
    expect(screen.getByText('No artifacts')).toBeInTheDocument()
  })

  it('shows run not found for a 404', async () => {
    setup(vi.fn().mockRejectedValue(new ApiError(404, null, 'Not found')))
    expect(await screen.findByRole('heading', { name: 'Run not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'All runs' })).toHaveAttribute('href', '/runs')
  })

  it('shows an error with a retry for other failures', async () => {
    const getRun = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(500, null, 'Server exploded'))
      .mockResolvedValue(makeDetail('r1'))
    setup(getRun)
    expect(await screen.findByText('Server exploded')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByText('No steps yet.')).toBeInTheDocument()
    expect(getRun).toHaveBeenCalledTimes(2)
  })

  it('adds a step row and changes the status from live events', async () => {
    const bus = setup(vi.fn().mockResolvedValue(makeDetail('r1', { status: 'RUNNING', finishedAt: null })))
    await screen.findByText('No steps yet.')

    await bus.emit({ type: 'step', payload: makeStep('r1', { kind: 'INGEST', status: 'RUNNING' }) })
    expect(within(screen.getByRole('table', { name: 'Steps' })).getAllByRole('row')).toHaveLength(2)

    await bus.emit({ type: 'run', payload: makeRun('r1', { status: 'RUNNING', finishedAt: null }) })
    expect(screen.getByText('Running', { selector: '.MuiChip-label' })).toBeInTheDocument()
  })

  it('refetches on a terminal run event and shows the import report', async () => {
    const finished: RunDetail = makeDetail('r1', { status: 'SUCCEEDED', importReport: report })
    const getRun = vi
      .fn()
      .mockResolvedValueOnce(makeDetail('r1', { status: 'RUNNING', finishedAt: null }))
      .mockResolvedValue(finished)
    const bus = setup(getRun)
    await screen.findByText('No import report yet.')

    await bus.emit({ type: 'run', payload: makeRun('r1', { status: 'SUCCEEDED' }) })

    expect(await screen.findByText('Files seen')).toBeInTheDocument()
    expect(getRun).toHaveBeenCalledTimes(2)
  })

  it('refetches after a reconnect', async () => {
    const getRun = vi.fn().mockResolvedValue(makeDetail('r1'))
    const bus = setup(getRun)
    await screen.findByText('No steps yet.')

    await bus.emit({ type: 'reconnected' })

    expect(getRun).toHaveBeenCalledTimes(2)
  })

  it('keeps the list filters in the back link', async () => {
    setup(vi.fn().mockResolvedValue(makeDetail('r1')), '?source=FCTT&page=2')
    expect(await screen.findByRole('link', { name: 'All runs' })).toHaveAttribute('href', '/runs?source=FCTT&page=2')
  })

  describe('replay', () => {
    it('enables the button when the server allows it and shows the dialog text', async () => {
      setup(vi.fn().mockResolvedValue(makeDetail('r1')))

      const button = await screen.findByRole('button', { name: 'Replay import' })
      expect(button).toBeEnabled()
      await userEvent.click(button)

      const dialog = screen.getByRole('dialog', { name: 'Replay import' })
      expect(within(dialog).getByText(/Ingest is skipped/)).toBeInTheDocument()
      expect(within(dialog).getByText(/returns the existing import job/)).toBeInTheDocument()
    })

    it.each([
      ['RUN_ACTIVE', 'The run has not finished yet'],
      ['NO_PACKAGE', 'This run has no stored package to replay'],
      ['ARTIFACT_PURGED', 'The stored package was purged by the retention policy'],
    ])('disables the button with a tooltip for %s', async (code, label) => {
      setup(vi.fn().mockResolvedValue(makeDetail('r1', { replay: { allowed: false, code } })))

      const button = await screen.findByRole('button', { name: 'Replay import' })
      expect(button).toBeDisabled()
      await userEvent.hover(button.parentElement as HTMLElement)
      expect(await screen.findByText(label)).toBeInTheDocument()
    })

    it('hides the button without the matches:write permission', async () => {
      setup(vi.fn().mockResolvedValue(makeDetail('r1')), undefined, { permissions: [] })

      await screen.findByText('No steps yet.')
      expect(screen.queryByRole('button', { name: 'Replay import' })).not.toBeInTheDocument()
    })

    it('navigates to the new run on a 201', async () => {
      const replayRun = vi.fn().mockResolvedValue(makeRun('r2', { trigger: 'RETRY', retryOfRunId: 'r1' }))
      setup(vi.fn().mockResolvedValue(makeDetail('r1')), undefined, { replayRun })

      await userEvent.click(await screen.findByRole('button', { name: 'Replay import' }))
      await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Replay import' }))

      expect(replayRun).toHaveBeenCalledWith('r1')
      expect(await screen.findByText('replay page')).toBeInTheDocument()
    })

    it.each([
      [409, 'Source RFETM already has an active run'],
      [422, 'The stored package of run r1 has been purged'],
    ])('shows the server message in the dialog on a %i', async (status, message) => {
      const replayRun = vi.fn().mockRejectedValue(new ApiError(status, { detail: message }, message))
      setup(vi.fn().mockResolvedValue(makeDetail('r1')), undefined, { replayRun })

      await userEvent.click(await screen.findByRole('button', { name: 'Replay import' }))
      await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Replay import' }))

      expect(await within(screen.getByRole('dialog')).findByText(message)).toBeInTheDocument()
      expect(screen.queryByText('replay page')).not.toBeInTheDocument()
    })

    it('says "Replay of" in the header of a replay run', async () => {
      setup(vi.fn().mockResolvedValue(makeDetail('r1', { trigger: 'RETRY', retryOfRunId: 'r0' })))

      expect(await screen.findByRole('link', { name: 'r0' })).toHaveAttribute('href', '/runs/r0')
      expect(screen.getByText(/Replay of/)).toBeInTheDocument()
    })

    it('shows the reused chip in the header and the import report section', async () => {
      setup(vi.fn().mockResolvedValue(makeDetail('r1', { importJobReused: true, importReport: report })))

      expect(await screen.findAllByText('Existing import job reused \u2014 nothing was re-imported')).toHaveLength(2)
    })

    it('does not show the reused chip for a fresh job', async () => {
      setup(vi.fn().mockResolvedValue(makeDetail('r1', { importJobReused: false })))

      await screen.findByText('No steps yet.')
      expect(screen.queryByText(/Existing import job reused/)).not.toBeInTheDocument()
    })

    it('marks a purged artifact row', async () => {
      const detail = makeDetail('r1', {
        artifacts: [
          { unitId: 'u1', kind: 'ZIP', sha256: 'aaa', sizeBytes: 10, createdAt: '2026-10-01T10:00:40Z', purgedAt: '2026-11-01T04:30:00Z' },
          { unitId: 'u1', kind: 'JSON', sha256: 'bbb', sizeBytes: 10, createdAt: '2026-10-01T10:00:40Z', purgedAt: null },
        ],
      })
      setup(vi.fn().mockResolvedValue(detail))

      const artifacts = await screen.findByRole('table', { name: 'Artifacts' })
      const rows = within(artifacts).getAllByRole('row').slice(1)
      expect(within(rows[0]).getByText(/^Purged /)).toBeInTheDocument()
      expect(within(rows[1]).queryByText(/^Purged /)).not.toBeInTheDocument()
    })
  })

  describe('units', () => {
    const failedUnit = makeUnitDetail('u2', {
      ordinal: 1,
      unitKey: 'b'.repeat(64),
      label: 'Tercera Group 2',
      status: 'FAILED',
      error: { code: 'IMPORT_FAILED', message: 'bad zip' },
      ingestRunId: 'ing-2',
      steps: [makeStep('r1', { unitId: 'u2', kind: 'IMPORT', status: 'FAILED', error: { code: 'E1', message: 'bad zip' } })],
      artifacts: [
        { unitId: 'u2', kind: 'ZIP', sha256: 'abc123', sizeBytes: 2048, createdAt: '2026-10-01T10:00:40Z', purgedAt: null },
      ],
      storageFolder: 'rfetm/r1/u2',
      packageUrl: '/api/pipeline/runs/r1/units/u2/package',
      retry: { eligible: true, reason: null },
      counters: report,
    })
    const okUnit = makeUnitDetail('u1', { label: 'Tercera Group 1', unitKey: 'a'.repeat(64) })
    const detail = (overrides: Partial<RunDetail> = {}) =>
      makeDetail('r1', { status: 'PARTIAL', units: [okUnit, failedUnit], ...overrides })

    it('says there are no units yet when the run has none', async () => {
      setup(vi.fn().mockResolvedValue(makeDetail('r1')))

      expect(await screen.findByText('No units yet.')).toBeInTheDocument()
    })

    it('lists one accordion per unit and opens the failed one by default', async () => {
      setup(vi.fn().mockResolvedValue(detail()))

      const failed = await screen.findByRole('button', { name: 'Unit Tercera Group 2' })
      const ok = screen.getByRole('button', { name: 'Unit Tercera Group 1' })
      expect(failed).toHaveAttribute('aria-expanded', 'true')
      expect(ok).toHaveAttribute('aria-expanded', 'false')
      expect(screen.getAllByText('IMPORT_FAILED: bad zip').length).toBeGreaterThan(0)
      expect(screen.getByRole('table', { name: 'Steps of Tercera Group 2' })).toBeInTheDocument()
      expect(screen.getByRole('table', { name: 'Artifacts of Tercera Group 2' })).toHaveTextContent('2.0 KiB')
      expect(screen.getByText('rfetm/r1/u2')).toBeInTheDocument()
      expect(screen.getByText('ing-2')).toBeInTheDocument()
    })

    it('opens the only unit of a run even when it succeeded, and toggles on click', async () => {
      const user = userEvent.setup()
      setup(vi.fn().mockResolvedValue(makeDetail('r1', { units: [okUnit] })))

      const header = await screen.findByRole('button', { name: 'Unit Tercera Group 1' })
      expect(header).toHaveAttribute('aria-expanded', 'true')

      await user.click(header)

      expect(header).toHaveAttribute('aria-expanded', 'false')
    })

    it('shows the progress of a running unit', async () => {
      const running = makeUnitDetail('u1', {
        status: 'RUNNING_INGEST',
        finishedAt: null,
        durationMs: null,
        progress: {
          step: 'INGEST',
          stage: 'DOWNLOAD',
          itemsProcessed: 3,
          itemsTotal: 10,
          percent: 30,
          currentItem: 'group-1',
          updatedAt: '2026-10-01T10:00:10Z',
        },
      })
      setup(vi.fn().mockResolvedValue(makeDetail('r1', { status: 'RUNNING', finishedAt: null, units: [running] })))

      expect(await screen.findByRole('progressbar', { name: 'Ingest progress' })).toHaveAttribute('aria-valuenow', '30')
      expect(screen.getByText('Ingest: 30% (DOWNLOAD · 3/10 · group-1)')).toBeInTheDocument()
    })

    it('enables Retry unit for an eligible unit and confirms before retrying', async () => {
      const user = userEvent.setup()
      const retryUnit = vi.fn().mockResolvedValue({ runId: 'r9' })
      setup(vi.fn().mockResolvedValue(detail()), undefined, { retryUnit })

      const button = await screen.findByRole('button', { name: 'Retry unit' })
      expect(button).toBeEnabled()
      await user.click(button)
      const dialog = screen.getByRole('dialog', { name: 'Retry unit' })
      expect(dialog).toHaveTextContent('Tercera Group 2')
      expect(retryUnit).not.toHaveBeenCalled()
      await user.click(within(dialog).getByRole('button', { name: 'Retry unit' }))

      expect(retryUnit).toHaveBeenCalledWith('r1', 'u2')
      expect(await screen.findByText('retry page')).toBeInTheDocument()
    })

    it.each([
      ['RUN_ACTIVE', 'The run, or another run of this source, has not finished yet'],
      ['UNIT_NOT_RETRYABLE', 'Only a failed or skipped unit can be retried'],
    ])('disables Retry unit with a tooltip for %s', async (reason, label) => {
      const user = userEvent.setup()
      const blocked = { ...failedUnit, retry: { eligible: false, reason } }
      setup(vi.fn().mockResolvedValue(detail({ units: [okUnit, blocked] })))

      const button = await screen.findByRole('button', { name: 'Retry unit' })
      expect(button).toBeDisabled()
      await user.hover(button.parentElement as HTMLElement)
      expect(await screen.findByText(label)).toBeInTheDocument()
    })

    it('hides Retry unit without the matches:write permission', async () => {
      setup(vi.fn().mockResolvedValue(detail()), undefined, { permissions: [] })

      await screen.findByRole('button', { name: 'Unit Tercera Group 2' })
      expect(screen.queryByRole('button', { name: 'Retry unit' })).not.toBeInTheDocument()
    })

    it('links the runs that retried the unit', async () => {
      const retried = { ...failedUnit, retriedBy: [{ runId: 'r5', status: 'SUCCEEDED' as const }] }
      setup(vi.fn().mockResolvedValue(detail({ units: [okUnit, retried] })))

      const link = await screen.findByRole('link', { name: 'r5' })
      expect(link).toHaveAttribute('href', '/runs/r5')
      expect(screen.getByText(/Retried by/)).toHaveTextContent('Retried by r5 (SUCCEEDED)')
    })

    it('says "Retry of a unit of" in the header of a unit retry run', async () => {
      setup(vi.fn().mockResolvedValue(makeDetail('r1', { trigger: 'UNIT_RETRY', retryOfRunId: 'r0', retryOfUnitId: 'u7' })))

      expect(await screen.findByRole('link', { name: 'r0' })).toHaveAttribute('href', '/runs/r0')
      expect(screen.getByText(/Retry of a unit of/)).toBeInTheDocument()
      expect(screen.queryByText(/Replay of/)).not.toBeInTheDocument()
    })

    describe('package download', () => {
      const original = { createObjectURL: URL.createObjectURL, revokeObjectURL: URL.revokeObjectURL }

      afterEach(() => {
        URL.createObjectURL = original.createObjectURL
        URL.revokeObjectURL = original.revokeObjectURL
      })

      it('downloads the stored package through the authenticated client', async () => {
        const user = userEvent.setup()
        URL.createObjectURL = vi.fn(() => 'blob:zip')
        URL.revokeObjectURL = vi.fn()
        const clicked: string[] = []
        const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
          clicked.push(`${this.download}|${this.href}`)
        })
        const downloadUnitPackage = vi.fn().mockResolvedValue({ blob: new Blob(['zip']), filename: 'rfetm-u2.zip' })
        setup(vi.fn().mockResolvedValue(detail()), undefined, { downloadUnitPackage })

        await user.click(await screen.findByRole('button', { name: 'Download package' }))

        expect(downloadUnitPackage).toHaveBeenCalledWith('r1', 'u2')
        await vi.waitFor(() => expect(clicked).toEqual(['rfetm-u2.zip|blob:zip']))
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:zip')
        click.mockRestore()
      })

      it('shows the failure of a download', async () => {
        const user = userEvent.setup()
        const downloadUnitPackage = vi
          .fn()
          .mockRejectedValue(new ApiError(410, { detail: 'The package was purged' }, 'The package was purged'))
        setup(vi.fn().mockResolvedValue(detail()), undefined, { downloadUnitPackage })

        await user.click(await screen.findByRole('button', { name: 'Download package' }))

        expect(await screen.findByText('The package was purged')).toBeInTheDocument()
      })

      it('offers no download for a unit without an unpurged package', async () => {
        setup(vi.fn().mockResolvedValue(detail({ units: [{ ...failedUnit, packageUrl: null }] })))

        await screen.findByRole('button', { name: 'Unit Tercera Group 2' })
        expect(screen.queryByRole('button', { name: 'Download package' })).not.toBeInTheDocument()
      })
    })

    it('shows the run payload in the JSON tab and copies it', async () => {
      const user = userEvent.setup()
      const writeText = vi.fn().mockResolvedValue(undefined)
      Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
      setup(vi.fn().mockResolvedValue(detail()))
      await screen.findByRole('button', { name: 'Unit Tercera Group 2' })

      await user.click(screen.getByRole('tab', { name: 'JSON' }))

      const json = screen.getByLabelText('Run JSON')
      expect(JSON.parse(json.textContent ?? '')).toMatchObject({ id: 'r1', status: 'PARTIAL', units: [{ id: 'u1' }, { id: 'u2' }] })
      expect(screen.queryByRole('button', { name: 'Unit Tercera Group 2' })).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Copy JSON' }))
      expect(writeText).toHaveBeenCalledWith(json.textContent)
      await user.click(screen.getByRole('tab', { name: 'Overview' }))
      expect(screen.getByRole('button', { name: 'Unit Tercera Group 2' })).toBeInTheDocument()
    })

    describe('live events', () => {
      const pending = () =>
        makeUnitDetail('u1', { status: 'PENDING', startedAt: null, finishedAt: null, durationMs: null })

      it('updates a unit from a unit event without a request', async () => {
        const getRun = vi.fn().mockResolvedValue(makeDetail('r1', { status: 'RUNNING', finishedAt: null, units: [pending()] }))
        const bus = setup(getRun)
        await screen.findAllByText('Pending', { selector: '.MuiChip-label' })

        await bus.emit({
          type: 'unit',
          payload: makeUnit('u1', { runId: 'r1', status: 'RUNNING_INGEST', finishedAt: null, durationMs: null }),
        })

        expect(screen.getByText('Running ingest', { selector: '.MuiChip-label' })).toBeInTheDocument()
        expect(getRun).toHaveBeenCalledTimes(1)
      })

      it('ignores the unit events of other runs', async () => {
        const getRun = vi.fn().mockResolvedValue(makeDetail('r1', { status: 'RUNNING', finishedAt: null, units: [pending()] }))
        const bus = setup(getRun)
        await screen.findAllByText('Pending', { selector: '.MuiChip-label' })

        await bus.emit({ type: 'unit', payload: makeUnit('u1', { runId: 'other', status: 'FAILED' }) })

        expect(screen.queryByText('Failed', { selector: '.MuiChip-label' })).not.toBeInTheDocument()
        expect(getRun).toHaveBeenCalledTimes(1)
      })

      it('refetches when a unit finishes, to get its counters, artifacts and retry decision', async () => {
        const running = makeUnitDetail('u1', { status: 'IMPORTING', finishedAt: null, durationMs: null, importJobId: 'job-1' })
        const finished = makeUnitDetail('u1', {
          status: 'FAILED',
          error: { code: 'E', message: 'boom' },
          retry: { eligible: true, reason: null },
        })
        const getRun = vi
          .fn()
          .mockResolvedValueOnce(makeDetail('r1', { status: 'RUNNING', finishedAt: null, units: [running] }))
          .mockResolvedValue(makeDetail('r1', { status: 'FAILED', units: [finished] }))
        const bus = setup(getRun, undefined, { retryUnit: vi.fn() })
        await screen.findAllByText('Importing', { selector: '.MuiChip-label' })

        await bus.emit({
          type: 'unit',
          payload: makeUnit('u1', { runId: 'r1', status: 'FAILED', error: { code: 'E', message: 'boom' } }),
        })

        expect(await screen.findByRole('button', { name: 'Retry unit' })).toBeEnabled()
        expect(getRun).toHaveBeenCalledTimes(2)
      })

      it('puts a step event into the steps of its unit and into the run steps', async () => {
        const unit = makeUnitDetail('u1', { label: 'Group 1', status: 'RUNNING_INGEST', finishedAt: null })
        const bus = setup(vi.fn().mockResolvedValue(makeDetail('r1', { status: 'RUNNING', finishedAt: null, units: [unit] })))
        await screen.findByRole('button', { name: 'Unit Group 1' })
        expect(screen.getByText('No steps.')).toBeInTheDocument()

        await bus.emit({ type: 'step', payload: makeStep('r1', { unitId: 'u1', kind: 'INGEST', status: 'RUNNING' }) })

        expect(within(screen.getByRole('table', { name: 'Steps of Group 1' })).getAllByRole('row')).toHaveLength(2)
        expect(within(screen.getByRole('table', { name: 'Steps' })).getAllByRole('row')).toHaveLength(2)
      })

      it('takes the units of a run event into the detail units without losing their steps', async () => {
        const unit = makeUnitDetail('u1', {
          label: 'Group 1',
          status: 'RUNNING_INGEST',
          finishedAt: null,
          steps: [makeStep('r1', { unitId: 'u1', kind: 'INGEST', status: 'RUNNING' })],
        })
        const bus = setup(vi.fn().mockResolvedValue(makeDetail('r1', { status: 'RUNNING', finishedAt: null, units: [unit] })))
        await screen.findByRole('table', { name: 'Steps of Group 1' })

        await bus.emit({
          type: 'run',
          payload: makeRun('r1', {
            status: 'RUNNING',
            finishedAt: null,
            units: [makeUnit('u1', { label: 'Group 1', status: 'IMPORTING', finishedAt: null })],
          }),
        })

        expect(screen.getByText('Importing', { selector: '.MuiChip-label' })).toBeInTheDocument()
        expect(screen.getByRole('table', { name: 'Steps of Group 1' })).toBeInTheDocument()
      })
    })
  })
})
