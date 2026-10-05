import Chip from '@mui/material/Chip'
import type { RunStatus } from '../api/types'
import { STATUS_LABELS, statusColor } from './runStatus'

export function RunStatusChip({ status }: { status: RunStatus }) {
  return <Chip size="small" color={statusColor(status)} label={STATUS_LABELS[status]} />
}
