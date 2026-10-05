const KEY = 'tt-league.pipeline.auth-token'

// Falls back to memory when sessionStorage is blocked or throws.
let memoryToken: string | null = null

export function readToken(): string | null {
  try {
    const stored = window.sessionStorage.getItem(KEY)
    if (stored !== null) {
      return stored
    }
  } catch {
    // storage unavailable: use memory only
  }
  return memoryToken
}

export function writeToken(token: string): void {
  memoryToken = token
  try {
    window.sessionStorage.setItem(KEY, token)
  } catch {
    // storage unavailable: token stays in memory only
  }
}

export function clearToken(): void {
  memoryToken = null
  try {
    window.sessionStorage.removeItem(KEY)
  } catch {
    // storage unavailable: memory already cleared
  }
}
