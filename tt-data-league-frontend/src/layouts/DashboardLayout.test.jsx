import { cleanup, render } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DashboardLayout from './DashboardLayout.jsx'

vi.mock('../components/sidebar/Sidebar.jsx', () => ({ default: () => null }))
vi.mock('../components/topbar/TopBar.jsx', () => ({ default: () => null }))
vi.mock('../context/useAppState.js', () => ({
  useAppState: () => ({
    closeMobileDrawer: vi.fn(),
    closeUserDropdown: vi.fn(),
    isMobile: false,
    isMobileDrawerOpen: false,
  }),
}))

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<DashboardLayout />}>
          <Route path="/calendari" element={<p>calendar</p>} />
          <Route path="/clubs" element={<p>clubs</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('DashboardLayout content width', () => {
  afterEach(cleanup)

  it('lifts the content width cap only on the season calendar', () => {
    const { container } = renderAt('/calendari?view=month')

    expect(container.querySelector('.app-content-inner')).toHaveClass('app-content-inner-wide')
  })

  it('keeps the capped width on other pages', () => {
    const { container } = renderAt('/clubs')

    expect(container.querySelector('.app-content-inner')).not.toHaveClass('app-content-inner-wide')
  })
})
