function base64Url(text: string): string {
  const bytes = new TextEncoder().encode(text)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export interface TestClaims {
  sub?: string
  roles?: unknown
  permissions?: unknown
  exp?: number
}

/** Builds an unsigned JWT-shaped string for tests. */
export function makeToken(claims: TestClaims = {}): string {
  const payload = {
    sub: 'operator',
    roles: [],
    permissions: [],
    exp: Math.floor(Date.now() / 1000) + 3600,
    ...claims,
  }
  return `${base64Url('{"alg":"HS256"}')}.${base64Url(JSON.stringify(payload))}.signature`
}
