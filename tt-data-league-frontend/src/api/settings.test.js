import { describe, expect, it, vi } from 'vitest'
import { getSettings, normalizeSetting } from './settings.js'

describe('system settings API', () => {
  it('normalizes server setting metadata', () => {
    expect(normalizeSetting({ key: 'ui.theme', category: 'UI', type: 'STRING', version: 0 }).allowedValues)
      .toEqual([])
  })

  it('rejects malformed setting responses', () => {
    expect(() => normalizeSetting({ key: 'ui.theme' })).toThrow()
  })

  it('passes category and search filters', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      headers: { get: () => 'application/json' },
      json: async () => [],
    })
    await getSettings({ category: 'UI', search: 'theme' }, 'token')
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/administration/settings?category=UI&search=theme',
      expect.any(Object),
    )
    vi.restoreAllMocks()
  })
})
