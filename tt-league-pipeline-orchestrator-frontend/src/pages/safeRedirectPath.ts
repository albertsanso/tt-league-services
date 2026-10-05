const DEFAULT_LANDING = '/runs'

/** Only same-app absolute paths are followed after sign-in. */
export function safeRedirectPath(from: unknown): string {
  if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') && !from.startsWith('/login')) {
    return from
  }
  return DEFAULT_LANDING
}
