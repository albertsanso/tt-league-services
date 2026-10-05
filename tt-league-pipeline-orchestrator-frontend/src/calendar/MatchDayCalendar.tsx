import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Typography from '@mui/material/Typography'
import { useMemo, useState } from 'react'
import type { MatchDaySummary } from '../api/types'
import { dayLabel, dayOfMonth, monthGrid, sameMonth, weekDays, WEEKDAY_NAMES } from './calendarDates'
import type { CalendarView } from './calendarDates'
import { placeEntries } from './calendarFilters'
import { MatchDayEntry } from './MatchDayEntry'

const MAX_ENTRIES_PER_CELL = 4

interface MatchDayCalendarProps {
  readonly view: CalendarView
  readonly date: string
  readonly today: string
  readonly matchDays: readonly MatchDaySummary[]
}

/** Month grid (up to four entries per day, then "+N more") or week columns (every entry). */
export function MatchDayCalendar({ view, date, today, matchDays }: MatchDayCalendarProps) {
  const [openDay, setOpenDay] = useState<string | null>(null)
  const weeks = useMemo(() => (view === 'month' ? monthGrid(date) : [weekDays(date)]), [view, date])
  const placed = useMemo(() => placeEntries(matchDays, weeks.flat()), [matchDays, weeks])
  const limit = view === 'month' ? MAX_ENTRIES_PER_CELL : Number.POSITIVE_INFINITY

  return (
    <>
      <Box role="grid" aria-label={view === 'month' ? 'Month calendar' : 'Week calendar'} sx={{ overflowX: 'auto' }}>
        <Box role="row" sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, minmax(120px, 1fr))' }}>
          {WEEKDAY_NAMES.map((name) => (
            <Box key={name} role="columnheader" sx={{ p: 0.5, fontWeight: 600, fontSize: 13, color: 'text.secondary' }}>
              {name}
            </Box>
          ))}
        </Box>
        {weeks.map((week) => (
          <Box key={week[0]} role="row" sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, minmax(120px, 1fr))' }}>
            {week.map((day) => {
              const entries = placed.get(day) ?? []
              const shown = entries.slice(0, limit)
              const hidden = entries.length - shown.length
              const outside = view === 'month' && !sameMonth(day, date)
              return (
                <Box
                  key={day}
                  role="gridcell"
                  aria-label={dayLabel(day)}
                  aria-current={day === today ? 'date' : undefined}
                  sx={(theme) => ({
                    minHeight: view === 'month' ? 104 : 220,
                    p: 0.5,
                    border: `1px solid ${theme.palette.divider}`,
                    opacity: outside ? 0.55 : 1,
                    backgroundColor: day === today ? theme.palette.action.hover : undefined,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 0.25,
                  })}
                >
                  <Typography
                    variant="caption"
                    sx={{ fontWeight: day === today ? 700 : 400, color: day === today ? 'primary.main' : 'text.secondary' }}
                  >
                    {dayOfMonth(day)}
                    {day === today && ' · today'}
                  </Typography>
                  {shown.map((entry) => (
                    <MatchDayEntry key={entry.id} summary={entry} />
                  ))}
                  {hidden > 0 && (
                    <Button size="small" onClick={() => setOpenDay(day)} sx={{ justifyContent: 'flex-start', py: 0 }}>
                      {`+${hidden} more`}
                    </Button>
                  )}
                </Box>
              )
            })}
          </Box>
        ))}
      </Box>
      {openDay !== null && (
        <Dialog open fullWidth maxWidth="sm" aria-labelledby="day-entries-title" onClose={() => setOpenDay(null)}>
          <DialogTitle id="day-entries-title">{dayLabel(openDay)}</DialogTitle>
          <DialogContent>
            <Box component="ul" sx={{ listStyle: 'none', p: 0, m: 0, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
              {(placed.get(openDay) ?? []).map((entry) => (
                <li key={entry.id}>
                  <MatchDayEntry summary={entry} />
                </li>
              ))}
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpenDay(null)}>Close</Button>
          </DialogActions>
        </Dialog>
      )}
    </>
  )
}
