import EventBusyIcon from '@mui/icons-material/EventBusy'
import LockIcon from '@mui/icons-material/Lock'
import Box from '@mui/material/Box'
import ButtonBase from '@mui/material/ButtonBase'
import { alpha } from '@mui/material/styles'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import type { MatchDaySummary } from '../api/types'
import { completionColor, entryDescription, entryLabel, postponedCount, progressText } from './completion'
import { matchDayPath } from './paths'

/**
 * One match day in a calendar cell: a link to its detail, coloured by completion. The text always carries the label
 * and `reported / total`; closed and postponed are marked with icons; the accessible name carries the completion
 * label, state and counts, so colour is never the only signal.
 */
export function MatchDayEntry({ summary }: { summary: MatchDaySummary }) {
  const location = useLocation()
  const color = completionColor(summary.completion)
  return (
    <ButtonBase
      component={RouterLink}
      to={matchDayPath(summary.id)}
      state={{ backTo: location.search }}
      aria-label={entryDescription(summary)}
      sx={(theme) => {
        const main = color === 'default' ? theme.palette.text.secondary : theme.palette[color].main
        return {
          display: 'block',
          width: '100%',
          textAlign: 'left',
          px: 0.75,
          py: 0.25,
          borderRadius: 1,
          borderLeft: `4px solid ${main}`,
          backgroundColor: alpha(main, 0.12),
          color: theme.palette.text.primary,
          '&:hover, &:focus-visible': { backgroundColor: alpha(main, 0.24) },
        }
      }}
    >
      <Box component="span" sx={{ display: 'flex', alignItems: 'center', gap: 0.5, fontSize: 12, lineHeight: 1.3 }}>
        <Box component="span" sx={{ flexGrow: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {entryLabel(summary)}
        </Box>
        {postponedCount(summary) > 0 && <EventBusyIcon aria-hidden fontSize="inherit" titleAccess="" />}
        {summary.state === 'CLOSED' && <LockIcon aria-hidden fontSize="inherit" titleAccess="" />}
        <Box component="span" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
          {progressText(summary)}
        </Box>
      </Box>
    </ButtonBase>
  )
}
