import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import type { UnitStatus } from '../api/types'
import { isRunningUnitStatus, UNIT_STATUS_LABELS, unitStatusColor } from './runStatus'

export function UnitStatusChip({ status }: { status: UnitStatus }) {
  return (
    <Chip
      size="small"
      color={unitStatusColor(status)}
      variant={status === 'PENDING' || status === 'SKIPPED' ? 'outlined' : 'filled'}
      label={UNIT_STATUS_LABELS[status]}
      icon={isRunningUnitStatus(status) ? <CircularProgress size={12} color="inherit" /> : undefined}
    />
  )
}
