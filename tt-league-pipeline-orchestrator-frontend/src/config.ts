export interface ApiConfig {
  readonly orchestratorBaseUrl: string
  readonly platformBaseUrl: string
}

export function parseBaseUrl(name: string, value: string | undefined): string {
  const trimmed = (value ?? '').trim()
  if (trimmed === '') {
    return ''
  }
  let url: URL
  try {
    url = new URL(trimmed)
  } catch {
    throw new Error(`${name} must be an absolute http(s) URL, got "${trimmed}"`)
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error(`${name} must be an absolute http(s) URL, got "${trimmed}"`)
  }
  return trimmed.replace(/\/+$/, '')
}

export function readApiConfig(env: {
  VITE_ORCHESTRATOR_BASE_URL?: string
  VITE_PLATFORM_BASE_URL?: string
}): ApiConfig {
  return {
    orchestratorBaseUrl: parseBaseUrl('VITE_ORCHESTRATOR_BASE_URL', env.VITE_ORCHESTRATOR_BASE_URL),
    platformBaseUrl: parseBaseUrl('VITE_PLATFORM_BASE_URL', env.VITE_PLATFORM_BASE_URL),
  }
}

export const apiConfig: ApiConfig = readApiConfig(import.meta.env)
