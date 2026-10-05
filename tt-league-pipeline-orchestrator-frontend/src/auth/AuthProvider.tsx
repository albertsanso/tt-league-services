import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '../api/ApiError'
import { currentUser, login, logout } from '../api/auth'
import { createHttpClient } from '../api/client'
import { apiConfig } from '../config'
import { AuthContext } from './authContext'
import type { AuthContextValue, AuthUser, SignOutReason } from './authContext'
import { decodeClaims } from './claims'
import { clearToken, readToken, writeToken } from './tokenStorage'

const MAX_TIMEOUT_MS = 2 ** 31 - 1

interface Session {
  readonly status: AuthContextValue['status']
  readonly token?: string
  readonly user?: AuthUser
  readonly signOutReason?: SignOutReason
  readonly notice?: string
}

function toUser(token: string): AuthUser | null {
  const claims = decodeClaims(token)
  if (claims === null || claims.expiresAt <= Date.now()) {
    return null
  }
  return {
    username: claims.subject,
    roles: claims.roles,
    permissions: claims.permissions,
    expiresAt: claims.expiresAt,
  }
}

function platformClient(token: string | null) {
  return createHttpClient({ baseUrl: apiConfig.platformBaseUrl, getToken: () => token })
}

/** Decides the starting session from the stored token, without any network call. */
function initialSession(): Session {
  const stored = readToken()
  if (stored === null) {
    return { status: 'signed-out' }
  }
  if (toUser(stored) === null) {
    clearToken()
    return { status: 'signed-out', signOutReason: 'expired' }
  }
  return { status: 'restoring', token: stored }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>(initialSession)
  const tokenRef = useRef<string | null>(null)

  // A stored, unexpired token is validated once so a token revoked by a platform logout is not reused.
  const restoringToken = session.status === 'restoring' ? session.token : undefined
  useEffect(() => {
    if (restoringToken === undefined) {
      return
    }
    let cancelled = false
    const controller = new AbortController()
    currentUser(platformClient(restoringToken), controller.signal).then(
      () => {
        if (cancelled) {
          return
        }
        const user = toUser(restoringToken)
        if (user === null) {
          clearToken()
          setSession({ status: 'signed-out', signOutReason: 'expired' })
          return
        }
        tokenRef.current = restoringToken
        setSession({ status: 'signed-in', token: restoringToken, user })
      },
      (error: unknown) => {
        if (cancelled) {
          return
        }
        clearToken()
        if (error instanceof ApiError && error.status === 401) {
          setSession({ status: 'signed-out', signOutReason: 'expired' })
        } else {
          const message = error instanceof Error ? error.message : 'Unknown error'
          setSession({ status: 'signed-out', notice: `Could not restore your session: ${message}` })
        }
      },
    )
    return () => {
      cancelled = true
      controller.abort()
    }
  }, [restoringToken])

  const signOut = useCallback((reason: SignOutReason = 'user') => {
    const token = tokenRef.current
    tokenRef.current = null
    clearToken()
    setSession({ status: 'signed-out', signOutReason: reason })
    if (reason === 'user' && token !== null && toUser(token) !== null) {
      // Local sign-out has already happened; a failed revocation call must not block it.
      logout(platformClient(token)).catch((error: unknown) => {
        console.warn('Platform logout failed', error instanceof ApiError ? error.status : 'unknown')
      })
    }
  }, [])

  const getToken = useCallback(() => tokenRef.current, [])

  const signIn = useCallback(async (username: string, password: string) => {
    const response = await login(platformClient(null), username, password)
    const user = typeof response?.token === 'string' ? toUser(response.token) : null
    if (user === null) {
      throw new Error('The sign-in response was invalid')
    }
    tokenRef.current = response.token
    writeToken(response.token)
    setSession({ status: 'signed-in', token: response.token, user })
  }, [])

  const expiresAt = session.user?.expiresAt
  useEffect(() => {
    if (expiresAt === undefined) {
      return
    }
    let timer: ReturnType<typeof setTimeout>
    const arm = () => {
      const remaining = expiresAt - Date.now()
      if (remaining <= 0) {
        signOut('expired')
        return
      }
      timer = setTimeout(arm, Math.min(remaining, MAX_TIMEOUT_MS))
    }
    arm()
    return () => clearTimeout(timer)
  }, [expiresAt, signOut])

  const value = useMemo<AuthContextValue>(() => {
    const { token, ...rest } = session
    return { ...rest, token: session.status === 'signed-in' ? token : undefined, signIn, signOut, getToken }
  }, [session, signIn, signOut, getToken])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
