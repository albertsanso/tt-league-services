import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client.js'
import { uploadImportFile } from './importJobs.js'

vi.mock('./client.js', () => ({
  apiRequest: vi.fn(),
}))

describe('import upload API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiRequest.mockResolvedValue({ message: 'accepted' })
  })

  it('builds an authenticated multipart request with the backend field name', async () => {
    const file = new File(['zip'], 'season.zip', { type: 'application/zip' })
    const onProgress = vi.fn()
    const onUnauthorized = vi.fn()

    await uploadImportFile('session-token', file, onProgress, onUnauthorized)

    expect(apiRequest).toHaveBeenCalledWith(
      '/api/v1/administration/import/upload',
      expect.objectContaining({
        token: 'session-token',
        method: 'POST',
        onUploadProgress: onProgress,
        onUnauthorized,
      }),
    )
    const request = apiRequest.mock.calls[0][1]
    expect(request.body).toBeInstanceOf(FormData)
    expect(request.body.get('file')).toBe(file)
    expect(request.body.has('source')).toBe(false)
    expect(request.body.has('season')).toBe(false)
  })
})
