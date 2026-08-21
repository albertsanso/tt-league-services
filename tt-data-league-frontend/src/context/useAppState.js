import { useContext } from 'react'
import { appStateContext } from './appStateContext.js'

export function useAppState() {
  const context = useContext(appStateContext)

  if (!context) {
    throw new Error('useAppState ha d\'utilitzar-se dins d\'AppStateProvider')
  }

  return context
}
