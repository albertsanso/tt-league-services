import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'

export const CHART_HEIGHT = 260

/** One dashboard card: a heading, optional notes and the chart plus its summary table. */
export function Panel({
  title,
  subtitle,
  children,
}: {
  readonly title: string
  readonly subtitle?: string
  readonly children: ReactNode
}) {
  return (
    <Card variant="outlined" component="section" aria-label={title} sx={{ minWidth: 0 }}>
      <CardContent>
        <Typography variant="h6" component="h3">
          {title}
        </Typography>
        {subtitle !== undefined && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {subtitle}
          </Typography>
        )}
        {children}
      </CardContent>
    </Card>
  )
}

export function NoData({ children = 'No data in this range.' }: { readonly children?: ReactNode }) {
  return (
    <Typography color="text.secondary" sx={{ py: 2 }}>
      {children}
    </Typography>
  )
}

/** The compact table under a chart: it is the accessible text equivalent of the chart. */
export function SummaryTable({
  label,
  head,
  rows,
}: {
  readonly label: string
  readonly head: readonly string[]
  readonly rows: readonly (readonly (string | number)[])[]
}) {
  return (
    <TableContainer sx={{ maxHeight: 280 }}>
      <Table size="small" stickyHeader aria-label={label}>
        <TableHead>
          <TableRow>
            {head.map((name) => (
              <TableCell key={name}>{name}</TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row, index) => (
            <TableRow key={`${row.join('|')}-${index}`}>
              {row.map((value, column) => (
                <TableCell key={head[column]} align={column < 2 ? 'left' : 'right'}>
                  {value}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
