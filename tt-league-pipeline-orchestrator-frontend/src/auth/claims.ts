export interface TokenClaims {
  readonly subject: string
  readonly roles: readonly string[]
  readonly permissions: readonly string[]
  /** Epoch milliseconds. */
  readonly expiresAt: number
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

/**
 * Decodes the JWT payload for display, permission gating and the expiry timer. The signature is not verified here;
 * the backends validate every token.
 */
export function decodeClaims(token: string): TokenClaims | null {
  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
    const payload: unknown = JSON.parse(new TextDecoder().decode(bytes))
    if (typeof payload !== 'object' || payload === null) {
      return null
    }
    const record = payload as Record<string, unknown>
    if (typeof record.exp !== 'number' || !Number.isFinite(record.exp)) {
      return null
    }
    return {
      subject: typeof record.sub === 'string' ? record.sub : '',
      roles: stringArray(record.roles),
      permissions: stringArray(record.permissions),
      expiresAt: record.exp * 1000,
    }
  } catch {
    return null
  }
}
