import Link from '@mui/material/Link'
import { Link as RouterLink } from 'react-router-dom'
import type { TriggerResult } from '../api/types'

interface TriggerResultListProps {
  readonly results: readonly TriggerResult[]
  /** Called when a link inside the list is followed, so a dialog around it can close. */
  readonly onNavigate?: () => void
}

/** One line per source of a rejected trigger, with a link to the active run when there is one. */
export function TriggerResultList({ results, onNavigate }: TriggerResultListProps) {
  return (
    <ul style={{ margin: 0, paddingLeft: '1.2em' }}>
      {results.map((result) => (
        <li key={result.source}>
          {result.source} — {result.message ?? result.outcome}
          {result.code !== undefined && ` (${result.code})`}
          {result.activeRunId !== undefined && (
            <>
              {' '}
              <Link component={RouterLink} to={`/runs/${result.activeRunId}`} onClick={onNavigate}>
                Open active run
              </Link>
            </>
          )}
        </li>
      ))}
    </ul>
  )
}
