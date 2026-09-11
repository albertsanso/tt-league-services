import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ImportResourceList from './ImportResourceList.jsx'

afterEach(() => {
  cleanup()
})

const resources = [
  { id: 'r-pending-2024', season: '2024-2025', resourceType: 'ACTAS', status: 'PENDING' },
  { id: 'r-processing-2026', season: '2026-2027', resourceType: 'ACTAS', status: 'PROCESSING' },
  { id: 'r-error-2023', season: '2023-2024', resourceType: 'ACTAS', status: 'ERROR' },
  { id: 'r-processed-2025', season: '2025-2026', resourceType: 'ACTAS', status: 'PROCESSED' },
  { id: 'r-processed-2022', season: '2022-2023', resourceType: 'ACTAS', status: 'PROCESSED' },
]

describe('ImportResourceList', () => {
  it('splits resources into Imported and Pending sections, sorted by season desc', () => {
    render(<ImportResourceList resources={resources} onSimulate={vi.fn()} onImport={vi.fn()} />)

    expect(screen.getByText('Importats')).toBeInTheDocument()
    expect(screen.getByText('Pendents')).toBeInTheDocument()

    const lists = screen.getAllByRole('list')
    const importedList = lists[0]
    const pendingList = lists[1]

    expect(importedList.textContent.indexOf('2025-2026')).toBeLessThan(importedList.textContent.indexOf('2022-2023'))
    expect(pendingList.textContent.indexOf('2026-2027')).toBeLessThan(pendingList.textContent.indexOf('2024-2025'))
    expect(pendingList.textContent.indexOf('2024-2025')).toBeLessThan(pendingList.textContent.indexOf('2023-2024'))

    expect(importedList.querySelectorAll('article')).toHaveLength(2)
    expect(pendingList.querySelectorAll('article')).toHaveLength(3)
  })

  it('shows the processing indicator only on the processing resource card', () => {
    render(<ImportResourceList resources={resources} onSimulate={vi.fn()} onImport={vi.fn()} />)

    const indicators = screen.getAllByRole('status')
    expect(indicators).toHaveLength(1)
    const processingCard = indicators[0].closest('article')
    expect(processingCard.textContent).toContain('2026-2027')
  })

  it('omits a group heading when its group is empty', () => {
    const allPending = resources.filter((resource) => resource.status !== 'PROCESSED')
    render(<ImportResourceList resources={allPending} onSimulate={vi.fn()} onImport={vi.fn()} />)

    expect(screen.queryByText('Importats')).not.toBeInTheDocument()
    expect(screen.getByText('Pendents')).toBeInTheDocument()
  })
})
