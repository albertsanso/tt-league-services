import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react'
import { useNavigate } from 'react-router-dom'
import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
  requestPasswordRecovery,
  resetPassword,
} from '../api/auth.js'
import { ApiError } from '../api/client.js'
import { authContext } from './authContext.js'
import { AUTH_TOKEN_KEY } from './authStorage.js'

function getStoredToken() {
  return window.sessionStorage.getItem(AUTH_TOKEN_KEY)
}

export function AuthProvider({ children }) {
  const navigate = useNavigate()
  const [token, setToken] = useState(getStoredToken)
  const [status, setStatus] = useState(() => (getStoredToken() ? 'loading' : 'anonymous'))
  const [user, setUser] = useState(null)
  const [error, setError] = useState(null)

  const clearSession = useCallback(() => {
    window.sessionStorage.removeItem(AUTH_TOKEN_KEY)
    setToken(null)
    setUser(null)
    setStatus('anonymous')
  }, [])

  const loadUser = useCallback(async (sessionToken, signal) => {
    try {
      const currentUser = await getCurrentUser(
        sessionToken,
        signal,
        clearSession,
      )
      setUser(currentUser)
      setToken(sessionToken)
      setStatus('authenticated')
      setError(null)
      return currentUser
    } catch (requestError) {
      if (requestError.name === 'AbortError') {
        throw requestError
      }
      clearSession()
      setError(requestError)
      throw requestError
    }
  }, [clearSession])

  useEffect(() => {
    const storedToken = getStoredToken()
    if (!storedToken) {
      return undefined
    }

    const controller = new AbortController()
    queueMicrotask(() => {
      loadUser(storedToken, controller.signal).catch((requestError) => {
        if (requestError.name !== 'AbortError') {
          return requestError
        }
        return undefined
      })
    })

    return () => controller.abort()
  }, [loadUser])

  const login = useCallback(async (credentials) => {
    const response = await loginRequest(credentials)
    if (!response?.token) {
      throw new ApiError('La resposta d’autenticació no és vàlida.', 502, response)
    }

    window.sessionStorage.setItem(AUTH_TOKEN_KEY, response.token)
    setToken(response.token)
    setStatus('loading')
    return loadUser(response.token)
  }, [loadUser])

  const register = useCallback((details, signal) => registerRequest(details, signal), [])
  const recoverPassword = useCallback(
    (email, signal) => requestPasswordRecovery(email, signal),
    [],
  )
  const changePassword = useCallback(
    (details, signal) => resetPassword(details, signal),
    [],
  )

  const logout = useCallback(async () => {
    const currentToken = token
    try {
      if (currentToken) {
        await logoutRequest(currentToken)
      }
    } finally {
      clearSession()
      navigate('/login', { replace: true })
    }
  }, [clearSession, navigate, token])

  const hasRole = useCallback(
    (role) => user?.roles?.includes(role) ?? false,
    [user],
  )
  const hasPermission = useCallback(
    (permission) => user?.permissions?.includes(permission) ?? false,
    [user],
  )

  const value = useMemo(
    () => ({
      token,
      user,
      status,
      loading: status === 'loading',
      authenticated: status === 'authenticated',
      anonymous: status === 'anonymous',
      error,
      login,
      register,
      recoverPassword,
      changePassword,
      logout,
      clearSession,
      hasRole,
      hasPermission,
    }),
    [
      token,
      user,
      status,
      error,
      login,
      register,
      recoverPassword,
      changePassword,
      logout,
      clearSession,
      hasRole,
      hasPermission,
    ],
  )

  return <authContext.Provider value={value}>{children}</authContext.Provider>
}
