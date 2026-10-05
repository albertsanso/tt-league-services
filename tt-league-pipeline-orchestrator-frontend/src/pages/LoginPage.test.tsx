import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { clearToken } from '../auth/tokenStorage'
import { json, stubFetch } from '../test/fakeFetch'
import { makeToken } from '../test/jwt'
import { renderApp, storeSession, stubBackends } from '../test/renderApp'
import { safeRedirectPath } from './safeRedirectPath'

afterEach(() => {
  vi.unstubAllGlobals()
  clearToken()
  window.sessionStorage.clear()
})

describe('LoginPage', () => {
  it('shows the platform message for a wrong password and stays on the form', async () => {
    stubBackends({ loginStatus: 401 })
    renderApp('/login')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(/username/i), 'operator')
    await user.type(screen.getByLabelText(/password/i), 'bad')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Invalid username or password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })

  it('sets autocomplete hints and disables submit while the request is pending', async () => {
    let release: () => void = () => undefined
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    stubFetch(async (request) => {
      if (request.url.endsWith('/login')) {
        await gate
        return json({ token: makeToken(), type: 'Bearer', username: 'operator' })
      }
      if (request.url.endsWith('/events')) {
        return new Response(new ReadableStream<Uint8Array>({ start() {} }), { status: 200 })
      }
      return json({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })
    })
    renderApp('/login')
    const user = userEvent.setup()

    const username = await screen.findByLabelText(/username/i)
    expect(username).toHaveAttribute('autocomplete', 'username')
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('autocomplete', 'current-password')
    await user.type(username, 'operator')
    await user.type(screen.getByLabelText(/password/i), 'secret')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByRole('button', { name: 'Sign in' })).toBeDisabled()
    release()
    expect(await screen.findByRole('heading', { name: 'Runs' })).toBeInTheDocument()
  })

  it('redirects a signed-in visitor away from the login page', async () => {
    stubBackends()
    storeSession()
    renderApp('/login')

    expect(await screen.findByRole('heading', { name: 'Runs' })).toBeInTheDocument()
  })
})

describe('safeRedirectPath', () => {
  it.each([
    ['/calendar', '/calendar'],
    ['/runs/abc?x=1', '/runs/abc?x=1'],
    ['//evil.example', '/runs'],
    ['https://evil.example', '/runs'],
    ['/login', '/runs'],
    [undefined, '/runs'],
    [42, '/runs'],
  ])('maps %s to %s', (input, expected) => {
    expect(safeRedirectPath(input)).toBe(expected)
  })
})
