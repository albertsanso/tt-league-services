import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ImportPreviewWorkspace from './ImportPreviewWorkspace.jsx'

describe('ImportPreviewWorkspace', () => {
  afterEach(() => cleanup())

  const resource = { id: 'resource-1', resourceType: 'ACTAS', season: '2025-2026' }

  it('renders loading, success, empty-result, and failure states', () => {
    const { rerender } = render(<ImportPreviewWorkspace
      resource={resource}
      preview={{ loading: true, result: null, error: null }}
      onRetry={vi.fn()}
      onProceed={vi.fn()}
    />)
    expect(screen.getByRole('status')).toHaveTextContent('Simulant ACTAS · 2025-2026')

    rerender(<ImportPreviewWorkspace
      resource={resource}
      preview={{ loading: false, result: { status: 'success', itemsDispatched: 1, filesSeen: 1, skipped: 0, validationFindings: [], processingErrors: [] }, error: null }}
      onRetry={vi.fn()}
      onProceed={vi.fn()}
    />)
    expect(screen.getByText('Correcta')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Procedeix a importar' })).toBeInTheDocument()

    rerender(<ImportPreviewWorkspace
      resource={resource}
      preview={{ loading: false, result: { status: 'empty-result', itemsDispatched: 0, filesSeen: 0, skipped: 0, validationFindings: [], processingErrors: [] }, error: null }}
      onRetry={vi.fn()}
      onProceed={vi.fn()}
    />)
    expect(screen.getByText('Sense resultats')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Torna a simular' })).toBeInTheDocument()

    rerender(<ImportPreviewWorkspace
      resource={resource}
      preview={{ loading: false, result: { status: 'failure', itemsDispatched: 1, filesSeen: 1, skipped: 0, validationFindings: [], processingErrors: [{ message: 'bad file' }] }, error: null }}
      onRetry={vi.fn()}
      onProceed={vi.fn()}
    />)
    expect(screen.getByText('Fallida')).toBeInTheDocument()
    expect(screen.getByText('bad file')).toBeInTheDocument()
  })

  it('retries and proceeds without changing the selected resource context', () => {
    const onRetry = vi.fn()
    const onProceed = vi.fn()
    const { rerender } = render(<ImportPreviewWorkspace
      resource={resource}
      preview={{ loading: false, result: { status: 'failure', itemsDispatched: 1, filesSeen: 1, skipped: 0, validationFindings: [], processingErrors: [] }, error: null }}
      onRetry={onRetry}
      onProceed={onProceed}
    />)
    fireEvent.click(screen.getByRole('button', { name: 'Torna a simular' }))
    expect(onRetry).toHaveBeenCalledWith(resource)

    rerender(<ImportPreviewWorkspace
      resource={resource}
      preview={{ loading: false, result: { status: 'success', itemsDispatched: 1, filesSeen: 1, skipped: 0, validationFindings: [], processingErrors: [] }, error: null }}
      onRetry={onRetry}
      onProceed={onProceed}
    />)
    fireEvent.click(screen.getByRole('button', { name: 'Procedeix a importar' }))
    expect(onProceed).toHaveBeenCalledWith(resource)
  })
})
