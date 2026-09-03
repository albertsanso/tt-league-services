import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ImportSourceSelector from './ImportSourceSelector.jsx'

afterEach(cleanup)

const sources = [
  { id: 'RFETM', label: 'RFETM', status: 'available' },
  { id: 'BCNESA', label: 'BCNESA', status: 'unavailable' },
  { id: 'FCTT', label: 'FCTT', status: 'loading' },
]

describe('ImportSourceSelector', () => {
  it('renders exactly three source rows with star-only accessible statuses', () => {
    render(<ImportSourceSelector sources={{ data: sources, loading: false, error: null }} selected="" onSelect={vi.fn()} />)

    expect(screen.getAllByRole('article')).toHaveLength(3)
    expect(screen.queryByText('Disponible')).not.toBeInTheDocument()
    expect(screen.queryByText('No disponible')).not.toBeInTheDocument()
    expect(screen.queryByText('Comprovant')).not.toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Disponible' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'No disponible' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Comprovant' })).toBeInTheDocument()
    expect(screen.getByText('Estat de les fonts actualitzat.')).toBeInTheDocument()
  })

  it('selects a source with a keyboard-accessible button', () => {
    const onSelect = vi.fn()
    render(<ImportSourceSelector sources={{ data: sources, loading: false, error: null }} selected="" onSelect={onSelect} />)

    const sourceButton = screen.getByText('BCNESA').closest('button')
    fireEvent.click(sourceButton)

    expect(onSelect).toHaveBeenCalledWith('BCNESA')
    expect(sourceButton).toHaveAttribute('aria-pressed', 'false')
  })

  it('selects a source when its star area is clicked', () => {
    const onSelect = vi.fn()
    render(<ImportSourceSelector sources={{ data: sources, loading: false, error: null }} selected="" onSelect={onSelect} />)

    fireEvent.click(screen.getByRole('img', { name: 'Disponible' }))

    expect(onSelect).toHaveBeenCalledWith('RFETM')
  })

  it('announces polling errors while retaining the last known source statuses', () => {
    render(<ImportSourceSelector sources={{
      data: sources,
      loading: false,
      error: new Error('offline'),
      retry: vi.fn(),
    }} selected="" onSelect={vi.fn()} />)

    expect(screen.getByText("No s'han pogut carregar les fonts d'importació.")).toBeInTheDocument()
    expect(screen.queryByText('Error')).not.toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Disponible' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'No disponible' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Comprovant' })).toBeInTheDocument()
    expect(screen.getByText('No s’ha pogut actualitzar l’estat de les fonts.')).toBeInTheDocument()
  })
})
