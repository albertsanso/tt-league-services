import { createContext } from 'react'

/**
 * Why the session ended. `rejected` is a server 401 for a token that is still valid locally (revoked, or signed
 * with a secret the server does not trust); a 401 for a token past its expiry resolves to `expired`.
 */
export type SignOutReason = 'expired' | 'rejected' | 'user'

export interface AuthUser {
  readonly username: string
  readonly roles: readonly string[]
  readonly permissions: readonly string[]
  /** Epoch milliseconds. */
  readonly expiresAt: number
}

export interface AuthContextValue {
  readonly status: 'restoring' | 'signed-out' | 'signed-in'
  readonly user?: AuthUser
  readonly token?: string
  readonly signOutReason?: SignOutReason
  /** Visible explanation when a stored session could not be restored. */
  readonly notice?: string
  signIn(username: string, password: string): Promise<void>
  signOut(reason?: SignOutReason): void
  /** Current token for building API clients; stable across renders. */
  getToken(): string | null
}

export const AuthContext = createContext<AuthContextValue | null>(null)
