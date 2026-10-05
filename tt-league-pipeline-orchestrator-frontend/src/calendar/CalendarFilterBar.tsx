import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import type { MatchDayFacets, MatchDayState, PipelineSource } from '../api/types'
import { SOURCES } from '../runs/runFilters'
import { hasDataFilters } from './calendarFilters'
import type { CalendarFilterValues } from './calendarFilters'
import { STATE_LABELS } from './completion'

type DataFilters = Pick<CalendarFilterValues, 'source' | 'season' | 'competition' | 'phase' | 'state'>

interface CalendarFilterBarProps {
  readonly filters: CalendarFilterValues
  readonly facets: MatchDayFacets | null
  readonly onChange: (patch: Partial<DataFilters>) => void
  readonly onClear: () => void
}

const ALL = ''
const STATES: readonly MatchDayState[] = ['UPCOMING', 'OPEN', 'CLOSED']

/** The offered values plus the selected one, which can be missing from the offer (for example from a shared link). */
function options(offered: readonly string[] | undefined, selected: string | null): readonly string[] {
  const values = offered ?? []
  return selected !== null && !values.includes(selected) ? [selected, ...values] : values
}

export function CalendarFilterBar({ filters, facets, onChange, onClear }: CalendarFilterBarProps) {
  const field = {
    select: true,
    size: 'small',
    sx: { minWidth: 160 },
    slotProps: { select: { displayEmpty: true }, inputLabel: { shrink: true } },
  } as const
  return (
    <Box role="group" aria-label="Calendar filters" sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', mb: 2 }}>
      <TextField
        {...field}
        label="Source"
        value={filters.source ?? ALL}
        onChange={(event) =>
          onChange({ source: event.target.value === ALL ? null : (event.target.value as PipelineSource), competition: null, phase: null })
        }
      >
        <MenuItem value={ALL}>All sources</MenuItem>
        {SOURCES.map((source) => (
          <MenuItem key={source} value={source}>
            {source}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        {...field}
        label="Season"
        value={filters.season ?? ALL}
        onChange={(event) =>
          onChange({ season: event.target.value === ALL ? null : event.target.value, competition: null, phase: null })
        }
      >
        <MenuItem value={ALL}>All seasons</MenuItem>
        {options(facets?.seasons, filters.season).map((season) => (
          <MenuItem key={season} value={season}>
            {season}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        {...field}
        label="Category"
        value={filters.competition ?? ALL}
        onChange={(event) => onChange({ competition: event.target.value === ALL ? null : event.target.value })}
      >
        <MenuItem value={ALL}>All categories</MenuItem>
        {options(facets?.competitions, filters.competition).map((competition) => (
          <MenuItem key={competition} value={competition}>
            {competition}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        {...field}
        label="Phase"
        value={filters.phase ?? ALL}
        onChange={(event) => onChange({ phase: event.target.value === ALL ? null : event.target.value })}
      >
        <MenuItem value={ALL}>All phases</MenuItem>
        {options(facets?.phases, filters.phase).map((phase) => (
          <MenuItem key={phase} value={phase}>
            {phase}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        {...field}
        label="State"
        value={filters.state ?? ALL}
        onChange={(event) => onChange({ state: event.target.value === ALL ? null : (event.target.value as MatchDayState) })}
      >
        <MenuItem value={ALL}>All states</MenuItem>
        {STATES.map((state) => (
          <MenuItem key={state} value={state}>
            {STATE_LABELS[state]}
          </MenuItem>
        ))}
      </TextField>
      <Button size="small" onClick={onClear} disabled={!hasDataFilters(filters)}>
        Clear filters
      </Button>
    </Box>
  )
}
