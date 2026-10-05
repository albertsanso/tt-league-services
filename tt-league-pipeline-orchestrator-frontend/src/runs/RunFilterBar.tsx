import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import ListSubheader from '@mui/material/ListSubheader'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import type { PipelineSource, RunStatus } from '../api/types'
import { hasActiveFilters, hasRangeError, SOURCES } from './runFilters'
import type { RunFilters } from './runFilters'
import { ACTIVE_STATUSES, STATUS_LABELS, TERMINAL_STATUSES } from './runStatus'

interface RunFilterBarProps {
  readonly filters: RunFilters
  /** `replace` is true while typing a date, so the history does not fill with partial values. */
  readonly onChange: (next: Partial<Pick<RunFilters, 'sources' | 'statuses' | 'fromDate' | 'toDate'>>, replace: boolean) => void
  readonly onClear: () => void
}

export function RunFilterBar({ filters, onChange, onClear }: RunFilterBarProps) {
  const rangeError = hasRangeError(filters)
  return (
    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'flex-start', mb: 2 }}>
      <TextField
        select
        size="small"
        label="Source"
        sx={{ minWidth: 160 }}
        value={filters.sources}
        slotProps={{ select: { multiple: true, renderValue: (value) => (value as string[]).join(', ') } }}
        onChange={(event) => onChange({ sources: event.target.value as unknown as PipelineSource[] }, false)}
      >
        {SOURCES.map((source) => (
          <MenuItem key={source} value={source}>
            {source}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        size="small"
        label="Status"
        sx={{ minWidth: 200 }}
        value={filters.statuses}
        slotProps={{
          select: {
            multiple: true,
            renderValue: (value) => (value as RunStatus[]).map((status) => STATUS_LABELS[status]).join(', '),
          },
        }}
        onChange={(event) => onChange({ statuses: event.target.value as unknown as RunStatus[] }, false)}
      >
        <ListSubheader>Active</ListSubheader>
        {ACTIVE_STATUSES.map((status) => (
          <MenuItem key={status} value={status}>
            {STATUS_LABELS[status]}
          </MenuItem>
        ))}
        <ListSubheader>Finished</ListSubheader>
        {TERMINAL_STATUSES.map((status) => (
          <MenuItem key={status} value={status}>
            {STATUS_LABELS[status]}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        size="small"
        type="date"
        label="From"
        value={filters.fromDate ?? ''}
        error={rangeError}
        slotProps={{ inputLabel: { shrink: true } }}
        onChange={(event) => onChange({ fromDate: event.target.value || null }, true)}
      />
      <TextField
        size="small"
        type="date"
        label="To"
        value={filters.toDate ?? ''}
        error={rangeError}
        helperText={rangeError ? '"From" must not be after "To"' : undefined}
        slotProps={{ inputLabel: { shrink: true } }}
        onChange={(event) => onChange({ toDate: event.target.value || null }, true)}
      />
      <Button onClick={onClear} disabled={!hasActiveFilters(filters)} sx={{ mt: 0.5 }}>
        Clear filters
      </Button>
    </Box>
  )
}
