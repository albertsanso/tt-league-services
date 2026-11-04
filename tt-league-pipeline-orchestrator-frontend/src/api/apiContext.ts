import { createContext } from 'react'
import type { Api } from './bindApi'

export const ApiContext = createContext<Api | null>(null)
