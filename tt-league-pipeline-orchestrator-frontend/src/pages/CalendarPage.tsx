import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import LinearProgress from '@mui/material/LinearProgress'
import Typography from '@mui/material/Typography'
import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { todayIso } from '../calendar/calendarDates'
import { hasDataFilters, parseCalendarFilters, serializeCalendarFilters } from '../calendar/calendarFilters'
import type { CalendarFilterValues } from '../calendar/calendarFilters'
import { CalendarFilterBar } from '../calendar/CalendarFilterBar'
import { CalendarToolbar } from '../calendar/CalendarToolbar'
import { CompletionLegend } from '../calendar/CompletionLegend'
import { MatchDayCalendar } from '../calendar/MatchDayCalendar'
import { UndatedMatchDays } from '../calendar/UndatedMatchDays'
import { useMatchDayCalendar } from '../calendar/useMatchDayCalendar'
import { useMatchDayFacets } from '../calendar/useMatchDayFacets'

const NO_DATA_FILTERS = { source: null, season: null, competition: null, phase: null, state: null } as const

export default function CalendarPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const today = todayIso()
  const parsed = useMemo(() => parseCalendarFilters(searchParams, today), [searchParams, today])
  const { errors, ...filters } = parsed
  const { days, undated, loading, error, truncated, refetch } = useMatchDayCalendar(filters)
  const { facets, error: facetsError } = useMatchDayFacets(filters.source, filters.season)

  const write = (next: CalendarFilterValues) => setSearchParams(serializeCalendarFilters(next), { replace: true })
  const clearFilters = () => write({ ...filters, ...NO_DATA_FILTERS })
  const empty = !loading && error === null && days.length === 0

  return (
    <>
      <Typography variant="h5" component="h2" gutterBottom>
        Calendar
      </Typography>

      {errors.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {errors.map((message) => (
            <div key={message}>{message}</div>
          ))}
        </Alert>
      )}

      <CalendarToolbar view={filters.view} date={filters.date} onChange={(patch) => write({ ...filters, ...patch })} />
      <CalendarFilterBar
        filters={filters}
        facets={facets}
        onChange={(patch) => write({ ...filters, ...patch })}
        onClear={clearFilters}
      />
      {facetsError !== null && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {`The filter options could not be loaded: ${facetsError}`}
        </Alert>
      )}
      <CompletionLegend />

      {error !== null && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={refetch}>
              Retry
            </Button>
          }
        >
          {error}
        </Alert>
      )}
      {truncated && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          More than 2000 match days — narrow the filters
        </Alert>
      )}
      {loading && <LinearProgress aria-label="Loading match days" sx={{ mb: 1 }} />}

      <MatchDayCalendar view={filters.view} date={filters.date} today={today} matchDays={days} />

      {empty && (
        <Box sx={{ mt: 2 }}>
          <Typography color="text.secondary">No tracked match days in this period.</Typography>
          {hasDataFilters(filters) && (
            <Button size="small" onClick={clearFilters}>
              Clear filters
            </Button>
          )}
        </Box>
      )}

      <UndatedMatchDays matchDays={undated} />
    </>
  )
}
