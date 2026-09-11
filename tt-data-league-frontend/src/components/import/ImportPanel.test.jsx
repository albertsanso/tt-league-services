import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ImportPanel from './ImportPanel.jsx'
import { useAuth } from '../../context/useAuth.js'
import { createImportPreview, startImport, uploadImportFile } from '../../api/importJobs.js'
import { useImportSourceStatus } from '../../hooks/useImportSourceStatus.js'
import { useImportResources } from '../../hooks/useImportResources.js'
import { useImportProcessStatus } from '../../hooks/useImportProcessStatus.js'

vi.mock('../../context/useAuth.js', () => ({ useAuth: vi.fn() }))
vi.mock('../../api/importJobs.js', () => ({
  createImportPreview: vi.fn(),
  getImportPreviewStatus: vi.fn(),
  startImport: vi.fn(),
  uploadImportFile: vi.fn(),
}))
vi.mock('../../hooks/useImportSourceStatus.js', () => ({ useImportSourceStatus: vi.fn() }))
vi.mock('../../hooks/useImportResources.js', () => ({ useImportResources: vi.fn() }))
vi.mock('../../hooks/useImportProcessStatus.js', () => ({
  useImportProcessStatus: vi.fn(),
  isActiveImportRunStatus: (status) => ['queued', 'running'].includes(status),
}))

describe('ImportPanel resources', () => {
  let refreshStatus
  let refreshResources

  beforeEach(() => {
    window.sessionStorage.clear()
    refreshStatus = vi.fn()
    refreshResources = vi.fn()
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn() })
    useImportSourceStatus.mockReturnValue({
      data: [{ id: 'RFETM', label: 'RFETM', status: 'available' }],
      loading: false,
      error: null,
      refresh: refreshStatus,
    })
    useImportResources.mockReturnValue({
      data: [],
      loading: false,
      error: null,
      retry: vi.fn(),
      refresh: refreshResources,
    })
    useImportProcessStatus.mockReturnValue({ runId: null, data: null, loading: false, error: null })
    uploadImportFile.mockResolvedValue({ status: 'ACCEPTED' })
    createImportPreview.mockResolvedValue({ status: 'PREVIEW' })
    startImport.mockResolvedValue({ response: { runId: 'run-1', status: 'queued' } })
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('requests resources after selecting a source and renders their details', async () => {
    useImportResources.mockReturnValue({
      data: [{
        id: 'resource-1',
        filename: null,
        season: '2025-2026',
        resourceType: 'ACTAS',
        status: 'PROCESSED',
        createdDate: '2026-09-01T10:04:47.985968Z',
        lastProcessedDate: '2026-09-02',
      }],
      loading: false,
      error: null,
      retry: vi.fn(),
      refresh: refreshResources,
    })

    render(<ImportPanel />)

    expect(screen.getByText('Selecciona una font')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))
    expect(screen.getByText('Recursos d’importació')).toBeInTheDocument()
    expect(screen.getByText('2025-2026')).toBeInTheDocument()
    expect(screen.getByText('ACTAS')).toBeInTheDocument()
    expect(screen.getByText('2026-09-01 10:04')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Simula' }))
    await waitFor(() => expect(createImportPreview).toHaveBeenCalledWith(
      'token',
      'resource-1',
      expect.any(Function),
    ))
    fireEvent.click(screen.getByRole('button', { name: 'Importa' }))
    await waitFor(() => expect(startImport).toHaveBeenCalledWith('token', 'resource-1', expect.any(Function)))
    expect(screen.getByText('PROCESSED')).toBeInTheDocument()
  })

  it('shows loading, empty, and retryable error states', () => {
    useImportResources.mockReturnValue({
      data: [],
      loading: true,
      error: null,
      retry: vi.fn(),
      refresh: refreshResources,
    })
    const { rerender } = render(<ImportPanel />)
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))
    expect(screen.getByText('Carregant recursos d’importació...')).toBeInTheDocument()

    const retry = vi.fn()
    useImportResources.mockReturnValue({ data: [], loading: false, error: new Error('offline'), retry, refresh: refreshResources })
    rerender(<ImportPanel />)
    expect(screen.getByRole('alert')).toHaveTextContent("No s'han pogut carregar les fonts d'importació.")
    fireEvent.click(screen.getByRole('button', { name: 'Reintenta' }))
    expect(retry).toHaveBeenCalledOnce()
  })

  it('refreshes source status and resources after upload', async () => {
    render(<ImportPanel />)
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))
    const file = new File(['zip'], 'season.zip', { type: 'application/zip' })
    fireEvent.change(screen.getByLabelText('Fitxer d’importació'), { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: 'Carrega' }))

    await waitFor(() => expect(uploadImportFile).toHaveBeenCalled())
    expect(refreshStatus).toHaveBeenCalledOnce()
    expect(refreshResources).toHaveBeenCalledOnce()
  })

  it('polls for the newly uploaded resource until it shows up as PENDING, then stops refreshing', async () => {
    vi.useFakeTimers()
    try {
      let mockState = { data: [], loading: false, error: null, retry: vi.fn(), refresh: refreshResources }
      useImportResources.mockImplementation(() => mockState)

      const { rerender } = render(<ImportPanel />)
      fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))

      const file = new File(['zip'], 'season.zip', { type: 'application/zip' })
      fireEvent.change(screen.getByLabelText('Fitxer d’importació'), { target: { files: [file] } })
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Carrega' }))
        await Promise.resolve()
        await Promise.resolve()
      })
      expect(refreshResources).toHaveBeenCalledTimes(1)

      // Backend hasn't registered the new resource yet: the poll fires again after the interval.
      await act(async () => {
        await vi.advanceTimersByTimeAsync(2000)
      })
      expect(refreshResources).toHaveBeenCalledTimes(2)

      // The resource now shows up as PENDING (simulating the hook's state updating after refresh).
      mockState = {
        ...mockState,
        data: [{ id: 'new-resource', season: '2025-2026', resourceType: 'ACTAS', status: 'PENDING' }],
      }
      await act(async () => {
        rerender(<ImportPanel />)
      })

      // Polling stops: no further refresh calls even after another interval elapses.
      await act(async () => {
        await vi.advanceTimersByTimeAsync(2000)
      })
      expect(refreshResources).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('refreshes the resource list as soon as the run becomes active, before it finishes', async () => {
    useImportResources.mockReturnValue({
      data: [{ id: 'resource-1', season: '2025-2026', resourceType: 'ACTAS', status: 'PENDING' }],
      loading: false,
      error: null,
      retry: vi.fn(),
      refresh: refreshResources,
    })
    startImport.mockResolvedValue({ response: { runId: 'run-1', status: 'queued' } })
    useImportProcessStatus.mockImplementation((runId) => (runId
      ? {
          runId,
          loading: false,
          error: null,
          data: { status: 'running', processed: 0, total: 5, percentage: 0, skipped: 0, errorCount: 0, result: null },
        }
      : { runId: null, loading: false, error: null, data: null }))

    render(<ImportPanel />)
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))
    expect(refreshResources).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Importa' }))

    await waitFor(() => expect(startImport).toHaveBeenCalledWith('token', 'resource-1', expect.any(Function)))
    await waitFor(() => expect(refreshResources).toHaveBeenCalled())
  })

  it('submits the import asynchronously and polls the run until it succeeds', async () => {
    useImportResources.mockReturnValue({
      data: [{ id: 'resource-1', season: '2025-2026', resourceType: 'ACTAS' }],
      loading: false,
      error: null,
      retry: vi.fn(),
      refresh: refreshResources,
    })
    startImport.mockResolvedValue({ response: { runId: 'run-1', status: 'queued' } })
    useImportProcessStatus.mockReturnValue({
      runId: 'run-1',
      loading: false,
      error: null,
      data: {
        status: 'success',
        processed: 1,
        total: 1,
        percentage: 100,
        skipped: 0,
        errorCount: 0,
        result: { status: 'success', filesSeen: 1, itemsPersisted: 2, skipped: 0, findings: [], processingErrors: [] },
      },
    })

    render(<ImportPanel />)
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Importa' }))

    await waitFor(() => expect(startImport).toHaveBeenCalledWith('token', 'resource-1', expect.any(Function)))
    await waitFor(() => expect(screen.getByText('Resultat de la importació')).toBeInTheDocument())
    expect(screen.getByText('La importació ha desat 2 element(s).')).toBeInTheDocument()
    expect(screen.getByText('Correcta')).toBeInTheDocument()
  })

  it('shows queued/running progress before the run reaches a terminal state', async () => {
    useImportResources.mockReturnValue({
      data: [{ id: 'resource-1', season: '2025-2026', resourceType: 'ACTAS' }],
      loading: false,
      error: null,
      retry: vi.fn(),
      refresh: refreshResources,
    })
    startImport.mockResolvedValue({ response: { runId: 'run-1', status: 'queued' } })
    useImportProcessStatus.mockReturnValue({
      runId: 'run-1',
      loading: false,
      error: null,
      data: { status: 'running', processed: 2, total: 5, percentage: 40, skipped: 0, errorCount: 0, result: null },
    })

    render(<ImportPanel />)
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Importa' }))

    await waitFor(() => expect(screen.getByText('En curs')).toBeInTheDocument())
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '40')
  })

  it('disables starting or simulating another import while one is already running', async () => {
    useImportResources.mockReturnValue({
      data: [
        { id: 'resource-1', season: '2025-2026', resourceType: 'ACTAS' },
        { id: 'resource-2', season: '2024-2025', resourceType: 'ACTAS' },
      ],
      loading: false,
      error: null,
      retry: vi.fn(),
      refresh: refreshResources,
    })
    startImport.mockResolvedValue({ response: { runId: 'run-1', status: 'queued' } })
    useImportProcessStatus.mockReturnValue({
      runId: 'run-1',
      loading: false,
      error: null,
      data: { status: 'running', processed: 1, total: 2, percentage: 50, skipped: 0, errorCount: 0, result: null },
    })

    const { rerender } = render(<ImportPanel />)
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Importa' })[0])

    await waitFor(() => expect(startImport).toHaveBeenCalledWith('token', 'resource-1', expect.any(Function)))
    await waitFor(() => expect(screen.getByText('En curs')).toBeInTheDocument())

    screen.getAllByRole('button', { name: 'Importa' }).forEach((button) => expect(button).toBeDisabled())
    screen.getAllByRole('button', { name: 'Simula' }).forEach((button) => expect(button).toBeDisabled())

    useImportProcessStatus.mockReturnValue({
      runId: 'run-1',
      loading: false,
      error: null,
      data: {
        status: 'success',
        processed: 2,
        total: 2,
        percentage: 100,
        skipped: 0,
        errorCount: 0,
        result: { status: 'success', filesSeen: 2, itemsPersisted: 2, skipped: 0, findings: [], processingErrors: [] },
      },
    })
    rerender(<ImportPanel />)

    screen.getAllByRole('button', { name: 'Importa' }).forEach((button) => expect(button).not.toBeDisabled())
    screen.getAllByRole('button', { name: 'Simula' }).forEach((button) => expect(button).not.toBeDisabled())
  })

  it('routes preview proceed to the same import process workspace', async () => {
    useImportResources.mockReturnValue({
      data: [{ id: 'resource-1', season: '2025-2026', resourceType: 'ACTAS' }],
      loading: false,
      error: null,
      retry: vi.fn(),
      refresh: refreshResources,
    })
    createImportPreview.mockResolvedValue({
      response: {
        status: 'SUCCESS',
        filesSeen: 1,
        itemsDispatched: 1,
        skipped: 0,
        validationFindings: [],
        processingErrors: [],
      },
    })
    startImport.mockResolvedValue({ response: { runId: 'run-2', status: 'queued' } })
    useImportProcessStatus.mockReturnValue({
      runId: 'run-2',
      loading: false,
      error: null,
      data: {
        status: 'empty-result',
        processed: 0,
        total: 1,
        percentage: 0,
        skipped: 1,
        errorCount: 0,
        result: { status: 'empty-result', filesSeen: 1, itemsPersisted: 0, skipped: 1, findings: [], processingErrors: [] },
      },
    })

    render(<ImportPanel />)
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Simula' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Procedeix a importar' })).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: 'Procedeix a importar' }))

    await waitFor(() => expect(screen.getByText('Resultat de la importació')).toBeInTheDocument())
    expect(screen.getByText('Sense resultats')).toBeInTheDocument()
    expect(startImport).toHaveBeenCalledWith('token', 'resource-1', expect.any(Function))
  })

  it('refreshes the selected source resource list when that source status transitions', async () => {
    useImportSourceStatus.mockReturnValue({
      data: [{ id: 'RFETM', label: 'RFETM', status: 'pending' }],
      loading: false,
      error: null,
      refresh: refreshStatus,
    })
    const { rerender } = render(<ImportPanel />)
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))

    useImportSourceStatus.mockReturnValue({
      data: [{ id: 'RFETM', label: 'RFETM', status: 'available' }],
      loading: false,
      error: null,
      refresh: refreshStatus,
    })
    rerender(<ImportPanel />)

    await waitFor(() => expect(refreshResources).toHaveBeenCalled())
  })

  it('refreshes the resource list once an active import run reaches a terminal status', async () => {
    useImportResources.mockReturnValue({
      data: [{ id: 'resource-1', season: '2025-2026', resourceType: 'ACTAS' }],
      loading: false,
      error: null,
      retry: vi.fn(),
      refresh: refreshResources,
    })
    startImport.mockResolvedValue({ response: { runId: 'run-1', status: 'queued' } })
    useImportProcessStatus.mockReturnValue({
      runId: 'run-1',
      loading: false,
      error: null,
      data: { status: 'running', processed: 1, total: 2, percentage: 50, skipped: 0, errorCount: 0, result: null },
    })

    const { rerender } = render(<ImportPanel />)
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Importa' }))
    await waitFor(() => expect(startImport).toHaveBeenCalled())

    useImportProcessStatus.mockReturnValue({
      runId: 'run-1',
      loading: false,
      error: null,
      data: {
        status: 'success',
        processed: 2,
        total: 2,
        percentage: 100,
        skipped: 0,
        errorCount: 0,
        result: { status: 'success', filesSeen: 2, itemsPersisted: 2, skipped: 0, findings: [], processingErrors: [] },
      },
    })
    rerender(<ImportPanel />)

    await waitFor(() => expect(refreshResources).toHaveBeenCalled())
  })

  it('persists the active run to sessionStorage and rehydrates it on remount', async () => {
    useImportResources.mockReturnValue({
      data: [{ id: 'resource-1', season: '2025-2026', resourceType: 'ACTAS' }],
      loading: false,
      error: null,
      retry: vi.fn(),
      refresh: refreshResources,
    })
    startImport.mockResolvedValue({ response: { runId: 'run-1', status: 'queued' } })
    useImportProcessStatus.mockReturnValue({
      runId: 'run-1',
      loading: false,
      error: null,
      data: { status: 'running', processed: 1, total: 2, percentage: 50, skipped: 0, errorCount: 0, result: null },
    })

    const { unmount } = render(<ImportPanel />)
    fireEvent.click(screen.getByRole('button', { name: /Marca RFETM/i }))
    fireEvent.click(screen.getByRole('button', { name: 'Importa' }))
    await waitFor(() => expect(startImport).toHaveBeenCalled())

    await waitFor(() => {
      const stored = JSON.parse(window.sessionStorage.getItem('import-panel:active-run'))
      expect(stored.runId).toBe('run-1')
      expect(stored.source).toBe('RFETM')
      expect(stored.resource.id).toBe('resource-1')
    })
    unmount()

    render(<ImportPanel />)

    expect(useImportProcessStatus).toHaveBeenLastCalledWith('run-1')
    expect(screen.getByText('En curs')).toBeInTheDocument()
  })
})
