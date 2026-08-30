import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import i18n from '../i18n/index.js'
import SettingsPage from './SettingsPage.jsx'

function LocationProbe() {
  return <output data-testid="location">{useLocation().pathname}</output>
}

describe('SettingsPage language selector', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('ca')
  })

  afterEach(() => {
    window.localStorage.clear()
  })

  it('switches translated copy without changing route', async () => {
    render(
      <MemoryRouter initialEntries={['/settings']}>
        <SettingsPage />
        <LocationProbe />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByRole('combobox', { name: 'Idioma' }), { target: { value: 'en' } })
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Settings' })).toBeInTheDocument())
    expect(screen.getByTestId('location')).toHaveTextContent('/settings')
    expect(window.localStorage.getItem('tt-league.locale')).toBe('en')
  })
})
