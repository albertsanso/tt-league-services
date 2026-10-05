import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router-dom'
import { formatInstant } from '../runs/format'
import { RunStatusChip } from '../runs/RunStatusChip'
import { TRIGGER_LABELS } from '../runs/runStatus'
import type { TimelineItem } from './timeline'

/** The merged timeline, newest first: tracker and operator events, and the runs that touched the match day. */
export function MatchDayTimeline({ items }: { items: readonly TimelineItem[] }) {
  if (items.length === 0) {
    return <Typography color="text.secondary">Nothing has happened to this match day yet.</Typography>
  }
  return (
    <Box component="ol" aria-label="Timeline" sx={{ listStyle: 'none', p: 0, m: 0, display: 'flex', flexDirection: 'column', gap: 1 }}>
      {items.map((item) => (
        <li key={item.key}>
          {item.type === 'run' ? (
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="body2" color="text.secondary" sx={{ minWidth: 170 }}>
                {formatInstant(item.at)}
              </Typography>
              <Link component={RouterLink} to={`/runs/${item.run.id}`}>
                {`Run ${item.run.id.slice(0, 8)}`}
              </Link>
              <Typography variant="body2">
                {TRIGGER_LABELS[item.run.trigger]}
                {item.run.requestedBy !== null ? ` by ${item.run.requestedBy}` : ''}
              </Typography>
              <RunStatusChip status={item.run.status} />
            </Box>
          ) : (
            <Box>
              <Box sx={{ display: 'flex', gap: 1, alignItems: 'baseline', flexWrap: 'wrap' }}>
                <Typography variant="body2" color="text.secondary" sx={{ minWidth: 170 }}>
                  {formatInstant(item.at)}
                </Typography>
                <Typography variant="body2">{item.text}</Typography>
                {item.runId !== null && (
                  <Link component={RouterLink} to={`/runs/${item.runId}`} variant="body2">
                    Open run
                  </Link>
                )}
              </Box>
              {item.note !== null && (
                <Typography variant="body2" color="text.secondary" sx={{ pl: '178px', whiteSpace: 'pre-wrap' }}>
                  {item.note}
                </Typography>
              )}
            </Box>
          )}
        </li>
      ))}
    </Box>
  )
}
