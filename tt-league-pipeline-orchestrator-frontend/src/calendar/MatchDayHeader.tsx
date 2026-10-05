import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Typography from '@mui/material/Typography'
import type { MatchDaySummary, TrackedMatchStatus } from '../api/types'
import { formatInstant } from '../runs/format'
import { COMPLETION_LABELS, completionColor, entryLabel, MATCH_STATUS_LABELS, progressText, STATE_LABELS } from './completion'

const STATUS_ORDER: readonly TrackedMatchStatus[] = ['REPORTED', 'AWAITING_RESULT', 'SCHEDULED', 'POSTPONED', 'OVERDUE']

const CLOSE_REASON_LABELS: Readonly<Record<string, string>> = {
  ALL_RESOLVED: 'every match resolved',
  MANUAL: 'closed by an operator',
  REMOVED: 'no longer reported by the platform',
}

function window(summary: MatchDaySummary): string {
  if (summary.firstDate === null || summary.lastDate === null) {
    return 'Undated'
  }
  const dates = summary.firstDate === summary.lastDate ? summary.firstDate : `${summary.firstDate} – ${summary.lastDate}`
  return summary.windowEnd === null ? dates : `${dates} (grace until ${summary.windowEnd})`
}

/** Key, source, season, window, state, completion, `reported / total` and the counts per status. */
export function MatchDayHeader({ summary }: { summary: MatchDaySummary }) {
  const closed = summary.state === 'CLOSED'
  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="h5" component="h2">
          {entryLabel(summary)}
        </Typography>
        <Chip size="small" color={completionColor(summary.completion)} label={COMPLETION_LABELS[summary.completion]} />
        <Chip size="small" variant="outlined" label={STATE_LABELS[summary.state]} />
        <Chip size="small" variant="outlined" label={`${progressText(summary)} reported`} />
      </Box>
      <Typography sx={{ mt: 1 }}>
        {summary.source} · {summary.season} · {window(summary)}
      </Typography>
      {closed && (
        <Typography variant="body2" color="text.secondary">
          {`Closed ${formatInstant(summary.closedAt)} by ${summary.closedBy ?? 'unknown'}`}
          {summary.closeReason !== null && ` — ${CLOSE_REASON_LABELS[summary.closeReason] ?? summary.closeReason}`}
        </Typography>
      )}
      <Box component="ul" aria-label="Matches per status" sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', listStyle: 'none', p: 0, m: 0, mt: 1 }}>
        {STATUS_ORDER.map((status) => (
          <li key={status}>
            <Chip size="small" variant="outlined" label={`${MATCH_STATUS_LABELS[status]}: ${summary.matchCounts[status]}`} />
          </li>
        ))}
        <li>
          <Chip size="small" variant="outlined" label={`Ignored: ${summary.ignoredMatches}`} />
        </li>
      </Box>
    </>
  )
}
