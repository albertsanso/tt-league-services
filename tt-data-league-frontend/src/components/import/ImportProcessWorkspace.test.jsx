import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ImportProcessWorkspace from './ImportProcessWorkspace.jsx'

describe('ImportProcessWorkspace', () => {
  afterEach(() => cleanup())

  const resource = { resourceType: 'ACTAS', season: '2025-2026' }
  const callbacks = { onRetry: vi.fn(), onBackToResources: vi.fn() }

  it('renders the initial submission loading state', () => {
    render(<ImportProcessWorkspace resource={resource} process={{ loading: true, run: null, error: null }} {...callbacks} />)
    expect(screen.getByRole('status')).toHaveTextContent('Important ACTAS · 2025-2026')
  })

  it('renders queued and running progress, indeterminate then determinate', () => {
    const { rerender } = render(<ImportProcessWorkspace resource={resource}
      process={{ loading: false, error: null, run: { status: 'queued', processed: 0, total: null, percentage: null, skipped: 0, errorCount: 0, result: null } }}
      {...callbacks} />)
    expect(screen.getByText('En cua')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuetext', 'Calculant el progrés...')

    rerender(<ImportProcessWorkspace resource={resource}
      process={{ loading: false, error: null, run: { status: 'running', processed: 3, total: 10, percentage: 30, skipped: 1, errorCount: 0, result: null } }}
      {...callbacks} />)
    expect(screen.getByText('En curs')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '30')
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('10')).toBeInTheDocument()
  })

  it('renders each terminal result state and retry/back actions', () => {
    const { rerender } = render(<ImportProcessWorkspace resource={resource}
      process={{ loading: false, error: null, run: { status: 'success', processed: 1, total: 1, percentage: 100, skipped: 0, errorCount: 0, result: { status: 'success', itemsPersisted: 2, filesSeen: 1, skipped: 0, findings: [], processingErrors: [] } } }}
      {...callbacks} />)
    expect(screen.getByText('Correcta')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Torna als recursos' }))
    expect(callbacks.onBackToResources).toHaveBeenCalledOnce()

    rerender(<ImportProcessWorkspace resource={resource}
      process={{ loading: false, error: null, run: { status: 'empty-result', processed: 0, total: 0, percentage: 0, skipped: 0, errorCount: 0, result: { status: 'empty-result', itemsPersisted: 0, filesSeen: 0, skipped: 0, findings: [], processingErrors: [] } } }}
      {...callbacks} />)
    expect(screen.getByText('Sense resultats')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Torna a intentar la importació' })).toBeInTheDocument()

    rerender(<ImportProcessWorkspace resource={resource}
      process={{ loading: false, error: null, run: { status: 'failure', processed: 1, total: 2, percentage: 50, skipped: 1, errorCount: 1, result: { status: 'failure', itemsPersisted: 0, filesSeen: 1, skipped: 1, findings: [{ message: 'bad data' }], processingErrors: [] } } }}
      {...callbacks} />)
    expect(screen.getByText('Fallida')).toBeInTheDocument()
    expect(screen.getByText('bad data')).toBeInTheDocument()
  })

  it('renders a network/submission error with a retry action', () => {
    render(<ImportProcessWorkspace resource={resource}
      process={{ loading: false, error: { status: 500 }, run: null }}
      {...callbacks} />)
    expect(screen.getByText('La importació ha fallat. Revisa els errors de processament.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Torna a intentar la importació' }))
    expect(callbacks.onRetry).toHaveBeenCalledWith(resource)
  })
})
