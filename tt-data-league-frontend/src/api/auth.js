import { apiRequest } from './client.js'

export function login(credentials, signal) {
  return apiRequest('/api/v1/auth/login', {
    method: 'POST',
    body: credentials,
    signal,
  })
}

export function register(details, signal) {
  return apiRequest('/api/v1/auth/register', {
    method: 'POST',
    body: details,
    signal,
  })
}

export function getCurrentUser(token, signal, onUnauthorized) {
  return apiRequest('/api/v1/auth/me', {
    token,
    signal,
    onUnauthorized,
  })
}

export function logout(token, signal) {
  return apiRequest('/api/v1/auth/logout', {
    method: 'POST',
    token,
    signal,
  })
}

export function requestPasswordRecovery(email, signal) {
  return apiRequest('/api/v1/auth/password/forgot', {
    method: 'POST',
    body: { email },
    signal,
  })
}

export function resetPassword(details, signal) {
  return apiRequest('/api/v1/auth/password/reset', {
    method: 'POST',
    body: details,
    signal,
  })
}
