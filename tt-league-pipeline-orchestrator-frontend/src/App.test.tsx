import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { clearToken, readToken } from './auth/tokenStorage'
import { renderApp, storeSession, stubBackends } from './test/renderApp'

afterEach(() => {
  vi.unstubAllGlobals()
  clearToken()
  window.sessionStorage.clear()
})

async function signIn() {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText(/username/i), 'operator')
  await user.type(screen.getByLabelText(/password/i), 'secret')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('App routing', () => {
  it('sends a signed-out deep link to login and back after signing in', async () => {
    stubBackends()
    renderApp('/calendar')

    await signIn()

    expect(await screen.findByRole('heading', { name: 'Calendar' })).toBeInTheDocument()
    expect(readToken()).not.toBeNull()
  })

  it('redirects / to the runs page', async () => {
    stubBackends()
    storeSession()
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Runs' })).toBeInTheDocument()
  })

  it('renders the three navigation entries and marks the active one', async () => {
    stubBackends()
    storeSession()
    renderApp('/statistics')

    await screen.findByRole('heading', { name: 'Statistics' })
    const nav = screen.getAllByRole('navigation', { name: 'Sections' })[0]
    const links = within(nav).getAllByRole('link')
    expect(links.map((link) => link.textContent)).toEqual(['Calendar', 'Runs', 'Statistics'])
    expect(within(nav).getByRole('link', { name: 'Statistics' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Runs' })).not.toHaveAttribute('aria-current')
  })

  it.each([
    ['/calendar', 'Calendar'],
    ['/runs', 'Runs'],
    ['/runs/run-1', 'Run details'],
    ['/statistics', 'Statistics'],
  ])('renders the lazy route %s', async (path, heading) => {
    stubBackends()
    storeSession()
    renderApp(path)

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })

  it('shows a not-found page for unknown paths inside the layout', async () => {
    stubBackends()
    storeSession()
    renderApp('/nope')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })

  it('returns to login after signing out', async () => {
    stubBackends()
    storeSession()
    renderApp('/runs')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(readToken()).toBeNull()
  })

  it('returns to login with the expired banner when the session expires on a page', async () => {
    stubBackends()
    storeSession({ exp: Math.floor(Date.now() / 1000) + 3 })
    renderApp('/calendar')
    await screen.findByRole('heading', { name: 'Calendar' })

    expect(await screen.findByText('Your session has expired. Sign in again.', {}, { timeout: 6000 })).toBeInTheDocument()
    expect(readToken()).toBeNull()
  }, 10_000)
})
