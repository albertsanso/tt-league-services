import { describe, expect, it } from 'vitest'
import { isRouteActive } from './routeMatching.js'

describe('Sidebar route matching', () => {
  it('keeps the Club item active on nested routes', () => {
    expect(isRouteActive('/clubs/club-id/edit', '/clubs')).toBe(true)
    expect(isRouteActive('/clubs/club-id/competition/2024/Preferent', '/clubs')).toBe(true)
  })

  it('does not activate similarly prefixed routes', () => {
    expect(isRouteActive('/clubs-other', '/clubs')).toBe(false)
  })
})
