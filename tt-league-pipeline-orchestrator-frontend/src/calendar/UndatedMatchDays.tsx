import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Collapse from '@mui/material/Collapse'
import { useState } from 'react'
import type { MatchDaySummary } from '../api/types'
import { MatchDayEntry } from './MatchDayEntry'

/** Match days without dates cannot sit on a day; they are listed here, collapsed, and hidden when there are none. */
export function UndatedMatchDays({ matchDays }: { matchDays: readonly MatchDaySummary[] }) {
  const [open, setOpen] = useState(false)
  if (matchDays.length === 0) {
    return null
  }
  return (
    <Box sx={{ mt: 2 }}>
      <Button size="small" aria-expanded={open} aria-controls="undated-match-days" onClick={() => setOpen(!open)}>
        {`Undated match days (${matchDays.length})`}
      </Button>
      <Collapse in={open} unmountOnExit>
        <Box id="undated-match-days" component="ul" sx={{ listStyle: 'none', p: 0, m: 0, display: 'flex', flexDirection: 'column', gap: 0.5, maxWidth: 520 }}>
          {matchDays.map((matchDay) => (
            <li key={matchDay.id}>
              <MatchDayEntry summary={matchDay} />
            </li>
          ))}
        </Box>
      </Collapse>
    </Box>
  )
}
