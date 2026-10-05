import { act, render, screen, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { ApiProvider } from '../api/ApiProvider'
import { useApi } from '../api/useApi'
import { json, stubFetch } from '../test/fakeFetch'
import { makeToken } from '../test/jwt'
import { AuthProvider } from './AuthProvider'
import type { AuthContextValue } from './authContext'
import { clearToken, readToken, writeToken } from './tokenStorage'
import { useAuth } from './useAuth'

let auth: AuthContextValue

function Probe() {
  const current = useAuth()
  useEffect(() => {
    auth = current
  })
  return (
    <p>
      {current.status}
      {current.signOutReason ? `:${current.signOutReason}` : ''}
      {current.notice ? `|${current.notice}` : ''}
    </p>
  )
}

function renderProvider() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  clearToken()
  window.sessionStorage.clear()
})

describe('AuthProvider sign-in', () => {
  it('signs in through the platform and stores the token for the session', async () => {
    const token = makeToken({ sub: 'ana', permissions: ['matches:write'] })
    const fake = stubFetch(() => json({ token, type: 'Bearer', username: 'ana' }))
    renderProvider()
    expect(await screen.findByText('signed-out')).toBeInTheDocument()

    await act(() => auth.signIn('ana', 'secret'))

    expect(screen.getByText('signed-in')).toBeInTheDocument()
    expect(auth.user?.username).toBe('ana')
    expect(auth.user?.permissions).toEqual(['matches:write'])
    expect(readToken()).toBe(token)
    expect(window.localStorage.length).toBe(0)
    const call = fake.callsTo('/api/v1/auth/login')[0]
    expect(call.method).toBe('POST')
    expect(JSON.parse(call.body ?? '{}')).toEqual({ username: 'ana', password: 'secret' })
  })

  it('surfaces the platform message for a wrong password', async () => {
    stubFetch(() => json({ message: 'Invalid username or password' }, 401))
    renderProvider()
    await screen.findByText('signed-out')

    await expect(act(() => auth.signIn('ana', 'bad'))).rejects.toThrow('Invalid username or password')
    expect(screen.getByText('signed-out')).toBeInTheDocument()
    expect(readToken()).toBeNull()
  })

  it('rejects an invalid or already expired login response', async () => {
    stubFetch(() => json({ token: 'not-a-jwt', type: 'Bearer', username: 'ana' }))
    renderProvider()
    await screen.findByText('signed-out')

    await expect(act(() => auth.signIn('ana', 'x'))).rejects.toThrow('The sign-in response was invalid')
    expect(readToken()).toBeNull()
  })
})

describe('AuthProvider restore', () => {
  it('validates a stored token once with /me and signs in', async () => {
    const token = makeToken({ sub: 'ana' })
    writeToken(token)
    const fake = stubFetch(() => json({ id: '1', username: 'ana', roles: [], permissions: [] }))
    renderProvider()

    expect(await screen.findByText('signed-in')).toBeInTheDocument()
    const calls = fake.callsTo('/api/v1/auth/me')
    expect(calls).toHaveLength(1)
    expect(calls[0].headers.Authorization).toBe(`Bearer ${token}`)
  })

  it('drops a token that /me rejects as revoked', async () => {
    writeToken(makeToken())
    stubFetch(() => json({ message: 'Unauthorized' }, 401))
    renderProvider()

    expect(await screen.findByText('signed-out:expired')).toBeInTheDocument()
    expect(readToken()).toBeNull()
  })

  it('does not call /me for an expired stored token', async () => {
    writeToken(makeToken({ exp: Math.floor(Date.now() / 1000) - 10 }))
    const fake = stubFetch(() => json({}))
    renderProvider()

    expect(await screen.findByText('signed-out:expired')).toBeInTheDocument()
    expect(fake.calls()).toHaveLength(0)
    expect(readToken()).toBeNull()
  })

  it('shows a visible message and stays signed out when /me cannot be reached', async () => {
    writeToken(makeToken())
    stubFetch(() => {
      throw new TypeError('network')
    })
    renderProvider()

    expect(await screen.findByText(/signed-out\|Could not restore your session: Cannot reach the server/)).toBeInTheDocument()
  })
})

describe('AuthProvider expiry and sign-out', () => {
  it('signs out with reason expired when the token expires', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const token = makeToken({ exp: Math.floor(Date.now() / 1000) + 60 })
    stubFetch(() => json({ token, type: 'Bearer', username: 'operator' }))
    renderProvider()
    await act(() => auth.signIn('operator', 'pw'))
    expect(screen.getByText('signed-in')).toBeInTheDocument()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(61_000)
    })

    expect(screen.getByText('signed-out:expired')).toBeInTheDocument()
    expect(readToken()).toBeNull()
  })

  it('signs out when the orchestrator answers 401', async () => {
    const token = makeToken()
    stubFetch((request) =>
      request.url.includes('/api/v1/auth/login')
        ? json({ token, type: 'Bearer', username: 'operator' })
        : json({ title: 'Unauthorized' }, 401),
    )
    function Caller() {
      const api = useApi()
      return (
        <button onClick={() => void api.runs.listRuns().catch(() => undefined)}>load</button>
      )
    }
    render(
      <AuthProvider>
        <ApiProvider>
          <Probe />
          <Caller />
        </ApiProvider>
      </AuthProvider>,
    )
    await screen.findByText('signed-out')
    await act(() => auth.signIn('operator', 'pw'))

    await act(async () => {
      screen.getByRole('button', { name: 'load' }).click()
    })

    await waitFor(() => expect(screen.getByText('signed-out:expired')).toBeInTheDocument())
    expect(readToken()).toBeNull()
  })

  it('calls /logout with the token and clears storage on user sign-out', async () => {
    const token = makeToken()
    const fake = stubFetch((request) =>
      request.url.includes('/login') ? json({ token, type: 'Bearer', username: 'operator' }) : json({}, 200),
    )
    renderProvider()
    await screen.findByText('signed-out')
    await act(() => auth.signIn('operator', 'pw'))

    act(() => auth.signOut('user'))

    expect(screen.getByText('signed-out:user')).toBeInTheDocument()
    expect(readToken()).toBeNull()
    const logout = fake.callsTo('/api/v1/auth/logout')
    expect(logout).toHaveLength(1)
    expect(logout[0].headers.Authorization).toBe(`Bearer ${token}`)
  })
})
