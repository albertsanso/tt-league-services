import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import { Link as RouterLink } from 'react-router-dom'
import { useEventConnection } from '../events/useRunEvents'
import { useRunActivity } from './useRunActivity'
import type { ActivitySeverity } from './useRunActivity'

const SEVERITY_COLOR: Record<ActivitySeverity, string> = {
  info: 'text.primary',
  success: 'success.main',
  warning: 'warning.main',
  error: 'error.main',
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function formatTime(epoch: number): string {
  const date = new Date(epoch)
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function RunActivityLog({ runId }: { runId?: string }) {
  const entries = useRunActivity({ runId })
  const { state } = useEventConnection()

  return (
    <Box>
      {state !== 'open' && state !== 'connecting' && (
        <Alert severity="warning" sx={{ mb: 1 }}>
          {state === 'stopped' ? 'Live updates stopped.' : 'Live updates paused — reconnecting…'}
        </Alert>
      )}
      <Typography variant="caption" color="text.secondary" component="p" sx={{ mb: 0.5 }}>
        Showing events received since this page was opened.
      </Typography>
      <Box
        role="log"
        aria-live="polite"
        aria-label="Live activity"
        sx={{
          border: 1,
          borderColor: 'divider',
          borderRadius: 1,
          p: 1,
          maxHeight: 240,
          overflowY: 'auto',
          fontFamily: 'monospace',
          fontSize: '0.8rem',
        }}
      >
        {entries.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'inherit' }}>
            No activity yet.
          </Typography>
        )}
        {entries.map((entry) => (
          <Box key={entry.id} sx={{ color: SEVERITY_COLOR[entry.severity], wordBreak: 'break-word' }}>
            {formatTime(entry.receivedAt)} {entry.source ?? '—'} {entry.text}
            {entry.runId !== undefined && runId === undefined && (
              <>
                {' '}
                <Link component={RouterLink} to={`/runs/${entry.runId}`}>
                  run {entry.runId.slice(0, 8)}
                </Link>
              </>
            )}
          </Box>
        ))}
      </Box>
    </Box>
  )
}
