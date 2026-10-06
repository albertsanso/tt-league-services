import { render, screen, within } from '@testing-library/react'
import { makeUnit } from '../test/runFixtures'
import { RunUnitsList } from './RunUnitsList'

const NOW = Date.parse('2026-10-01T10:05:05Z')

describe('RunUnitsList', () => {
  it('says there are no units yet', () => {
    render(<RunUnitsList units={[]} now={NOW} />)

    expect(screen.getByText('No units yet.')).toBeInTheDocument()
  })

  it('lists one line per unit with its status, label, duration and error code', () => {
    render(
      <RunUnitsList
        now={NOW}
        units={[
          makeUnit('u1'),
          makeUnit('u2', {
            ordinal: 1,
            label: 'Tercera Group 1',
            status: 'FAILED',
            durationMs: 5_000,
            error: { code: 'IMPORT_FAILED', message: 'bad zip' },
          }),
          makeUnit('u3', { ordinal: 2, label: 'Tercera Group 2', status: 'PENDING', startedAt: null, finishedAt: null, durationMs: null }),
        ]}
      />,
    )

    const items = within(screen.getByRole('list', { name: 'Units' })).getAllByRole('listitem')
    expect(items).toHaveLength(3)
    expect(items[0]).toHaveTextContent('SucceededFull season1 min 00 s')
    expect(items[1]).toHaveTextContent('FailedTercera Group 15 sIMPORT_FAILED')
    expect(items[2]).toHaveTextContent('PendingTercera Group 2—')
  })

  it('shows the progress bar and a running duration for the unit that is running', () => {
    render(
      <RunUnitsList
        now={NOW}
        units={[
          makeUnit('u1', {
            status: 'RUNNING_INGEST',
            startedAt: '2026-10-01T10:05:00Z',
            finishedAt: null,
            durationMs: null,
            progress: {
              step: 'INGEST',
              stage: 'DOWNLOAD',
              itemsProcessed: 2,
              itemsTotal: 4,
              percent: 50,
              currentItem: null,
              updatedAt: '2026-10-01T10:05:04Z',
            },
          }),
        ]}
      />,
    )

    expect(screen.getByRole('listitem')).toHaveTextContent('Running ingestFull season5 s')
    expect(screen.getByRole('progressbar', { name: 'Ingest progress' })).toHaveAttribute('aria-valuenow', '50')
  })
})
