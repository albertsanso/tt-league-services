import { useMemo } from 'react'
import { computeOverallRecord, computeWinRateBySeason } from '../utils/clubSummary.js'

function sortedSeasons(values) {
  return [...new Set(values)].sort((left, right) => right.localeCompare(left))
}

function CompetitionRow({ competition, t }) {
  const record = useMemo(() => computeOverallRecord([competition]), [competition])
  const total = record.wins + record.draws + record.losses
  const pct = (value) => (total === 0 ? 0 : (value / total) * 100)

  return (
    <li className="comp-row card">
      <span className="comp-row-name">
        <strong>{competition.name}</strong>
        {competition.source ? <span className="comp-row-source">{competition.source}</span> : null}
      </span>
      <span className="comp-row-counts">
        {record.wins}{t('detail.winsAbbrev')} · {record.draws}{t('detail.drawsAbbrev')} · {record.losses}{t('detail.lossesAbbrev')}
      </span>
      <span
        className="record-bar comp-row-bar"
        role="img"
        aria-label={t('detail.summaryRecordAria', record)}
      >
        <span className="record-bar-segment is-win" style={{ flexBasis: `${pct(record.wins)}%` }} />
        <span className="record-bar-segment is-draw" style={{ flexBasis: `${pct(record.draws)}%` }} />
        <span className="record-bar-segment is-loss" style={{ flexBasis: `${pct(record.losses)}%` }} />
      </span>
      <span className="comp-row-rate">{record.winRate}%</span>
    </li>
  )
}

function WinRateTrend({ seasons, competitions, t }) {
  const points = useMemo(() => computeWinRateBySeason(seasons, competitions), [seasons, competitions])
  const width = 640
  const height = 180
  const padding = { top: 16, right: 20, bottom: 30, left: 40 }
  const plotWidth = width - padding.left - padding.right
  const plotHeight = height - padding.top - padding.bottom
  const x = (index) => (points.length === 1
    ? padding.left + plotWidth / 2
    : padding.left + index * plotWidth / (points.length - 1))
  const y = (value) => padding.top + plotHeight - (value / 100) * plotHeight
  const linePoints = points.map((point, index) => `${x(index)},${y(point.winRate)}`).join(' ')

  if (points.length === 0) {
    return <p className="club-empty card">{t('detail.statsTrendEmpty')}</p>
  }

  return (
    <div className="history-chart history-connected-chart card" role="img" aria-label={t('detail.statsTrendAria')}>
      <svg viewBox={`0 0 ${width} ${height}`} role="presentation" focusable="false" preserveAspectRatio="xMidYMid meet">
        {[0, 50, 100].map((tick) => (
          <g key={tick}>
            <line className="chart-grid-line" x1={padding.left} y1={y(tick)} x2={width - padding.right} y2={y(tick)} />
            <text className="chart-axis-tick" x={padding.left - 8} y={y(tick) + 3} textAnchor="end">{tick}%</text>
          </g>
        ))}
        <polyline className="chart-line wins-line" fill="none" points={linePoints} />
        {points.map((point, index) => (
          <g key={point.season}>
            <circle className="chart-point wins-point" cx={x(index)} cy={y(point.winRate)} r="3" />
            <text className="chart-season-label" x={x(index)} y={height - 10} textAnchor="middle">{point.season}</text>
          </g>
        ))}
      </svg>
    </div>
  )
}

function ClubStatsPanel({ competitions, seasons, trendCompetitions, season, t }) {
  const groupBySeason = !season
  const rowSeasons = groupBySeason ? sortedSeasons(competitions.map((item) => item.season)) : [season]

  return (
    <section className="club-detail-section" aria-labelledby="club-stats-title">
      <h2 id="club-stats-title">{t('detail.statsTab')}</h2>
      <h3>{t('detail.statsRecordTitle')}</h3>
      {competitions.length === 0 ? (
        <p className="club-empty card">{t('detail.competitionsEmpty')}</p>
      ) : (
        rowSeasons.map((rowSeason) => {
          const seasonCompetitions = competitions
            .filter((item) => item.season === rowSeason)
            .sort((left, right) => left.name.localeCompare(right.name))

          return (
            <div key={rowSeason}>
              {groupBySeason ? <h4 className="comp-row-season-heading">{rowSeason}</h4> : null}
              <ul className="comp-row-list">
                {seasonCompetitions.map((competition) => (
                  <CompetitionRow key={`${competition.season}-${competition.name}`} competition={competition} t={t} />
                ))}
              </ul>
            </div>
          )
        })
      )}

      <div className="trend-card">
        <h3>{t('detail.statsWinRateTrendTitle')}</h3>
        <WinRateTrend seasons={seasons} competitions={trendCompetitions} t={t} />
      </div>
    </section>
  )
}

export default ClubStatsPanel
