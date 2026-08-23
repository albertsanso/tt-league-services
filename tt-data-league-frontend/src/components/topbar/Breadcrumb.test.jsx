import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import Breadcrumb from './Breadcrumb.jsx'

function renderBreadcrumb(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Breadcrumb />
    </MemoryRouter>,
  )
}

describe('Breadcrumb', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders the Club hierarchy and current-page semantics', () => {
    renderBreadcrumb('/clubs/club-id')

    expect(screen.getByRole('link', { name: 'Cerca de clubs' })).toHaveAttribute('href', '/clubs')
    expect(screen.getByText('Detall del club')).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'General' })).toHaveAttribute('href', '/')
  })

  it('preserves Club detail state from competition routes', () => {
    renderBreadcrumb('/clubs/club-id/competition/2024/Preferent?view=matches&season=2024&source=RFETM')

    expect(screen.getByRole('link', { name: 'Detall del club' })).toHaveAttribute(
      'href',
      '/clubs/club-id?view=matches&season=2024&source=RFETM',
    )
    expect(screen.getByText('Detall de competició')).toHaveAttribute('aria-current', 'page')
  })

  it('uses the same hierarchy for Club edit routes', () => {
    renderBreadcrumb('/clubs/club-id/edit?view=players&season=2024')

    expect(screen.getByRole('link', { name: 'Detall del club' })).toHaveAttribute(
      'href',
      '/clubs/club-id?view=players&season=2024',
    )
    expect(screen.getByText('Editar club')).toHaveAttribute('aria-current', 'page')
  })
})
