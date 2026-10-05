import type { HttpClient } from './client'
import type { CurrentUser, LoginResponse } from './types'

// These calls go to the platform API (a client built on the platform base URL).

export function login(client: HttpClient, username: string, password: string): Promise<LoginResponse> {
  return client.request<LoginResponse>('POST', '/api/v1/auth/login', { body: { username, password } })
}

export function currentUser(client: HttpClient, signal?: AbortSignal): Promise<CurrentUser> {
  return client.request<CurrentUser>('GET', '/api/v1/auth/me', { signal })
}

export function logout(client: HttpClient): Promise<void> {
  return client.request<void>('POST', '/api/v1/auth/logout')
}
