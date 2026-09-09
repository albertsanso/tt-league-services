import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ClubsConsolidationPanel from './ClubsConsolidationPanel.jsx'
import { useClubSearch, useConsolidateClubs } from '../hooks/useClubs.js'

vi.mock('../hooks/useClubs.js', () => ({
  useClubSearch: vi.fn(),
  useConsolidateClubs: vi.fn(),
}))

afterEach(() => {
  cleanup()
})

const CLUBS = [
  { id: 'club-a', name: 'CTT Terrassa', source: 'RFETM' },
  { id: 'club-b', name: 'C.T.T. Terrassa', source: 'BCNESA' },
]

function search(query) {
  fireEvent.change(screen.getByLabelText('Nom del club'), { target: { value: query } })
  fireEvent.click(screen.getByRole('button', { name: 'Cercar' }))
}

describe('ClubsConsolidationPanel', () => {
  let retry
  let consolidate

  beforeEach(() => {
    retry = vi.fn()
    consolidate = vi.fn().mockResolvedValue({ id: 'club-a', name: 'CTT Terrassa Consolidat' })
    useClubSearch.mockReturnValue({ data: CLUBS, loading: false, error: null, retry })
    useConsolidateClubs.mockReturnValue({ consolidate, loading: false, error: null, success: null, reset: vi.fn() })
  })

  it('keeps the consolidate action disabled until at least two clubs are selected', () => {
    render(<ClubsConsolidationPanel />)
    search('Terrassa')

    const consolidateButton = screen.getByRole('button', { name: 'Consolida els clubs seleccionats' })
    expect(consolidateButton).toBeDisabled()

    fireEvent.click(screen.getByLabelText('Selecciona CTT Terrassa'))
    expect(consolidateButton).toBeDisabled()

    fireEvent.click(screen.getByLabelText('Selecciona C.T.T. Terrassa'))
    expect(consolidateButton).toBeEnabled()
  })

  it('opens the confirm dialog with defaults and submits the consolidation', async () => {
    render(<ClubsConsolidationPanel />)
    search('Terrassa')
    fireEvent.click(screen.getByLabelText('Selecciona CTT Terrassa'))
    fireEvent.click(screen.getByLabelText('Selecciona C.T.T. Terrassa'))
    fireEvent.click(screen.getByRole('button', { name: 'Consolida els clubs seleccionats' }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByLabelText('Nom canònic')).toHaveValue('CTT Terrassa')

    fireEvent.click(screen.getByRole('button', { name: 'Consolida' }))

    await screen.findByText('Els clubs s’han consolidat correctament.')
    expect(consolidate).toHaveBeenCalledWith({
      clubIds: ['club-a', 'club-b'],
      canonicalName: 'CTT Terrassa',
      primaryClubId: 'club-a',
    })
    expect(retry).toHaveBeenCalled()
  })

  it('keeps the canonical name synchronized with the selected Main club until manually edited', () => {
    render(<ClubsConsolidationPanel />)
    search('Terrassa')
    fireEvent.click(screen.getByLabelText('Selecciona CTT Terrassa'))
    fireEvent.click(screen.getByLabelText('Selecciona C.T.T. Terrassa'))
    fireEvent.click(screen.getByRole('button', { name: 'Consolida els clubs seleccionats' }))

    const nameInput = screen.getByLabelText('Nom canònic')
    expect(nameInput).toHaveValue('CTT Terrassa')

    fireEvent.click(screen.getByLabelText('C.T.T. Terrassa'))
    expect(nameInput).toHaveValue('C.T.T. Terrassa')

    fireEvent.click(screen.getByRole('button', { name: 'Edita el nom' }))
    fireEvent.change(nameInput, { target: { value: 'Nom personalitzat' } })

    fireEvent.click(screen.getByLabelText('CTT Terrassa'))
    expect(nameInput).toHaveValue('Nom personalitzat')
  })

  it('locks the canonical name field until the user requests to edit it, and relocks it on reopen', () => {
    render(<ClubsConsolidationPanel />)
    search('Terrassa')
    fireEvent.click(screen.getByLabelText('Selecciona CTT Terrassa'))
    fireEvent.click(screen.getByLabelText('Selecciona C.T.T. Terrassa'))
    fireEvent.click(screen.getByRole('button', { name: 'Consolida els clubs seleccionats' }))

    const nameInput = screen.getByLabelText('Nom canònic')
    expect(nameInput).toHaveAttribute('readonly')

    fireEvent.click(screen.getByRole('button', { name: 'Edita el nom' }))
    expect(nameInput).not.toHaveAttribute('readonly')

    fireEvent.change(nameInput, { target: { value: 'Nom editat' } })
    expect(nameInput).toHaveValue('Nom editat')

    fireEvent.click(screen.getByRole('button', { name: 'Cancel·la' }))
    fireEvent.click(screen.getByRole('button', { name: 'Consolida els clubs seleccionats' }))

    expect(screen.getByLabelText('Nom canònic')).toHaveAttribute('readonly')
  })

  it('shows an error banner when the consolidation request fails', async () => {
    consolidate.mockRejectedValue(new Error('El club principal ha de ser un dels clubs seleccionats'))
    render(<ClubsConsolidationPanel />)
    search('Terrassa')
    fireEvent.click(screen.getByLabelText('Selecciona CTT Terrassa'))
    fireEvent.click(screen.getByLabelText('Selecciona C.T.T. Terrassa'))
    fireEvent.click(screen.getByRole('button', { name: 'Consolida els clubs seleccionats' }))
    fireEvent.click(screen.getByRole('button', { name: 'Consolida' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El club principal ha de ser un dels clubs seleccionats',
    )
  })
})
