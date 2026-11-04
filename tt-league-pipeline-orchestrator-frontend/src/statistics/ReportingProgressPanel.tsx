import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import MenuItem from '@mui/material/MenuItem'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { LineChart } from '@mui/x-charts/LineChart'
import { useState } from 'react'
import type { MatchDayProgress, PipelineSource } from '../api/types'
import { useMatchDayFacets } from '../calendar/useMatchDayFacets'
import { SOURCES } from '../runs/runFilters'
import { formatPercent, shortDate } from './format'
import { CHART_HEIGHT, NoData, Panel } from './Panel'
import { useReportingProgress } from './useReportingProgress'

const ALL = ''

function matchDayLabel(matchDay: MatchDayProgress): string {
  const parts = [matchDay.competition]
  if (matchDay.groupNumber !== null) {
    parts.push(`group ${matchDay.groupNumber}`)
  }
  if (matchDay.phase !== null) {
    parts.push(matchDay.phase)
  }
  parts.push(`round ${matchDay.round}`)
  return parts.join(' · ')
}

/** Reported versus pending matches over the window of one match day. It needs exactly one source. */
export function ReportingProgressPanel({
  season,
  defaultSource,
}: {
  readonly season: string | null
  readonly defaultSource: PipelineSource
}) {
  const [source, setSource] = useState<PipelineSource>(defaultSource)
  const [competition, setCompetition] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const { facets } = useMatchDayFacets(source, season)
  const { data, loading, error } = useReportingProgress(source, season, competition)

  const field = { select: true, size: 'small', sx: { minWidth: 160 }, slotProps: { inputLabel: { shrink: true } } } as const
  const competitions = facets?.competitions ?? []
  const matchDays = data?.matchDays ?? []
  const selected = matchDays.find((matchDay) => matchDay.matchDayId === selectedId) ?? matchDays[0] ?? null

  return (
    <Panel
      title="Reporting progress"
      subtitle="How a match day fills with results over its window (last match date plus the grace days)."
    >
      <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', mb: 2 }}>
        <TextField
          {...field}
          label="Progress source"
          value={source}
          onChange={(event) => {
            setSource(event.target.value as PipelineSource)
            setCompetition(null)
            setSelectedId(null)
          }}
        >
          {SOURCES.map((value) => (
            <MenuItem key={value} value={value}>
              {value}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          {...field}
          label="Progress category"
          value={competition ?? ALL}
          onChange={(event) => {
            setCompetition(event.target.value === ALL ? null : event.target.value)
            setSelectedId(null)
          }}
          slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
        >
          <MenuItem value={ALL}>All categories</MenuItem>
          {(competition !== null && !competitions.includes(competition) ? [competition, ...competitions] : competitions).map(
            (value) => (
              <MenuItem key={value} value={value}>
                {value}
              </MenuItem>
            ),
          )}
        </TextField>
      </Box>
      {season === null ? (
        <NoData>Select a season to see the reporting progress.</NoData>
      ) : error !== null ? (
        <Alert severity="error">{error}</Alert>
      ) : data === null ? (
        <Typography color="text.secondary">{loading ? 'Loading…' : ''}</Typography>
      ) : matchDays.length === 0 ? (
        <NoData>No dated match days for this selection.</NoData>
      ) : (
        <>
          <TableContainer sx={{ maxHeight: 260, mb: 2 }}>
            <Table size="small" stickyHeader aria-label="Match days">
              <TableHead>
                <TableRow>
                  <TableCell>Match day</TableCell>
                  <TableCell>State</TableCell>
                  <TableCell align="right">Active</TableCell>
                  <TableCell align="right">Reported</TableCell>
                  <TableCell align="right">Pending</TableCell>
                  <TableCell align="right">Reported %</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {matchDays.map((matchDay) => (
                  <TableRow key={matchDay.matchDayId} selected={matchDay.matchDayId === selected?.matchDayId}>
                    <TableCell>{matchDayLabel(matchDay)}</TableCell>
                    <TableCell>{matchDay.state}</TableCell>
                    <TableCell align="right">{matchDay.active}</TableCell>
                    <TableCell align="right">{matchDay.reported}</TableCell>
                    <TableCell align="right">{matchDay.pending}</TableCell>
                    <TableCell align="right">{formatPercent(matchDay.reported, matchDay.active)}</TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        aria-label={`Show ${matchDayLabel(matchDay)}`}
                        aria-pressed={matchDay.matchDayId === selected?.matchDayId}
                        onClick={() => setSelectedId(matchDay.matchDayId)}
                      >
                        Show
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {selected !== null && (
            <>
              <Typography variant="subtitle2" component="h4">
                {matchDayLabel(selected)}: {selected.windowStart} to {selected.windowEnd}
              </Typography>
              {selected.points.length === 0 ? (
                <NoData>The window of this match day has not started.</NoData>
              ) : (
                <LineChart
                  height={CHART_HEIGHT}
                  xAxis={[{ scaleType: 'point', data: selected.points.map((point) => shortDate(point.date)) }]}
                  series={[
                    { data: selected.points.map((point) => point.reported), label: 'Reported' },
                    { data: selected.points.map((point) => point.pending), label: 'Pending' },
                  ]}
                />
              )}
            </>
          )}
        </>
      )}
    </Panel>
  )
}
