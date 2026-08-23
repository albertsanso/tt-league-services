import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import ClubsSearchPage from './ClubsSearchPage.jsx'
import { useClubSearch } from '../hooks/useClubs.js'

vi.mock('../hooks/useClubs.js', () => ({
  useClubSearch: vi.fn(),
}))

function renderPage(path = '/clubs?q=te') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <ClubsSearchPage />
    </MemoryRouter>,
  )
}

describe('ClubsSearchPage', () => {
  beforeEach(() => {
    useClubSearch.mockReturnValue({
      data: [{ id: 'club-uuid', name: 'Club Terrassa', source: 'RFETM' }],
      loading: false,
      error: null,
      retry: vi.fn(),
    })
  })

  it('shows the result source and navigates with the UUID', () => {
    renderPage()

    expect(screen.getByText('Club Terrassa')).toBeInTheDocument()
    expect(screen.getByText('Font: RFETM')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Club Terrassa/ }))
      .toHaveAttribute('href', '/clubs/club-uuid?season=all&source=all')
  })

  it('shows validation instead of requesting a one-character URL query', () => {
    renderPage('/clubs?q=t')

    expect(screen.getByRole('alert')).toHaveTextContent('2 caràcters')
    expect(useClubSearch).toHaveBeenCalledWith('t')
  })

  it('exposes a retry action when the API fails', () => {
    const retry = vi.fn()
    useClubSearch.mockReturnValue({
      data: null,
      loading: false,
      error: new Error('network'),
      retry,
    })
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Reintenta' }))
    expect(retry).toHaveBeenCalledOnce()
  })
})
