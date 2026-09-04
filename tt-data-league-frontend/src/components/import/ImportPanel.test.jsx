import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ImportPanel from './ImportPanel.jsx'
import { useAuth } from '../../context/useAuth.js'
import { createImportPreview, getImportHistory, startImport, uploadImportFile } from '../../api/importJobs.js'
import { useImportSourceStatus } from '../../hooks/useImportSourceStatus.js'
import { useImportResources } from '../../hooks/useImportResources.js'

vi.mock('../../context/useAuth.js', () => ({ useAuth: vi.fn() }))
vi.mock('../../api/importJobs.js', () => ({
  createImportPreview: vi.fn(),
  getImportHistory: vi.fn(),
  startImport: vi.fn(),
  uploadImportFile: vi.fn(),
}))
vi.mock('../../hooks/useImportSourceStatus.js', () => ({ useImportSourceStatus: vi.fn() }))
vi.mock('../../hooks/useImportResources.js', () => ({ useImportResources: vi.fn() }))

describe('ImportPanel resources', () => {
  let refreshStatus
  let refreshResources

  beforeEach(() => {
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
    uploadImportFile.mockResolvedValue({ status: 'ACCEPTED' })
    createImportPreview.mockResolvedValue({ status: 'PREVIEW' })
    startImport.mockResolvedValue({ status: 'STARTED' })
    getImportHistory.mockResolvedValue([])
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
})
