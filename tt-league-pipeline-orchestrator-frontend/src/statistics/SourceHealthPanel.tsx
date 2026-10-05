import Box from '@mui/material/Box'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Chip from '@mui/material/Chip'
import Typography from '@mui/material/Typography'
import { BarChart } from '@mui/x-charts/BarChart'
import type { SourceHealthCounts, SourceHealthResponse } from '../api/types'
import { hasSeveral, rowLabel } from './chartLabels'
import { CHART_HEIGHT, NoData, Panel, SummaryTable } from './Panel'

function SourceCard({ totals }: { readonly totals: SourceHealthCounts }) {
  return (
    <Card variant="outlined" component="article" aria-label={`${totals.source} source health`} sx={{ flex: '1 1 220px' }}>
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
          <Typography variant="subtitle1" component="h4">
            {totals.source}
          </Typography>
          {totals.parseErrors > 0 && <Chip size="small" color="warning" label="Parse errors" />}
        </Box>
        <Box component="dl" sx={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 0.5, m: 0 }}>
          {(
            [
              ['HTTP errors', totals.httpErrors],
              ['Timeouts', totals.timeouts],
              ['Parse errors', totals.parseErrors],
              ['Source unavailable', totals.sourceUnavailable],
              ['Health unknown', totals.healthUnknown],
              ['Ingest attempts', totals.ingestAttempts],
            ] as const
          ).map(([label, value]) => (
            <Box key={label} sx={{ display: 'contents' }}>
              <Typography component="dt" variant="body2" color="text.secondary">
                {label}
              </Typography>
              <Typography component="dd" variant="body2" sx={{ m: 0, textAlign: 'right' }}>
                {value}
              </Typography>
            </Box>
          ))}
        </Box>
      </CardContent>
    </Card>
  )
}

/** HTTP errors, timeouts and parse errors the ingest attempts reported, per source and day. */
export function SourceHealthPanel({ data }: { readonly data: SourceHealthResponse }) {
  const { days, totals } = data
  const several = hasSeveral(days.map((day) => day.source))
  return (
    <Panel
      title="Source health"
      subtitle="Attempts that report no health data (an older ingest service) are counted as unknown."
    >
      {days.length === 0 ? (
        <NoData />
      ) : (
        <>
          <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', mb: 2 }}>
            {totals.map((row) => (
              <SourceCard key={row.source} totals={row} />
            ))}
          </Box>
          <BarChart
            height={CHART_HEIGHT}
            xAxis={[{ scaleType: 'band', data: days.map((day) => rowLabel(day.date, day.source, several)) }]}
            series={[
              { data: days.map((day) => day.httpErrors), label: 'HTTP errors', stack: 'health' },
              { data: days.map((day) => day.timeouts), label: 'Timeouts', stack: 'health' },
              { data: days.map((day) => day.parseErrors), label: 'Parse errors', stack: 'health' },
            ]}
          />
          <SummaryTable
            label="Source health table"
            head={['Day', 'Source', 'HTTP errors', 'Timeouts', 'Parse errors', 'Attempts', 'Unavailable', 'Unknown']}
            rows={days.map((day) => [
              day.date,
              day.source,
              day.httpErrors,
              day.timeouts,
              day.parseErrors,
              day.ingestAttempts,
              day.sourceUnavailable,
              day.healthUnknown,
            ])}
          />
        </>
      )}
    </Panel>
  )
}
