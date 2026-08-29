import { renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../context/AuthContext.jsx'
import { usePlayerDetails } from './usePlayers.js'

function response(name) {
  return {
    ok: true,
    headers: { get: () => 'application/json' },
    json: async () => ({
      id: 'player-id',
      name,
      federatedPlayers: [],
      registrations: [],
      clubs: [],
      competitions: [],
      matches: [],
      statistics: [],
    }),
  }
}

function Wrapper({ children }) {
  return <MemoryRouter><AuthProvider>{children}</AuthProvider></MemoryRouter>
}

describe('usePlayerDetails', () => {
  afterEach(() => vi.restoreAllMocks())

  it('cancels a previous filter request and ignores its late response', async () => {
    const requests = []
    vi.spyOn(globalThis, 'fetch').mockImplementation((path, options) => new Promise((resolve) => {
      requests.push({ path, options, resolve })
    }))

    const hook = renderHook(
      ({ source }) => usePlayerDetails('player-id', source, '', ''),
      { initialProps: { source: 'RFETM' }, wrapper: Wrapper },
    )

    await waitFor(() => expect(requests).toHaveLength(1))
    hook.rerender({ source: 'FCTT' })
    await waitFor(() => expect(requests).toHaveLength(2))

    expect(requests[0].options.signal.aborted).toBe(true)
    requests[0].resolve(response('stale'))
    requests[1].resolve(response('current'))

    await waitFor(() => expect(hook.result.current.data.name).toBe('current'))
    expect(hook.result.current.loading).toBe(false)
  })
})
