import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { makePage, makeRun, makeUnit } from '../test/runFixtures'
import { RunsTable } from './RunsTable'

function setup(runs: ReturnType<typeof makeRun>[]) {
  render(
    <MemoryRouter initialEntries={['/runs']}>
      <Routes>
        <Route
          path="/runs"
          element={<RunsTable page={makePage(runs)} filtered={false} onPageChange={() => undefined} onClearFilters={() => undefined} />}
        />
        <Route path="/runs/:id" element={<div>run page</div>} />
      </Routes>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

const groups = [
  makeUnit('u1', { label: 'Tercera Group 1' }),
  makeUnit('u2', { ordinal: 1, label: 'Tercera Group 2', status: 'FAILED', error: { code: 'IMPORT_FAILED', message: 'bad zip' } }),
  makeUnit('u3', { ordinal: 2, label: 'Tercera Group 3', status: 'PENDING', startedAt: null, finishedAt: null, durationMs: null }),
]

describe('RunsTable units', () => {
  it('summarises the finished units of a run with several units', () => {
    setup([makeRun('a', { units: groups })])

    expect(screen.getByText('2/3 units')).toBeInTheDocument()
  })

  it('keeps the units collapsed until the row is expanded', async () => {
    const user = setup([makeRun('a', { units: groups })])
    expect(screen.queryByRole('list', { name: 'Units' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^Show units of the run/ }))

    const list = await screen.findByRole('list', { name: 'Units' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(3)
    expect(within(list).getByText('IMPORT_FAILED')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Hide units of the run/ })).toHaveAttribute('aria-expanded', 'true')
  })

  it('does not open the run when the units are toggled', async () => {
    const user = setup([makeRun('a', { units: groups })])

    await user.click(screen.getByRole('button', { name: /^Show units of the run/ }))

    expect(screen.queryByText('run page')).not.toBeInTheDocument()
  })

  it('has no toggle or summary for a run without units, and no summary for a single unit', () => {
    setup([makeRun('a'), makeRun('b', { units: [makeUnit('u1')] })])

    expect(screen.getAllByRole('button', { name: /units of the run/ })).toHaveLength(1)
    expect(screen.queryByText(/^\d+\/\d+ units?$/)).not.toBeInTheDocument()
  })
})
