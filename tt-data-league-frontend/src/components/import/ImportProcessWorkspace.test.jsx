import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ImportProcessWorkspace from './ImportProcessWorkspace.jsx'

describe('ImportProcessWorkspace', () => {
  afterEach(() => cleanup())

  const resource = { resourceType: 'ACTAS', season: '2025-2026' }
  const callbacks = { onRetry: vi.fn(), onBackToResources: vi.fn() }

  it('renders each process result state and retry actions', () => {
    const { rerender } = render(<ImportProcessWorkspace resource={resource} process={{ loading: true, result: null, error: null }} {...callbacks} />)
    expect(screen.getByRole('status')).toHaveTextContent('Important ACTAS · 2025-2026')

    rerender(<ImportProcessWorkspace resource={resource} process={{ loading: false, result: { status: 'success', itemsPersisted: 2, filesSeen: 1, skipped: 0, findings: [], processingErrors: [] }, error: null }} {...callbacks} />)
    expect(screen.getByText('Correcta')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Torna als recursos' }))
    expect(callbacks.onBackToResources).toHaveBeenCalledOnce()

    rerender(<ImportProcessWorkspace resource={resource} process={{ loading: false, result: { status: 'empty-result', itemsPersisted: 0, filesSeen: 0, skipped: 0, findings: [], processingErrors: [] }, error: null }} {...callbacks} />)
    expect(screen.getByText('Sense resultats')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Torna a intentar la importació' })).toBeInTheDocument()

    rerender(<ImportProcessWorkspace resource={resource} process={{ loading: false, result: { status: 'failure', itemsPersisted: 0, filesSeen: 1, skipped: 1, findings: [{ message: 'bad data' }], processingErrors: [] }, error: null }} {...callbacks} />)
    expect(screen.getByText('Fallida')).toBeInTheDocument()
    expect(screen.getByText('bad data')).toBeInTheDocument()
  })
})
