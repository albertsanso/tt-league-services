import { render, screen } from '@testing-library/react'
import { StepBadges } from './StepBadges'

describe('StepBadges', () => {
  it('shows unstarted steps as not started', () => {
    render(<StepBadges steps={[]} />)

    expect(screen.getByLabelText('Ingest: not started')).toBeInTheDocument()
    expect(screen.getByLabelText('Fetch package: not started')).toBeInTheDocument()
    expect(screen.getByLabelText('Import: not started')).toBeInTheDocument()
  })

  it('shows ingest and the package fetch of a replay run as skipped', () => {
    render(<StepBadges steps={[{ kind: 'IMPORT', status: 'RUNNING', attempt: 1 }]} replay />)

    expect(screen.getByLabelText('Ingest: skipped (replay)')).toBeInTheDocument()
    expect(screen.getByLabelText('Fetch package: skipped (replay)')).toBeInTheDocument()
    expect(screen.getByLabelText('Import: running, attempt 1')).toBeInTheDocument()
  })

  it('does not call an import that has not started skipped', () => {
    render(<StepBadges steps={[]} replay />)

    expect(screen.getByLabelText('Import: not started')).toBeInTheDocument()
  })
})
