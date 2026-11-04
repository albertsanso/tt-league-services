import RefreshIcon from '@mui/icons-material/Refresh'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import type { PipelineSource, UnitOutcomesRow } from '../api/types'
import { SOURCES } from '../runs/runFilters'
import { rangeError } from './statisticsFilters'
import type { StatisticsFilters } from './statisticsFilters'

interface StatisticsFilterBarProps {
  readonly filters: StatisticsFilters
  /** The season in use: the chosen one, or the latest the facets offer. */
  readonly season: string | null
  readonly seasons: readonly string[]
  /** The units the statistics know, to choose the unit filter from. */
  readonly units: readonly Pick<UnitOutcomesRow, 'unitKey' | 'label'>[]
  readonly onChange: (
    patch: Partial<Pick<StatisticsFilters, 'sources' | 'season' | 'unitKey' | 'fromDate' | 'toDate'>>,
    replace: boolean,
  ) => void
  readonly onReset: () => void
  readonly onRefresh: () => void
  readonly refreshing: boolean
}

/** One option per unit key (the label of the first unit with that key), keeping a chosen key the data no longer has. */
function unitOptions(
  units: readonly Pick<UnitOutcomesRow, 'unitKey' | 'label'>[],
  chosen: string | null,
): readonly Pick<UnitOutcomesRow, 'unitKey' | 'label'>[] {
  const byKey = new Map<string, Pick<UnitOutcomesRow, 'unitKey' | 'label'>>()
  units.forEach((unit) => {
    if (!byKey.has(unit.unitKey)) {
      byKey.set(unit.unitKey, unit)
    }
  })
  if (chosen !== null && !byKey.has(chosen)) {
    byKey.set(chosen, { unitKey: chosen, label: chosen })
  }
  return [...byKey.values()].sort((a, b) => a.label.localeCompare(b.label))
}

export function StatisticsFilterBar({
  filters,
  season,
  seasons,
  units,
  onChange,
  onReset,
  onRefresh,
  refreshing,
}: StatisticsFilterBarProps) {
  const error = rangeError(filters)
  const offered = season !== null && !seasons.includes(season) ? [season, ...seasons] : seasons
  const offeredUnits = unitOptions(units, filters.unitKey)
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
        select
        size="small"
        label="Unit"
        sx={{ minWidth: 200 }}
        value={filters.unitKey ?? ''}
        slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
        onChange={(event) => onChange({ unitKey: event.target.value === '' ? null : event.target.value }, false)}
      >
        <MenuItem value="">All units</MenuItem>
        {offeredUnits.map((unit) => (
          <MenuItem key={unit.unitKey} value={unit.unitKey}>
            {unit.label}
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
