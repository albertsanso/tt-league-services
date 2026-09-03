import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ImportPanel from './ImportPanel.jsx'
import { useAuth } from '../../context/useAuth.js'
import { createImportPreview, getImportHistory, startImport } from '../../api/importJobs.js'
import { useImportSources } from '../../hooks/useImportSources.js'

vi.mock('../../context/useAuth.js', () => ({
  useAuth: vi.fn(),
}))

vi.mock('../../api/importJobs.js', () => ({
  createImportPreview: vi.fn(),
  getImportHistory: vi.fn(),
  startImport: vi.fn(),
}))

vi.mock('../../hooks/useImportSources.js', () => ({
  useImportSources: vi.fn(),
}))

describe('ImportPanel', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  beforeEach(() => {
    useAuth.mockReturnValue({ token: 'token', clearSession: vi.fn() })
    useImportSources.mockReturnValue({
      data: [{ id: 'RFETM', label: 'RFETM' }],
      loading: false,
      error: null,
      retry: vi.fn(),
    })
    getImportHistory.mockResolvedValue([
      { season: '2025-2026', jobId: 'job-1', status: 'COMPLETED', updatedAt: '2026-09-01' },
    ])
    createImportPreview.mockResolvedValue({ status: 'PREVIEW' })
    startImport.mockResolvedValue({ status: 'STARTED' })
  })

  it('shows loading states while sources and seasons are loading', () => {
    useImportSources.mockReturnValue({ data: [], loading: true, error: null, retry: vi.fn() })

    render(<ImportPanel />)

    expect(screen.getByText('Carregant fonts...')).toBeInTheDocument()
    expect(screen.getByText('Carregant temporades...')).toBeInTheDocument()
  })

  it('shows empty states when no sources or seasons are available', async () => {
    useImportSources.mockReturnValue({ data: [], loading: false, error: null, retry: vi.fn() })
    getImportHistory.mockResolvedValue([])

    render(<ImportPanel />)

    expect(screen.getByText('No hi ha fonts configurades en aquest entorn.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('No hi ha temporades d’importació.')).toBeInTheDocument())
  })

  it.each([
    [401, 'La sessió ha caducat.'],
    [403, 'No tens permisos per gestionar importacions.'],
    [500, "No s'han pogut carregar les fonts d'importació."],
  ])('shows the appropriate history error for HTTP %s', async (status, message) => {
    const error = Object.assign(new Error('request failed'), { status })
    getImportHistory.mockRejectedValue(error)

    render(<ImportPanel />)

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(message))
  })

  it('renders sources, seasons, report workspace, and actions on success', async () => {
    render(<ImportPanel />)

    expect(screen.getByRole('button', { name: /Marca RFETM/i })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('2025-2026')).toBeInTheDocument())
    expect(screen.getByText('Informe i estat')).toBeInTheDocument()
    expect(screen.getByText('Selecciona una temporada o inicia una importació.')).toBeInTheDocument()

    const file = new File(['data'], 'season.csv', { type: 'text/csv' })
    fireEvent.change(screen.getByLabelText('Fitxer d’importació'), { target: { files: [file] } })
    const loadButtons = screen.getAllByRole('button', { name: 'Carrega' })
    expect(loadButtons[0]).toBeEnabled()
    fireEvent.click(loadButtons[0])
    await waitFor(() => expect(createImportPreview).toHaveBeenCalledWith(
      'token',
      { file: 'season.csv' },
      expect.any(Function),
    ))

    fireEvent.click(screen.getByText('RFETM').closest('button'))
    fireEvent.click(loadButtons[1])
    expect(startImport).toHaveBeenCalledWith('token', 'job-1', expect.any(Function))
  })
})
