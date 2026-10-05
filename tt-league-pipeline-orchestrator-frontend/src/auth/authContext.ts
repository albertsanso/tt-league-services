import { createContext } from 'react'

export type SignOutReason = 'expired' | 'user'

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
