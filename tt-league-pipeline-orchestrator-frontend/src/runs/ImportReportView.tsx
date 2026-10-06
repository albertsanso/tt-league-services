import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { ImportReport } from '../api/types'
import { formatInstant } from './format'

const REPORT_COUNTERS: ReadonlyArray<readonly [keyof ImportReport, string]> = [
  ['filesSeen', 'Files seen'],
  ['itemsPersisted', 'Items persisted'],
  ['skipped', 'Skipped'],
  ['processorFailures', 'Processor failures'],
  ['scheduledCreated', 'Scheduled created'],
  ['upgradedToPlayed', 'Upgraded to played'],
  ['rescheduled', 'Rescheduled'],
  ['partialActas', 'Partial actas'],
  ['invalidActas', 'Invalid actas'],
  ['unresolvedPendingFixtures', 'Unresolved pending fixtures'],
  ['amendedPlayed', 'Amended played'],
]

/** The import counters of a run (summed over its units) or of one unit. */
export function ImportReportView({ report, active }: { report: ImportReport | null; active: boolean }) {
  if (report === null) {
    return <Typography color="text.secondary">{active ? 'No import report yet.' : 'No import report.'}</Typography>
  }
  return (
    <Box component="dl" sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 1, m: 0 }}>
      <Box>
        <Typography component="dt" variant="caption" color="text.secondary">
          Status
        </Typography>
        <Typography component="dd" sx={{ m: 0 }}>
          {report.status}
        </Typography>
      </Box>
      <Box>
        <Typography component="dt" variant="caption" color="text.secondary">
          Received
        </Typography>
        <Typography component="dd" sx={{ m: 0 }}>
          {formatInstant(report.receivedAt)}
        </Typography>
      </Box>
      {REPORT_COUNTERS.map(([field, label]) => (
        <Box key={field}>
          <Typography component="dt" variant="caption" color="text.secondary">
            {label}
          </Typography>
          <Typography component="dd" sx={{ m: 0 }}>
            {String(report[field])}
          </Typography>
        </Box>
      ))}
    </Box>
  )
}
