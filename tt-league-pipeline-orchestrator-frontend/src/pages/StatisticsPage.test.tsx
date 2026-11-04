import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { clearToken } from '../auth/tokenStorage'
import { renderApp, storeSession, stubBackends } from '../test/renderApp'
import {
  emptyCorrections,
  emptyDaily,
  emptyHealth,
  emptyPending,
  emptyRuns,
  emptyTimeToReport,
  emptyUnits,
  makeProgress,
} from '../test/statisticsFixtures'
import type { StatisticsOverrides } from '../test/statisticsFixtures'

// The page and its charts are a lazy chunk; loading it once up front keeps the first test inside the 5 s wait.
beforeAll(async () => {
  await import('./StatisticsPage')
}, 60_000)

afterEach(() => {
  vi.unstubAllGlobals()
  clearToken()
  window.sessionStorage.clear()
})

async function openStatistics(options: StatisticsOverrides = {}, path = '/statistics') {
  const fake = stubBackends({ statistics: options })
  storeSession()
  renderApp(path)
  await screen.findByRole('heading', { name: 'Statistics' })
  return fake
}

const panel = (name: string) => screen.findByRole('region', { name })
const statisticsCalls = (fake: ReturnType<typeof stubBackends>, endpoint: string) =>
  fake.callsTo(`/api/pipeline/statistics/${endpoint}`)

describe('StatisticsPage', () => {
  it('shows every panel with the figures of the server', async () => {
    await openStatistics()

    const daily = within(await panel('Daily overview')).getByRole('table', { name: 'Daily overview table' })
    const firstDay = within(daily).getByRole('row', { name: /2026-10-03/ })
    expect(within(firstDay).getAllByRole('cell').map((cell) => cell.textContent)).toEqual([
      '2026-10-03',
      'FCTT',
      '4',
      '1',
      '7',
      '1.5 h',
      '3',
    ])
    // a day without arrivals has no average
    expect(within(within(daily).getByRole('row', { name: /2026-10-04/ })).getAllByRole('cell')[5]).toHaveTextContent('—')

    const outcomes = within(await panel('Runs by outcome'))
    expect(within(outcomes.getByRole('table', { name: 'Runs by outcome table' })).getByRole('row', { name: /2026-10-03/ }))
      .toHaveTextContent('2026-10-03FCTT2101')
    expect(outcomes.getByRole('table', { name: 'Average step duration' })).toHaveTextContent('1 min 30 s')

    const timeToReport = within(await panel('Time to report'))
    expect(timeToReport.getByRole('table', { name: 'Time to report table' })).toHaveTextContent('FCTT66.0 h20.0 h')

    expect(within(await panel('Pending by age')).getByRole('table', { name: 'Pending by age table' })).toHaveTextContent(
      'FCTT12345',
    )

    const corrections = within(await panel('Corrections after first report'))
    expect(corrections.getByRole('table', { name: 'Corrections table' })).toHaveTextContent('2026-10-03FCTT2')
    expect(corrections.getByRole('table', { name: 'Corrections totals' })).toHaveTextContent('FCTT2')

    const health = within(await panel('Source health'))
    expect(health.getByRole('table', { name: 'Source health table' })).toHaveTextContent('2026-10-03FCTT321410')
  })

  it('shows one health card per source and flags a source with parse errors', async () => {
    await openStatistics()

    const fctt = await screen.findByRole('article', { name: 'FCTT source health' })
    expect(within(fctt).getByText('Parse errors', { selector: '.MuiChip-label' })).toBeInTheDocument()
    expect(within(fctt).getByText('HTTP errors').nextSibling).toHaveTextContent('3')
    const rfetm = screen.getByRole('article', { name: 'RFETM source health' })
    expect(within(rfetm).queryByText('Parse errors', { selector: '.MuiChip-label' })).not.toBeInTheDocument()
  })

  it('lists the time to report per category in an expandable table', async () => {
    const user = userEvent.setup()
    await openStatistics()
    const timeToReport = within(await panel('Time to report'))

    await user.click(timeToReport.getByRole('button', { name: 'By category' }))

    const table = timeToReport.getByRole('table', { name: 'Time to report by category' })
    expect(within(table).getByRole('row', { name: /TERCERA/ })).toHaveTextContent('FCTTTERCERA56.0 h10.0 h')
    expect(within(table).getByRole('row', { name: /PREFERENT/ })).toHaveTextContent('FCTTPREFERENT120.0 h20.0 h')
  })

  it('lists the units by outcome with their average duration', async () => {
    await openStatistics()

    const units = within(await panel('Units by outcome'))
    const table = units.getByRole('table', { name: 'Units by outcome table' })
    expect(within(table).getByRole('row', { name: /Full season/ })).toHaveTextContent('Full seasonFCTT520102 min 05 s')
    expect(within(table).getByRole('row', { name: /Tercera Group 1/ })).toHaveTextContent('Tercera Group 1FCTT30121—')
  })

  it('offers the units in the unit filter and limits the run outcomes to the chosen one', async () => {
    const user = userEvent.setup()
    const fake = await openStatistics({}, '/statistics?from=2026-09-01&to=2026-09-30&season=2026-2027')
    await panel('Runs by outcome')
    expect(statisticsCalls(fake, 'runs')[0].url).not.toContain('unitKey')

    await user.click(screen.getByRole('combobox', { name: 'Unit' }))
    await user.click(await screen.findByRole('option', { name: 'Tercera Group 1' }))

    await waitFor(() => expect(statisticsCalls(fake, 'runs')).toHaveLength(2))
    expect(statisticsCalls(fake, 'runs')[1].url).toContain(`unitKey=${'a'.repeat(64)}`)
    // the unit panel keeps listing every unit, whatever the filter
    expect(statisticsCalls(fake, 'units')[1].url).not.toContain('unitKey')
  })

  it('reads the unit filter from the URL', async () => {
    const fake = await openStatistics({}, '/statistics?from=2026-09-01&to=2026-09-30&season=2026-2027&unit=season')
    await panel('Runs by outcome')

    expect(statisticsCalls(fake, 'runs')[0].url).toContain('unitKey=season')
    expect(screen.getByRole('combobox', { name: 'Unit' })).toHaveTextContent('Full season')
  })

  it('notes that days are grouped in the server zone', async () => {
    await openStatistics()

    expect(await screen.findByText('Days are grouped in the server time zone (Europe/Madrid).')).toBeInTheDocument()
  })

  it('asks for the last 30 days and the latest season by default', async () => {
    const fake = await openStatistics()
    await panel('Daily overview')

    const [daily] = statisticsCalls(fake, 'daily')
    const params = new URL(daily.url, 'http://localhost').searchParams
    expect(params.get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(params.get('to')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(params.getAll('source')).toEqual([])
    const [timeToReport] = statisticsCalls(fake, 'time-to-report')
    expect(new URL(timeToReport.url, 'http://localhost').searchParams.get('season')).toBe('2026-2027')
  })

  it('reads the filters from the URL', async () => {
    const fake = await openStatistics({}, '/statistics?source=FCTT&season=2025-2026&from=2026-09-01&to=2026-09-30')
    await panel('Daily overview')

    const [daily] = statisticsCalls(fake, 'daily')
    expect(daily.url).toBe('/api/pipeline/statistics/daily?from=2026-09-01&to=2026-09-30&source=FCTT')
    const [timeToReport] = statisticsCalls(fake, 'time-to-report')
    expect(timeToReport.url).toBe('/api/pipeline/statistics/time-to-report?season=2025-2026&source=FCTT')
  })

  it('refetches with the new query when a filter changes', async () => {
    const user = userEvent.setup()
    const fake = await openStatistics({}, '/statistics?from=2026-09-01&to=2026-09-30&season=2026-2027')
    await panel('Daily overview')
    expect(statisticsCalls(fake, 'daily')).toHaveLength(1)

    await user.click(screen.getByRole('combobox', { name: 'Source' }))
    await user.click(await screen.findByRole('option', { name: 'RFETM' }))
    await user.keyboard('{Escape}')

    await waitFor(() => expect(statisticsCalls(fake, 'daily')).toHaveLength(2))
    expect(statisticsCalls(fake, 'daily')[1].url).toBe(
      '/api/pipeline/statistics/daily?from=2026-09-01&to=2026-09-30&source=RFETM',
    )
    expect(statisticsCalls(fake, 'source-health')[1].url).toContain('source=RFETM')
  })

  it('refetches everything from the refresh button', async () => {
    const user = userEvent.setup()
    const fake = await openStatistics({}, '/statistics?from=2026-09-01&to=2026-09-30&season=2026-2027')
    await panel('Daily overview')

    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    await waitFor(() => expect(statisticsCalls(fake, 'daily')).toHaveLength(2))
    expect(statisticsCalls(fake, 'runs')).toHaveLength(2)
    expect(statisticsCalls(fake, 'pending')).toHaveLength(2)
  })

  it('does not request anything for a reversed range and says so', async () => {
    const fake = await openStatistics({}, '/statistics?from=2026-10-05&to=2026-10-01&season=2026-2027')

    expect(await screen.findByText('The "from" date is after the "to" date.')).toBeInTheDocument()
    expect(statisticsCalls(fake, 'daily')).toHaveLength(0)
  })

  it('shows the empty state when the range has no data', async () => {
    await openStatistics({
      daily: emptyDaily,
      runs: emptyRuns,
      units: emptyUnits,
      corrections: emptyCorrections,
      sourceHealth: emptyHealth,
      pending: emptyPending,
      timeToReport: emptyTimeToReport,
    })

    expect(await screen.findByText('No statistics for this range')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Daily overview' })).not.toBeInTheDocument()
  })

  it('shows an error with a retry that loads again', async () => {
    const user = userEvent.setup()
    const fake = await openStatistics({ failWith: 500 }, '/statistics?from=2026-09-01&to=2026-09-30&season=2026-2027')

    expect(await screen.findByRole('alert')).toHaveTextContent(/Statistics unavailable|500/)
    const failed = statisticsCalls(fake, 'daily').length

    await user.click(screen.getByRole('button', { name: 'Retry' }))

    await waitFor(() => expect(statisticsCalls(fake, 'daily').length).toBeGreaterThan(failed))
  })

  it('shows skeletons while the first load is in flight', async () => {
    let release: () => void = () => undefined
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    stubBackends({ gateStatistics: gate })
    storeSession()
    renderApp('/statistics?from=2026-09-01&to=2026-09-30&season=2026-2027')

    expect(await screen.findByLabelText('Loading statistics')).toBeInTheDocument()
    release()
    expect(await panel('Daily overview')).toBeInTheDocument()
    expect(screen.queryByLabelText('Loading statistics')).not.toBeInTheDocument()
  })
})

describe('StatisticsPage reporting progress', () => {
  it('loads the match days of one source and shows the selected one', async () => {
    const user = userEvent.setup()
    const fake = await openStatistics({}, '/statistics?source=FCTT&season=2026-2027&from=2026-09-01&to=2026-09-30')
    const progress = within(await panel('Reporting progress'))

    const table = await progress.findByRole('table', { name: 'Match days' })
    expect(within(table).getByRole('row', { name: /round 3/ })).toHaveTextContent('TERCERA · group 1 · 1a Fase · round 3OPEN86175%')
    expect(within(table).getByRole('row', { name: /round 4/ })).toHaveTextContent('0%')
    expect(statisticsCalls(fake, 'reporting-progress')[0].url).toBe(
      '/api/pipeline/statistics/reporting-progress?source=FCTT&season=2026-2027',
    )
    expect(progress.getByRole('heading', { name: /round 3: 2026-10-03 to 2026-10-06/ })).toBeInTheDocument()

    await user.click(progress.getByRole('button', { name: /Show .* round 4/ }))

    expect(progress.getByRole('heading', { name: /round 4: 2026-10-10 to 2026-10-13/ })).toBeInTheDocument()
    expect(progress.getByText('The window of this match day has not started.')).toBeInTheDocument()
  })

  it('reloads the progress with the chosen competition', async () => {
    const user = userEvent.setup()
    const fake = await openStatistics({}, '/statistics?source=FCTT&season=2026-2027&from=2026-09-01&to=2026-09-30')
    const progress = within(await panel('Reporting progress'))
    await progress.findByRole('table', { name: 'Match days' })

    await user.click(progress.getByRole('combobox', { name: 'Progress category' }))
    await user.click(await screen.findByRole('option', { name: 'TERCERA-masculino' }))

    await waitFor(() => expect(statisticsCalls(fake, 'reporting-progress')).toHaveLength(2))
    expect(statisticsCalls(fake, 'reporting-progress')[1].url).toContain('competition=TERCERA-masculino')
  })

  it('says so when no match day has dates', async () => {
    await openStatistics(
      { progress: { ...makeProgress(), matchDays: [] } },
      '/statistics?source=FCTT&season=2026-2027&from=2026-09-01&to=2026-09-30',
    )

    expect(await within(await panel('Reporting progress')).findByText('No dated match days for this selection.')).toBeInTheDocument()
  })
})
