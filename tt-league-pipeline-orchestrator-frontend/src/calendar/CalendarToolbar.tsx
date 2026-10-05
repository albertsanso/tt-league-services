import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import { periodTitle, shift, todayIso } from './calendarDates'
import type { CalendarView } from './calendarDates'

interface CalendarToolbarProps {
  readonly view: CalendarView
  readonly date: string
  readonly onChange: (patch: { readonly view?: CalendarView; readonly date?: string }) => void
}

export function CalendarToolbar({ view, date, onChange }: CalendarToolbarProps) {
  const unit = view === 'month' ? 'month' : 'week'
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', mb: 2 }}>
      <IconButton aria-label={`Previous ${unit}`} onClick={() => onChange({ date: shift(view, date, -1) })}>
        <ChevronLeftIcon />
      </IconButton>
      <Button variant="outlined" size="small" onClick={() => onChange({ date: todayIso() })}>
        Today
      </Button>
      <IconButton aria-label={`Next ${unit}`} onClick={() => onChange({ date: shift(view, date, 1) })}>
        <ChevronRightIcon />
      </IconButton>
      <Typography variant="h6" component="h3" aria-live="polite" sx={{ flexGrow: 1, minWidth: 180 }}>
        {periodTitle(view, date)}
      </Typography>
      <ToggleButtonGroup
        exclusive
        size="small"
        value={view}
        aria-label="Calendar view"
        onChange={(_event, next: CalendarView | null) => {
          if (next !== null) {
            onChange({ view: next })
          }
        }}
      >
        <ToggleButton value="month">Month</ToggleButton>
        <ToggleButton value="week">Week</ToggleButton>
      </ToggleButtonGroup>
    </Box>
  )
}
