import { makeToken } from '../test/jwt'
import { decodeClaims } from './claims'
import { clearToken, readToken, writeToken } from './tokenStorage'

describe('decodeClaims', () => {
  it('decodes subject, roles, permissions and expiry', () => {
    const claims = decodeClaims(makeToken({ sub: 'ana', roles: ['ADMIN'], permissions: ['matches:write'], exp: 2000 }))
    expect(claims).toEqual({ subject: 'ana', roles: ['ADMIN'], permissions: ['matches:write'], expiresAt: 2_000_000 })
  })

  it('handles payloads of every base64 padding length', () => {
    for (const sub of ['a', 'ab', 'abc', 'abcd']) {
      expect(decodeClaims(makeToken({ sub }))?.subject).toBe(sub)
    }
  })

  it('decodes a non-ASCII subject', () => {
    expect(decodeClaims(makeToken({ sub: 'Josep Mª Pagès ✓' }))?.subject).toBe('Josep Mª Pagès ✓')
  })

  it('returns null for malformed tokens', () => {
    expect(decodeClaims('abc')).toBeNull()
    expect(decodeClaims('a.b.c')).toBeNull()
    expect(decodeClaims('a.b')).toBeNull()
  })

  it('returns null without a numeric exp', () => {
    expect(decodeClaims(makeToken({ exp: undefined }))).toBeNull()
    expect(decodeClaims(makeToken({ exp: 'soon' as unknown as number }))).toBeNull()
  })

  it('treats non-array roles and permissions as empty', () => {
    const claims = decodeClaims(makeToken({ roles: 'ADMIN', permissions: { a: 1 } }))
    expect(claims?.roles).toEqual([])
    expect(claims?.permissions).toEqual([])
  })
})

describe('tokenStorage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    clearToken()
  })

  it('stores, reads and clears the token in sessionStorage', () => {
    writeToken('abc')
    expect(window.sessionStorage.getItem('tt-league.pipeline.auth-token')).toBe('abc')
    expect(window.localStorage.length).toBe(0)
    expect(readToken()).toBe('abc')
    clearToken()
    expect(readToken()).toBeNull()
  })

  it('degrades to memory when storage throws', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    writeToken('mem')
    expect(readToken()).toBe('mem')
    clearToken()
    expect(readToken()).toBeNull()
  })
})
