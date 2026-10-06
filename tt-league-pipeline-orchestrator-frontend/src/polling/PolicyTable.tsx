import Button from '@mui/material/Button'
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
import type { PipelineSource, PollingPolicy, PollingStatus } from '../api/types'
import { Can } from '../auth/Can'
import { formatInstant } from '../runs/format'
import { formatIsoDuration, lookbackLabel } from './format'
import { DURATION_FIELDS, FIELD_LABELS } from './policyForm'
import type { PolicyField } from './policyForm'

interface PolicyTableProps {
  readonly status: PollingStatus
  readonly policies: readonly PollingPolicy[]
  readonly onEdit: (policy: PollingPolicy) => void
  readonly onReset: (policy: PollingPolicy) => void
}

const ROWS: readonly PolicyField[] = [
  ...DURATION_FIELDS,
  'overdueStopAfterDays',
  'noChangeThreshold',
  'recentMatchDays',
]

function value(policy: PollingPolicy, field: PolicyField): string {
  switch (field) {
    case 'recentMatchDays':
      return lookbackLabel(policy.recentMatchDays)
    case 'overdueStopAfterDays':
      return `${policy.overdueStopAfterDays} d`
    case 'noChangeThreshold':
      return String(policy.noChangeThreshold)
    default:
      return formatIsoDuration(policy[field])
  }
}

/** The effective policy of every source, one column per source. A source that is not polled adaptively keeps its column. */
export function PolicyTable({ status, policies, onEdit, onReset }: PolicyTableProps) {
  const bySource = new Map<PipelineSource, PollingPolicy>(policies.map((policy) => [policy.source, policy]))
  const columns = status.sources.flatMap((entry) => {
    const policy = bySource.get(entry.source)
    return policy === undefined ? [] : [{ entry, policy }]
  })
  return (
    <Card variant="outlined" component="section" aria-label="Polling policies">
      <CardContent>
        <Typography variant="h6" component="h3">
          Policy per source
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          The effective settings of each source: its stored override, or the configured defaults. The same policy applies to
          every season and competition of the source.
        </Typography>
        <TableContainer>
          <Table size="small" aria-label="Polling policy by source">
            <TableHead>
              <TableRow>
                <TableCell>Setting</TableCell>
                {columns.map(({ entry, policy }) => (
                  <TableCell key={entry.source} align="right">
                    <div>{entry.source}</div>
                    {policy.overridden ? (
                      <Chip
                        size="small"
                        color="warning"
                        label="Overridden"
                        title={`${policy.updatedBy ?? '—'} · ${formatInstant(policy.updatedAt)}`}
                      />
                    ) : (
                      <Chip size="small" variant="outlined" label="Defaults" />
                    )}
                    {policy.overridden && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        {`${policy.updatedBy ?? '—'} · ${formatInstant(policy.updatedAt)}`}
                      </Typography>
                    )}
                    {entry.mode !== 'ADAPTIVE' && (
                      <Typography variant="caption" color="text.secondary" component="div">
                        Not used: source is not adaptively polled
                      </Typography>
                    )}
                  </TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {ROWS.map((field) => (
                <TableRow key={field}>
                  <TableCell component="th" scope="row">
                    {FIELD_LABELS[field]}
                  </TableCell>
                  {columns.map(({ policy }) => (
                    <TableCell key={policy.source} align="right">
                      {value(policy, field)}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
              <TableRow>
                <TableCell component="th" scope="row">
                  Actions
                </TableCell>
                {columns.map(({ policy }) => (
                  <TableCell key={policy.source} align="right" sx={{ whiteSpace: 'nowrap' }}>
                    <Can capability="edit-polling-policy" mode="disable">
                      <Button size="small" onClick={() => onEdit(policy)} aria-label={`Edit policy of ${policy.source}`}>
                        Edit
                      </Button>
                    </Can>
                    <Can capability="edit-polling-policy" mode="disable">
                      <Button
                        size="small"
                        disabled={!policy.overridden}
                        onClick={() => onReset(policy)}
                        aria-label={`Reset policy of ${policy.source} to defaults`}
                      >
                        Reset to defaults
                      </Button>
                    </Can>
                  </TableCell>
                ))}
              </TableRow>
            </TableBody>
          </Table>
        </TableContainer>
      </CardContent>
    </Card>
  )
}
