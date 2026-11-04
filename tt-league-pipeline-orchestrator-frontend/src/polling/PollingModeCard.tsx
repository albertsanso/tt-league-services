import Alert from '@mui/material/Alert'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Chip from '@mui/material/Chip'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import type { PollingStatus } from '../api/types'
import { formatIsoDuration, modeLabel } from './format'

/** How each source is refreshed, as the server is configured; read-only. */
export function PollingModeCard({ status }: { readonly status: PollingStatus }) {
  const anyAdaptive = status.sources.some((entry) => entry.mode === 'ADAPTIVE')
  return (
    <Card variant="outlined" component="section" aria-label="Polling mode">
      <CardContent>
        <Typography variant="h6" component="h3">
          How each source is refreshed
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {status.season === null
            ? 'No source is scheduled, so there is no schedule season or time zone.'
            : `Schedule season ${status.season}, time zone ${status.zone ?? '—'}. Adaptive ticks run every ${formatIsoDuration(status.tickInterval)}.`}
        </Typography>
        {!anyAdaptive && (
          <Alert severity="info" sx={{ mb: 1 }}>
            No source is polled adaptively. Set PIPELINE_POLLING_SOURCES in the orchestrator configuration to turn it on.
          </Alert>
        )}
        <TableContainer>
          <Table size="small" aria-label="Polling mode by source">
            <TableHead>
              <TableRow>
                <TableCell>Source</TableCell>
                <TableCell>Mode</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {status.sources.map((entry) => (
                <TableRow key={entry.source}>
                  <TableCell>{entry.source}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      variant={entry.mode === 'NONE' ? 'outlined' : 'filled'}
                      color={entry.mode === 'ADAPTIVE' ? 'primary' : 'default'}
                      label={modeLabel(entry.mode, entry.cron)}
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </CardContent>
    </Card>
  )
}
