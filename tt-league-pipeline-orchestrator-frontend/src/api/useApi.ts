import { useContext } from 'react'
import { ApiContext } from './apiContext'
import type { Api } from './bindApi'

export function useApi(): Api {
  const value = useContext(ApiContext)
  if (value === null) {
    throw new Error('useApi must be used inside an ApiProvider')
  }
  return value
}
