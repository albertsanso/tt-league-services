import { render, screen } from '@testing-library/react'
import type { UnitProgress } from '../api/types'
import { UnitProgressBar } from './UnitProgressBar'

const progress = (overrides: Partial<UnitProgress> = {}): UnitProgress => ({
  step: 'INGEST',
  stage: 'DOWNLOAD',
  itemsProcessed: 3,
  itemsTotal: 10,
  percent: 30,
  currentItem: 'group-1',
  updatedAt: '2026-10-01T10:00:10Z',
  ...overrides,
})

describe('UnitProgressBar', () => {
  it('shows a determinate bar with the server percentage and the text under it', () => {
    render(<UnitProgressBar progress={progress()} />)

    const bar = screen.getByRole('progressbar', { name: 'Ingest progress' })
    expect(bar).toHaveAttribute('aria-valuenow', '30')
    expect(screen.getByText('Ingest: 30% (DOWNLOAD · 3/10 · group-1)')).toBeInTheDocument()
  })

  it('shows an indeterminate bar while the total is unknown', () => {
    render(<UnitProgressBar progress={progress({ itemsTotal: null, percent: null, itemsProcessed: 4, currentItem: null })} />)

    const bar = screen.getByRole('progressbar', { name: 'Ingest progress' })
    expect(bar).not.toHaveAttribute('aria-valuenow')
    expect(screen.getByText('Ingest: DOWNLOAD · 4')).toBeInTheDocument()
  })

  it('labels the bar with the step that is running', () => {
    render(<UnitProgressBar progress={progress({ step: 'IMPORT', stage: null, percent: 50, itemsProcessed: 5 })} />)

    expect(screen.getByRole('progressbar', { name: 'Import progress' })).toHaveAttribute('aria-valuenow', '50')
  })

  it('leaves the text out of the compact form used in table rows', () => {
    render(<UnitProgressBar progress={progress()} compact />)

    expect(screen.getByRole('progressbar', { name: 'Ingest progress' })).toBeInTheDocument()
    expect(screen.queryByText(/DOWNLOAD/)).not.toBeInTheDocument()
  })
})
