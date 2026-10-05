import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useState } from 'react'
import type { RunSummary } from '../api/types'
import { useApi } from '../api/useApi'
import { Can } from '../auth/Can'
import { useRunEvents } from '../events/useRunEvents'

export default function RunsPage() {
  const api = useApi()
  const [runs, setRuns] = useState<readonly RunSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    api.runs.listRuns({ page: 0, size: 20 }, controller.signal).then(
      (page) => {
        setRuns(page.items)
        setError(null)
      },
      (failure: unknown) => {
        if (controller.signal.aborted) {
          return
        }
        setError(failure instanceof Error ? failure.message : 'Failed to load runs')
      },
    )
    return () => controller.abort()
  }, [api, reloads])

  const refetch = useCallback(() => setReloads((count) => count + 1), [])
  useRunEvents((event) => {
    if (event.type === 'run' || event.type === 'reconnected') {
      refetch()
    }
  })

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5" component="h2">
          Runs
        </Typography>
        <Can capability="trigger-runs" mode="disable">
          <Button variant="contained">Run now</Button>
        </Can>
      </Box>
      {error !== null && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {runs !== null && runs.length === 0 && <Typography color="text.secondary">No runs yet.</Typography>}
      {runs !== null && runs.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label="Latest runs">
            <TableHead>
              <TableRow>
                <TableCell>Source</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Created</TableCell>
                <TableCell>Requested by</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {runs.map((run) => (
                <TableRow key={run.id}>
                  <TableCell>{run.source}</TableCell>
                  <TableCell>{run.status}</TableCell>
                  <TableCell>{new Date(run.createdAt).toLocaleString()}</TableCell>
                  <TableCell>{run.requestedBy ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </>
  )
}
