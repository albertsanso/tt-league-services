import { describe, expect, it, vi } from 'vitest'
import { getSettings, normalizeSetting } from './settings.js'

describe('system settings API', () => {
  it('normalizes server setting metadata', () => {
    expect(normalizeSetting({ name: 'general.timezone', category: 'GENERAL', type: 'STRING', version: 0 }).allowedValues)
      .toEqual([])
  })

  it('rejects malformed setting responses', () => {
    expect(() => normalizeSetting({ name: 'general.timezone' })).toThrow()
  })

  it('passes category and search filters', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => [],
    })
    await getSettings({ category: 'GENERAL', search: 'timezone' }, 'token')
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/administration/settings?category=GENERAL&search=timezone',
      expect.any(Object),
    )
    vi.restoreAllMocks()
  })
})
