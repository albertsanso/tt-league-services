import { readApiConfig } from './config'

describe('readApiConfig', () => {
  it('defaults to the same origin', () => {
    expect(readApiConfig({})).toEqual({ orchestratorBaseUrl: '', platformBaseUrl: '' })
  })

  it('accepts absolute http(s) URLs and strips trailing slashes', () => {
    expect(
      readApiConfig({
        VITE_ORCHESTRATOR_BASE_URL: 'https://pipeline.example.org/',
        VITE_PLATFORM_BASE_URL: 'http://localhost:8080',
      }),
    ).toEqual({ orchestratorBaseUrl: 'https://pipeline.example.org', platformBaseUrl: 'http://localhost:8080' })
  })

  it('fails with the variable name for an invalid value', () => {
    expect(() => readApiConfig({ VITE_ORCHESTRATOR_BASE_URL: '/relative' })).toThrow(/VITE_ORCHESTRATOR_BASE_URL/)
    expect(() => readApiConfig({ VITE_PLATFORM_BASE_URL: 'ftp://host' })).toThrow(/VITE_PLATFORM_BASE_URL/)
  })
})
