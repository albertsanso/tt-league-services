import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AppStateProvider } from '../../context/AppStateContext.jsx'
import { authContext } from '../../context/authContext.js'
import Sidebar from './Sidebar.jsx'
import { isRouteActive } from './routeMatching.js'
import { navigationSections } from '../../config/navigation.js'
import { routePaths } from '../../config/routes.js'

afterEach(() => {
  cleanup()
})

function renderSidebar({ roles = [], pathname = '/' } = {}) {
  window.innerWidth = 1440
  const authValue = {
    authenticated: true,
    hasRole: (role) => roles.includes(role),
    hasPermission: () => true,
  }

  return render(
    <MemoryRouter initialEntries={[pathname]}>
      <AppStateProvider>
        <authContext.Provider value={authValue}>
          <Sidebar variant="desktop" />
        </authContext.Provider>
      </AppStateProvider>
    </MemoryRouter>,
  )
}

describe('Sidebar route matching', () => {
  it('keeps the Club item active on nested routes', () => {
    expect(isRouteActive('/clubs/club-id/edit', '/clubs')).toBe(true)
    expect(isRouteActive('/clubs/club-id/competition/2024/Preferent', '/clubs')).toBe(true)
  })

  it('does not activate similarly prefixed routes', () => {
    expect(isRouteActive('/clubs-other', '/clubs')).toBe(false)
  })

  it('defines the administration destinations and nested active states', () => {
    const administration = navigationSections
      .flatMap((section) => section.items)
      .find((item) => item.id === 'administration')

    expect(administration.role).toBe('ADMIN')
    expect(administration.children.map((child) => child.path)).toEqual([
      routePaths.administrationUsers,
      routePaths.administrationSettings,
      routePaths.administrationImport,
    ])
    expect(isRouteActive(routePaths.administrationUsers, routePaths.administration)).toBe(true)
    expect(isRouteActive(routePaths.administrationImport, routePaths.administrationImport)).toBe(true)
  })

  it('renders administration navigation for administrators', () => {
    renderSidebar({ roles: ['ADMIN'] })

    const toggle = screen.getByRole('button', { name: 'Mostrar o amagar les opcions d’administració' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Usuaris i rols')).not.toBeInTheDocument()

    fireEvent.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Usuaris i rols')).toBeInTheDocument()
    expect(screen.getByText('Configuració del sistema')).toBeInTheDocument()
    expect(screen.getByText('Importació de dades')).toBeInTheDocument()

    fireEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Usuaris i rols')).not.toBeInTheDocument()
  })

  it('hides administration navigation from non-administrators', () => {
    renderSidebar()

    expect(screen.queryByRole('button', { name: 'Mostrar o amagar les opcions d’administració' })).not.toBeInTheDocument()
    expect(screen.queryByText('Usuaris i rols')).not.toBeInTheDocument()
    expect(screen.queryByText('Configuració del sistema')).not.toBeInTheDocument()
    expect(screen.queryByText('Importació de dades')).not.toBeInTheDocument()
  })
})
