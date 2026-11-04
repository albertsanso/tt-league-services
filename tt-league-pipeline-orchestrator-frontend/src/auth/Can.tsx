import Tooltip from '@mui/material/Tooltip'
import { cloneElement } from 'react'
import type { ReactElement } from 'react'
import { requirementText, useCan } from './permissions'
import type { Capability } from './permissions'

interface CanProps {
  readonly capability: Capability
  readonly mode: 'hide' | 'disable'
  readonly children: ReactElement<{ disabled?: boolean }>
}

export function Can({ capability, mode, children }: CanProps) {
  const allowed = useCan(capability)
  if (allowed) {
    return children
  }
  if (mode === 'hide') {
    return null
  }
  return (
    <Tooltip title={requirementText(capability)}>
      <span>{cloneElement(children, { disabled: true })}</span>
    </Tooltip>
  )
}
