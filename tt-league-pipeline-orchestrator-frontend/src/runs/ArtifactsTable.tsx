import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Tooltip from '@mui/material/Tooltip'
import type { Artifact } from '../api/types'
import { formatBytes, formatInstant } from './format'

export function ArtifactsTable({ artifacts, label }: { artifacts: readonly Artifact[]; label: string }) {
  return (
    <TableContainer>
      <Table size="small" aria-label={label}>
        <TableHead>
          <TableRow>
            <TableCell>Kind</TableCell>
            <TableCell>Size</TableCell>
            <TableCell>Created</TableCell>
            <TableCell>SHA-256</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {artifacts.map((artifact) => (
            <TableRow key={`${artifact.unitId}-${artifact.kind}-${artifact.sha256}`}>
              <TableCell>
                {artifact.kind}
                {artifact.purgedAt !== null && (
                  <Chip size="small" variant="outlined" sx={{ ml: 1 }} label={`Purged ${formatInstant(artifact.purgedAt)}`} />
                )}
              </TableCell>
              <TableCell>{formatBytes(artifact.sizeBytes)}</TableCell>
              <TableCell>{formatInstant(artifact.createdAt)}</TableCell>
              <TableCell>
                <Tooltip title={artifact.sha256}>
                  <Box
                    component="code"
                    sx={{ display: 'inline-block', maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', verticalAlign: 'bottom' }}
                  >
                    {artifact.sha256}
                  </Box>
                </Tooltip>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
