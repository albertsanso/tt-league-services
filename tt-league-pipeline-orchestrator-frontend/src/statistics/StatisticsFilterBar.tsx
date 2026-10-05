import RefreshIcon from '@mui/icons-material/Refresh'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import type { PipelineSource } from '../api/types'
import { SOURCES } from '../runs/runFilters'
import { rangeError } from './statisticsFilters'
import type { StatisticsFilters } from './statisticsFilters'

interface StatisticsFilterBarProps {
  readonly filters: StatisticsFilters
  /** The season in use: the chosen one, or the latest the facets offer. */
  readonly season: string | null
  readonly seasons: readonly string[]
  readonly onChange: (
    patch: Partial<Pick<StatisticsFilters, 'sources' | 'season' | 'fromDate' | 'toDate'>>,
    replace: boolean,
  ) => void
  readonly onReset: () => void
  readonly onRefresh: () => void
  readonly refreshing: boolean
}

export function StatisticsFilterBar({
  filters,
  season,
  seasons,
  onChange,
  onReset,
  onRefresh,
  refreshing,
}: StatisticsFilterBarProps) {
  const error = rangeError(filters)
  const offered = season !== null && !seasons.includes(season) ? [season, ...seasons] : seasons
  return (
    <Box role="group" aria-label="Statistics filters" sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'flex-start', mb: 2 }}>
      <TextField
        select
        size="small"
        label="Source"
        sx={{ minWidth: 160 }}
        value={filters.sources}
        slotProps={{
          select: {
            multiple: true,
            displayEmpty: true,
            renderValue: (value) => ((value as string[]).length === 0 ? 'All sources' : (value as string[]).join(', ')),
          },
          inputLabel: { shrink: true },
        }}
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
        label="Season"
        sx={{ minWidth: 160 }}
        value={season ?? ''}
        slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
        onChange={(event) => onChange({ season: event.target.value === '' ? null : event.target.value }, false)}
      >
        <MenuItem value="">No season</MenuItem>
        {offered.map((value) => (
          <MenuItem key={value} value={value}>
            {value}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        size="small"
        type="date"
        label="From"
        value={filters.fromDate}
        error={error !== null}
        slotProps={{ inputLabel: { shrink: true } }}
        onChange={(event) => onChange({ fromDate: event.target.value || filters.fromDate }, true)}
      />
      <TextField
        size="small"
        type="date"
        label="To"
        value={filters.toDate}
        error={error !== null}
        helperText={error ?? undefined}
        slotProps={{ inputLabel: { shrink: true } }}
        onChange={(event) => onChange({ toDate: event.target.value || filters.toDate }, true)}
      />
      <Button onClick={onReset} sx={{ mt: 0.5 }}>
        Reset filters
      </Button>
      <Button onClick={onRefresh} disabled={refreshing} startIcon={<RefreshIcon />} sx={{ mt: 0.5 }}>
        Refresh
      </Button>
    </Box>
  )
}
