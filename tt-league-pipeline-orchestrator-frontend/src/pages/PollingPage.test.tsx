import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { clearToken } from '../auth/tokenStorage'
import { json } from '../test/fakeFetch'
import { makePolicies, makePolicy, makeSchedule, makeStatus, makeStoppedSchedule } from '../test/pollingFixtures'
import type { PollingOverrides } from '../test/pollingFixtures'
import { renderApp, storeSession, stubBackends } from '../test/renderApp'

// The page is a lazy chunk; loading it once up front keeps the first test inside the wait.
beforeAll(async () => {
  await import('./PollingPage')
}, 60_000)

afterEach(() => {
  vi.unstubAllGlobals()
  clearToken()
  window.sessionStorage.clear()
})

const ADMIN = { roles: ['ADMIN'], permissions: [] }
const OPERATOR = { roles: [], permissions: ['matches:write'] }
const READER = { roles: [], permissions: [] }

async function openPolling(
  claims: { roles: string[]; permissions: string[] } = READER,
  polling: PollingOverrides = {},
  path = '/polling',
) {
  const fake = stubBackends({ polling })
  storeSession(claims)
  renderApp(path)
  await screen.findByRole('heading', { name: 'Polling' })
  await screen.findByRole('table', { name: 'Polling policy by source' })
  return fake
}

const schedulesCalls = (fake: ReturnType<typeof stubBackends>) =>
  fake.callsTo('/api/pipeline/polling/schedules').filter((call) => call.method === 'GET')

describe('PollingPage', () => {
  it('shows the mode of every source, the policy per source and the schedules of the adaptive source', async () => {
    const stopped = makeStoppedSchedule('s2', { filter: { category: 'segona', group: null, phase: null, territory: null, gender: 'female', matchDays: [2] } })
    const fake = await openPolling(READER, {
      policies: makePolicies({ FCTT: { overridden: true, version: 2, updatedBy: 'bob', updatedAt: '2026-10-01T10:00:00Z', recentMatchDays: 5 } }),
      schedules: [makeSchedule('s1', { pendingRunId: 'run-9' }), stopped],
    })

    const modes = within(screen.getByRole('table', { name: 'Polling mode by source' }))
    expect(modes.getByRole('row', { name: /RFETM/ })).toHaveTextContent('Cron 0 0 3 * * *')
    expect(modes.getByRole('row', { name: /BCNESA/ })).toHaveTextContent('Not scheduled')
    expect(modes.getByRole('row', { name: /FCTT/ })).toHaveTextContent('Adaptive')
    expect(screen.getByText(/Schedule season 2026-2027, time zone Europe\/Madrid/)).toBeInTheDocument()

    const policies = screen.getByRole('table', { name: 'Polling policy by source' })
    expect(within(policies).getAllByRole('row')).toHaveLength(1 + 10 + 1)
    expect(within(policies).getByRole('row', { name: /Full refresh interval/ })).toHaveTextContent('7 d7 d7 d')
    expect(within(policies).getByRole('row', { name: /Match days per group/ })).toHaveTextContent(
      'Last 3 match days per groupLast 3 match days per groupLast 5 match days per group',
    )
    expect(within(policies).getByText('Overridden')).toBeInTheDocument()
    expect(within(policies).getAllByText('Defaults')).toHaveLength(2)
    expect(within(policies).getByText(/bob/)).toBeInTheDocument()

    expect(fake.callsTo('/api/pipeline/polling/schedules')[0].url).toBe(
      '/api/pipeline/polling/schedules?source=FCTT&season=2026-2027',
    )
    const table = await screen.findByRole('table', { name: 'Poll schedules' })
    expect(within(table).getAllByRole('row')).toHaveLength(3)
    expect(within(table).getByText('tercera · G1 · 1a Fase · male · md 3, 4, 5')).toBeInTheDocument()
    expect(within(table).getByRole('link', { name: 'Open run' })).toHaveAttribute('href', '/runs/run-9')
    expect(within(table).getByText(/OVERDUE_LIMIT/)).toBeInTheDocument()
    expect(
      screen.getByText(
        'Only the last 5 match days of each group are polled; older ones are covered by the full refresh (every 7 d).',
      ),
    ).toBeInTheDocument()
  })

  it('shows the server lookback in the caption of the selected source', async () => {
    await openPolling(READER, {}, '/polling?source=RFETM')

    expect(
      await screen.findByText(
        'Only the last 3 match days of each group are polled; older ones are covered by the full refresh (every 7 d).',
      ),
    ).toBeInTheDocument()
    expect(screen.getByText('RFETM is not polled adaptively, so it gets no new schedules.')).toBeInTheDocument()
  })

  it('says that a source that is not adaptively polled keeps its unused policy', async () => {
    await openPolling()

    expect(screen.getAllByText('Not used: source is not adaptively polled')).toHaveLength(2)
  })

  it('explains that no schedules exist before the first adaptive tick', async () => {
    await openPolling()

    expect(await screen.findByText(/appear after the first adaptive tick/)).toBeInTheDocument()
  })

  it('tells how to turn adaptive polling on when no source is polled', async () => {
    await openPolling(READER, {
      status: makeStatus({
        season: null,
        zone: null,
        sources: [
          { source: 'RFETM', mode: 'NONE', cron: null },
          { source: 'BCNESA', mode: 'NONE', cron: null },
          { source: 'FCTT', mode: 'NONE', cron: null },
        ],
      }),
    })

    expect(screen.getByText(/PIPELINE_POLLING_SOURCES/)).toBeInTheDocument()
    expect(screen.getByText('Choose a season to see its schedules.')).toBeInTheDocument()
  })

  it('lets only an ADMIN edit or reset a policy and explains the requirement', async () => {
    const user = userEvent.setup()
    await openPolling(OPERATOR, { policies: makePolicies({ FCTT: { overridden: true, version: 1, updatedBy: 'bob', updatedAt: '2026-10-01T10:00:00Z' } }) })

    const edit = screen.getByRole('button', { name: 'Edit policy of FCTT' })
    expect(edit).toBeDisabled()
    await user.hover(edit.parentElement as HTMLElement)
    expect(await screen.findByText('Requires the ADMIN role')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reset policy of FCTT to defaults' })).toBeDisabled()
  })

  it('enables edit for an ADMIN and reset only for a source with an override', async () => {
    await openPolling(ADMIN, { policies: makePolicies({ FCTT: { overridden: true, version: 1, updatedBy: 'bob', updatedAt: '2026-10-01T10:00:00Z' } }) })

    expect(screen.getByRole('button', { name: 'Edit policy of RFETM' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Reset policy of FCTT to defaults' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Reset policy of RFETM to defaults' })).toBeDisabled()
  })

  it('saves an edited policy with the loaded version and the new lookback, then reloads', async () => {
    const user = userEvent.setup()
    let saved: unknown = null
    const fake = await openPolling(ADMIN, {
      policies: makePolicies({ FCTT: { overridden: true, version: 4, updatedBy: 'bob', updatedAt: '2026-10-01T10:00:00Z' } }),
      onRequest: (request) => {
        if (request.method === 'PUT') {
          saved = JSON.parse(request.body ?? '{}')
          return json(makePolicy('FCTT', { overridden: true, version: 5, recentMatchDays: 2 }))
        }
        return undefined
      },
    })

    await user.click(screen.getByRole('button', { name: 'Edit policy of FCTT' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit polling policy of FCTT' })
    const lookback = within(dialog).getByLabelText('Match days per group (lookback)')
    await user.clear(lookback)
    await user.type(lookback, '2')
    await user.click(within(dialog).getByRole('button', { name: 'Save policy' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(saved).toEqual({
      matchDay: 'PT2H',
      matchDayStartOffset: 'PT2H',
      dayAfter: 'PT3H',
      daysTwoToSeven: 'PT12H',
      open: 'PT24H',
      overdue: 'PT24H',
      overdueStopAfterDays: 21,
      fullRefresh: 'PT168H',
      noChangeThreshold: 3,
      recentMatchDays: 2,
      version: 4,
    })
    expect(fake.callsTo('/api/pipeline/polling/policies').filter((call) => call.method === 'GET').length).toBeGreaterThan(1)
  })

  it('checks the form before sending and keeps the dialog open', async () => {
    const user = userEvent.setup()
    const fake = await openPolling(ADMIN)

    await user.click(screen.getByRole('button', { name: 'Edit policy of FCTT' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit polling policy of FCTT' })
    const lookback = within(dialog).getByLabelText('Match days per group (lookback)')
    await user.clear(lookback)
    await user.type(lookback, '0')
    await user.click(within(dialog).getByRole('button', { name: 'Save policy' }))

    expect(await within(dialog).findByText('Must be a whole number of at least 1')).toBeInTheDocument()
    expect(fake.calls().some((call) => call.method === 'PUT')).toBe(false)
  })

  it('shows the server reason of a 400 and keeps the input', async () => {
    const user = userEvent.setup()
    await openPolling(ADMIN, {
      onRequest: (request) =>
        request.method === 'PUT'
          ? json({ title: 'Bad Request', status: 400, detail: 'overdue must not exceed fullRefresh' }, 400)
          : undefined,
    })

    await user.click(screen.getByRole('button', { name: 'Edit policy of FCTT' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit polling policy of FCTT' })
    await user.click(within(dialog).getByRole('button', { name: 'Save policy' }))

    expect(await within(dialog).findByText('overdue must not exceed fullRefresh')).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Reload current values' })).not.toBeInTheDocument()
  })

  it('keeps the input on a stale policy and offers the current values', async () => {
    const user = userEvent.setup()
    await openPolling(ADMIN, {
      onRequest: (request) => {
        if (request.method === 'PUT') {
          return json({ title: 'Conflict', status: 409, code: 'STALE_POLICY', detail: 'The policy changed' }, 409)
        }
        if (request.method === 'GET' && request.url.endsWith('/policies/FCTT')) {
          return json(makePolicy('FCTT', { overridden: true, version: 7, recentMatchDays: 6 }))
        }
        return undefined
      },
    })

    await user.click(screen.getByRole('button', { name: 'Edit policy of FCTT' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit polling policy of FCTT' })
    const lookback = within(dialog).getByLabelText('Match days per group (lookback)')
    await user.clear(lookback)
    await user.type(lookback, '2')
    await user.click(within(dialog).getByRole('button', { name: 'Save policy' }))

    expect(await within(dialog).findByText('The policy changed')).toBeInTheDocument()
    expect(lookback).toHaveValue('2')
    await user.click(within(dialog).getByRole('button', { name: 'Reload current values' }))
    await waitFor(() => expect(lookback).toHaveValue('6'))
    expect(within(dialog).queryByText('The policy changed')).not.toBeInTheDocument()
  })

  it('resets a policy after confirmation', async () => {
    const user = userEvent.setup()
    let deleted = false
    await openPolling(ADMIN, {
      policies: makePolicies({ FCTT: { overridden: true, version: 1, updatedBy: 'bob', updatedAt: '2026-10-01T10:00:00Z' } }),
      onRequest: (request) => {
        if (request.method === 'DELETE') {
          deleted = true
          return json(makePolicy('FCTT'))
        }
        return undefined
      },
    })

    await user.click(screen.getByRole('button', { name: 'Reset policy of FCTT to defaults' }))
    const dialog = await screen.findByRole('dialog', { name: 'Reset polling policy of FCTT' })
    await user.click(within(dialog).getByRole('button', { name: 'Reset to defaults' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(deleted).toBe(true)
  })

  it('shows Resume only on stopped rows and gates it by matches:write with the requirement tooltip', async () => {
    const user = userEvent.setup()
    await openPolling(READER, { schedules: [makeSchedule('s1'), makeStoppedSchedule('s2')] })

    const table = await screen.findByRole('table', { name: 'Poll schedules' })
    const resume = within(table).getAllByRole('button', { name: /Resume/ })
    expect(resume).toHaveLength(1)
    expect(resume[0]).toBeDisabled()
    await user.hover(resume[0].parentElement as HTMLElement)
    expect(await screen.findByText('Requires the matches:write permission')).toBeInTheDocument()
    expect(table.querySelectorAll('tr[data-stopped="true"]')).toHaveLength(1)
  })

  it('resumes a stopped unit and refetches the schedules', async () => {
    const user = userEvent.setup()
    let resumed = false
    const fake = await openPolling(OPERATOR, {
      schedules: [makeStoppedSchedule('s2')],
      onRequest: (request) => {
        if (request.method === 'POST' && request.url.endsWith('/schedules/s2/resume')) {
          resumed = true
          return json(makeSchedule('s2'))
        }
        return undefined
      },
    })

    await user.click(await screen.findByRole('button', { name: /Resume/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Resume stopped unit' })
    const before = schedulesCalls(fake).length
    await user.click(within(dialog).getByRole('button', { name: 'Resume' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(resumed).toBe(true)
    await waitFor(() => expect(schedulesCalls(fake).length).toBeGreaterThan(before))
  })

  it.each([
    [404, 'POLL_SCHEDULE_NOT_FOUND', 'Poll schedule s2 not found'],
    [409, 'NOT_STOPPED', 'Poll schedule s2 is not stopped'],
    [409, 'STALE_SCHEDULE', 'The poll schedule changed'],
  ])('shows the %i %s answer of a resume and refetches the schedules', async (status, code, detail) => {
    const user = userEvent.setup()
    const fake = await openPolling(OPERATOR, {
      schedules: [makeStoppedSchedule('s2')],
      onRequest: (request) =>
        request.method === 'POST' ? json({ title: 'Problem', status, code, detail }, status) : undefined,
    })

    await user.click(await screen.findByRole('button', { name: /Resume/ }))
    const dialog = await screen.findByRole('dialog', { name: 'Resume stopped unit' })
    const before = schedulesCalls(fake).length
    await user.click(within(dialog).getByRole('button', { name: 'Resume' }))

    expect(await within(dialog).findByText(detail)).toBeInTheDocument()
    await waitFor(() => expect(schedulesCalls(fake).length).toBeGreaterThan(before))
  })
})
