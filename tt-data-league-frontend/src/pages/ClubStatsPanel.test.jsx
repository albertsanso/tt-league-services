import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import ClubStatsPanel from './ClubStatsPanel.jsx'
import i18n from '../i18n/index.js'

function t(key, options) {
  return i18n.t(key, options)
}

function competition(season, name, { wins = 0, draws = 0, losses = 0, source } = {}) {
  return { name, season, source, matchCount: wins + draws + losses, resultTotals: { wins, draws, losses } }
}

describe('ClubStatsPanel', () => {
  afterEach(() => cleanup())

  it('shows one record row per competition in the selected season', () => {
    const competitions = [
      competition('2024-2025', 'Divisió Honor', { wins: 9, draws: 1, losses: 2, source: 'FCTM' }),
      competition('2024-2025', 'Primera Catalana', { wins: 11, draws: 0, losses: 3, source: 'FCTM' }),
    ]

    render(
      <ClubStatsPanel
        competitions={competitions}
        seasons={['2024-2025']}
        trendCompetitions={competitions}
        season="2024-2025"
        t={t}
      />,
    )

    expect(screen.getByText('Divisió Honor')).toBeInTheDocument()
    expect(screen.getByText('Primera Catalana')).toBeInTheDocument()
    expect(screen.getByText('75%')).toBeInTheDocument()
    expect(screen.getByText('79%')).toBeInTheDocument()
  })

  it('groups rows by season when all seasons are selected', () => {
    const competitions = [
      competition('2023-2024', 'Primera Catalana', { wins: 5, draws: 0, losses: 5 }),
      competition('2024-2025', 'Primera Catalana', { wins: 8, draws: 0, losses: 2 }),
    ]

    render(
      <ClubStatsPanel
        competitions={competitions}
        seasons={['2023-2024', '2024-2025']}
        trendCompetitions={competitions}
        season=""
        t={t}
      />,
    )

    expect(screen.getAllByText('2023-2024').length).toBeGreaterThan(0)
    expect(screen.getAllByText('2024-2025').length).toBeGreaterThan(0)
    expect(document.querySelector('.comp-row-rate')).toHaveTextContent('80%')
    expect(document.querySelectorAll('.comp-row-rate')[1]).toHaveTextContent('50%')
  })

  it('renders the win-rate-by-season trend for seasons with data', () => {
    const competitions = [
      competition('2023-2024', 'Primera Catalana', { wins: 5, draws: 0, losses: 5 }),
      competition('2024-2025', 'Primera Catalana', { wins: 8, draws: 0, losses: 2 }),
    ]

    render(
      <ClubStatsPanel
        competitions={competitions}
        seasons={['2022-2023', '2023-2024', '2024-2025']}
        trendCompetitions={competitions}
        season=""
        t={t}
      />,
    )

    expect(screen.getByRole('img', { name: i18n.t('detail.statsTrendAria') })).toBeInTheDocument()
    expect(screen.queryByText('Encara no hi ha prou dades de temporades per mostrar una tendència.')).not.toBeInTheDocument()
  })

  it('shows empty states when there are no competitions or trend data', () => {
    render(
      <ClubStatsPanel
        competitions={[]}
        seasons={[]}
        trendCompetitions={[]}
        season="2024-2025"
        t={t}
      />,
    )

    expect(screen.getByText('No hi ha resums de competició disponibles per als filtres seleccionats.')).toBeInTheDocument()
    expect(screen.getByText('Encara no hi ha prou dades de temporades per mostrar una tendència.')).toBeInTheDocument()
  })
})
