import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getUsers,
  normalizeRole,
  normalizeUser,
  normalizeUserPage,
} from './users.js'

const VALID_USER = {
  id: 'aaaaaaaa-0000-0000-0000-000000000001',
  username: 'alice',
  email: 'alice@example.com',
  createdAt: '2026-01-01T00:00:00Z',
  active: true,
  roles: ['ADMIN'],
  permissions: ['users:read', 'users:write'],
}

describe('normalizeUser', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('normalizes a valid user', () => {
    const user = normalizeUser(VALID_USER)
    expect(user.id).toBe('aaaaaaaa-0000-0000-0000-000000000001')
    expect(user.username).toBe('alice')
    expect(user.active).toBe(true)
    expect(user.roles).toEqual(['ADMIN'])
    expect(user.permissions).toContain('users:read')
  })

  it('rejects a missing id', () => {
    expect(() => normalizeUser({ ...VALID_USER, id: '' })).toThrow()
  })

  it('rejects a missing username', () => {
    expect(() => normalizeUser({ ...VALID_USER, username: '' })).toThrow()
  })

  it('rejects a missing email', () => {
    expect(() => normalizeUser({ ...VALID_USER, email: '   ' })).toThrow()
  })

  it('rejects when roles is not an array', () => {
    expect(() => normalizeUser({ ...VALID_USER, roles: 'ADMIN' })).toThrow()
  })

  it('rejects when permissions is not an array', () => {
    expect(() => normalizeUser({ ...VALID_USER, permissions: null })).toThrow()
  })

  it('rejects a null payload', () => {
    expect(() => normalizeUser(null)).toThrow()
  })

  it('sorts roles and permissions alphabetically', () => {
    const user = normalizeUser({ ...VALID_USER, roles: ['PRACTITIONER', 'ADMIN'], permissions: ['users:write', 'users:read'] })
    expect(user.roles[0]).toBe('ADMIN')
    expect(user.permissions[0]).toBe('users:read')
  })
})

describe('normalizeUserPage', () => {
  it('normalizes a valid page', () => {
    const page = normalizeUserPage({ content: [VALID_USER], totalElements: 1, totalPages: 1, page: 0, size: 20 })
    expect(page.content).toHaveLength(1)
    expect(page.totalElements).toBe(1)
  })

  it('rejects when content is not an array', () => {
    expect(() => normalizeUserPage({ content: null })).toThrow()
  })

  it('rejects a null payload', () => {
    expect(() => normalizeUserPage(null)).toThrow()
  })
})

describe('normalizeRole', () => {
  it('normalizes a valid role', () => {
    const role = normalizeRole({ name: 'ADMIN', permissions: ['users:read', 'clubs:read'] })
    expect(role.name).toBe('ADMIN')
    expect(role.permissions).toContain('users:read')
  })

  it('rejects a missing role name', () => {
    expect(() => normalizeRole({ name: '', permissions: [] })).toThrow()
  })

  it('rejects when permissions is not an array', () => {
    expect(() => normalizeRole({ name: 'ADMIN', permissions: null })).toThrow()
  })
})

describe('getUsers (fetch integration)', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('requests the user list endpoint with pagination params', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({ content: [VALID_USER], totalElements: 1, totalPages: 1, page: 0, size: 20 }),
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)

    await getUsers({ page: 0, size: 20 }, 'tok')

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/user'),
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: expect.any(String) }) }),
    )
  })

  it('includes search param when provided', async () => {
    const response = {
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => ({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 }),
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)

    await getUsers({ search: 'alice', page: 0, size: 20 }, 'tok')

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('search=alice'),
      expect.any(Object),
    )
  })

  it('calls onUnauthorized on 401', async () => {
    const response = {
      ok: false,
      status: 401,
      headers: { get: () => 'application/json' },
      json: async () => ({ message: 'Unauthorized' }),
    }
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(response)
    const onUnauthorized = vi.fn()

    await expect(getUsers({}, 'tok', undefined, onUnauthorized)).rejects.toThrow()
    expect(onUnauthorized).toHaveBeenCalled()
  })
})
