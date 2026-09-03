import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import SeasonImportListItem from './SeasonImportListItem.jsx'

describe('SeasonImportListItem', () => {
  it('renders accessible actions and status badges', () => {
    render(
      <SeasonImportListItem
        season={{ id: '2022-2024', updatedAt: '2026-09-01', status: 'COMPLETED' }}
        onLoad={vi.fn()}
        onSimulate={vi.fn()}
      />,
    )

    expect(screen.getByText('2022-2024')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /carrega/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /simula/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /carrega/i })).toHaveClass('import-start-button')
    expect(screen.getByText('COMPLETED')).toBeInTheDocument()
  })
})
