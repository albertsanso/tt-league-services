import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from './client.js'

describe('API client upload progress', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('reports multipart upload progress without overriding the browser boundary', async () => {
    const progressListeners = {}
    const requestListeners = {}
    const request = {
      upload: { addEventListener: (type, callback) => { progressListeners[type] = callback } },
      open: vi.fn(),
      setRequestHeader: vi.fn(),
      addEventListener: (type, callback) => { requestListeners[type] = callback },
      getResponseHeader: vi.fn(() => 'application/json'),
      status: 202,
      responseText: '{"message":"accepted"}',
      send: vi.fn(() => {
        progressListeners.progress({ lengthComputable: true, loaded: 50, total: 100 })
        requestListeners.load()
      }),
      abort: vi.fn(),
    }
    vi.stubGlobal('XMLHttpRequest', class {
      constructor() {
        return request
      }
    })
    const body = new FormData()
    body.append('file', new File(['data'], 'season.zip'))
    const onProgress = vi.fn()

    await apiRequest('/api/v1/administration/import/upload', {
      token: 'session-token',
      method: 'POST',
      body,
      onUploadProgress: onProgress,
    })

    expect(request.open).toHaveBeenCalledWith('POST', '/api/v1/administration/import/upload')
    expect(request.setRequestHeader).toHaveBeenCalledWith('Authorization', 'Bearer session-token')
    expect(request.setRequestHeader).toHaveBeenCalledWith('Accept', 'application/json')
    expect(request.setRequestHeader).not.toHaveBeenCalledWith('Content-Type', expect.anything())
    expect(request.send).toHaveBeenCalledWith(body)
    expect(onProgress).toHaveBeenCalledWith(50)
    expect(onProgress).toHaveBeenLastCalledWith(100)
  })
})
