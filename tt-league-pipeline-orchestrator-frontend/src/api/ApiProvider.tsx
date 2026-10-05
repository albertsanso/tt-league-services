import { useMemo } from 'react'
import type { ReactNode } from 'react'
import { useAuth } from '../auth/useAuth'
import { apiConfig } from '../config'
import { ApiContext } from './apiContext'
import { bindApi } from './bindApi'
import { createHttpClient } from './client'

export function ApiProvider({ children }: { children: ReactNode }) {
  const { getToken, signOut } = useAuth()

  const api = useMemo(
    () =>
      bindApi(
        createHttpClient({
          baseUrl: apiConfig.orchestratorBaseUrl,
          getToken,
          onUnauthorized: () => signOut('expired'),
        }),
      ),
    [getToken, signOut],
  )

  return <ApiContext.Provider value={api}>{children}</ApiContext.Provider>
}
