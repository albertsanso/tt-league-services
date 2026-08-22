import { useContext } from 'react'
import { authContext } from './authContext.js'

export function useAuth() {
  const value = useContext(authContext)
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return value
}
