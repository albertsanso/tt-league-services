import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useClubMatches } from '../hooks/useClubs.js'
import { computeOverallRecord, getRecentMatches, getTopPerformers, getTopPlayers } from '../utils/clubSummary.js'
import i18n from '../i18n/index.js'
import { routePaths } from '../config/routes.js'

function initials(name) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

function StatTile({ label, value, subLabel }) {
  return (
    <article className="stat-tile card">
      <p className="stat-tile-label">{label}</p>
      <p className="stat-tile-value">{value}</p>
      <p className="stat-tile-sub">{subLabel}</p>
    </article>
  )
}

function RecordBar({ record, t }) {
  const total = record.wins + record.draws + record.losses
  const pct = (value) => (total === 0 ? 0 : (value / total) * 100)

  return (
    <div className="record-bar-block">
      <div
        className="record-bar"
        role="img"
        aria-label={t('detail.summaryRecordAria', record)}
      >
        <span className="record-bar-segment is-win" style={{ flexBasis: `${pct(record.wins)}%` }} />
        <span className="record-bar-segment is-draw" style={{ flexBasis: `${pct(record.draws)}%` }} />
        <span className="record-bar-segment is-loss" style={{ flexBasis: `${pct(record.losses)}%` }} />
      </div>
      <p className="record-bar-legend">
        {record.wins}{t('detail.winsAbbrev')} · {record.draws}{t('detail.drawsAbbrev')} · {record.losses}{t('detail.lossesAbbrev')}
      </p>
    </div>
  )
}

function MatchRow({ match, returnSearch, t }) {
  const resultLabel = match.result === 'win' ? t('detail.win') : match.result === 'loss' ? t('detail.loss') : t('detail.draw')
  const score = match.homeGamesWon == null || match.awayGamesWon == null
    ? t('detail.pendingResult')
    : `${match.homeGamesWon} — ${match.awayGamesWon}`
  const date = match.dateTime ? new Date(match.dateTime).toLocaleDateString(i18n.language) : null

  return (
    <li>
      <Link className="match-row card" to={routePaths.matchSummary(match.id, returnSearch)}>
        <span className={`match-row-result is-${match.result}`} title={resultLabel}>
          {resultLabel[0]}
        </span>
        <span className="match-row-body">
          <strong>{match.homeTeam} — {match.awayTeam}</strong>
          <span className="match-row-meta">
            {[match.competition, t('detail.round', { round: match.round }), date].filter(Boolean).join(' · ')}
          </span>
        </span>
        <span className="match-row-score">{score}</span>
      </Link>
    </li>
  )
}

function PlayerRow({ player, t }) {
  const name = player.playerName ?? player.registrationName
  const body = (
    <>
      <span className="player-avatar" aria-hidden="true">{initials(name)}</span>
      <span className="mini-list-body">
        <strong>{name}</strong>
        <span className="mini-list-meta">{player.competitions.join(', ')}</span>
      </span>
      <span className="mini-list-stat">
        {t('detail.summaryPlayerCompetitionsCount', { count: player.competitions.length })}
      </span>
    </>
  )

  return (
    <li>
      {player.canonicalPlayerId ? (
        <Link
          className="mini-list-item card"
          to={routePaths.playerDetails(
            player.canonicalPlayerId,
            `source=${encodeURIComponent(player.source)}&season=${encodeURIComponent(player.season)}`,
          )}
        >
          {body}
        </Link>
      ) : (
        <div className="mini-list-item card">{body}</div>
      )}
    </li>
  )
}

function PerformerRow({ player, t }) {
  const name = player.playerName ?? player.registrationName
  const body = (
    <>
      <span className="player-avatar" aria-hidden="true">{initials(name)}</span>
      <span className="mini-list-body">
        <strong>{name}</strong>
        <span className="mini-list-meta">{t('detail.summaryPlayerMatchCount', { count: player.matchCount })}</span>
      </span>
      <span className="mini-list-stat">
        {t('detail.summaryPlayerWinRate', {
          winRate: player.winRate,
          wins: player.resultTotals.wins,
          draws: player.resultTotals.draws,
          losses: player.resultTotals.losses,
        })}
      </span>
    </>
  )

  return (
    <li>
      {player.canonicalPlayerId ? (
        <Link
          className="mini-list-item card"
          to={routePaths.playerDetails(
            player.canonicalPlayerId,
            `source=${encodeURIComponent(player.source)}&season=${encodeURIComponent(player.season)}`,
          )}
        >
          {body}
        </Link>
      ) : (
        <div className="mini-list-item card">{body}</div>
      )}
    </li>
  )
}

function ClubSummaryPanel({ club, competitions, players, season, returnSearch, onSeeMatches, onSeePlayers, t }) {
  const competitionsWithSource = useMemo(
    () => competitions.map((item) => ({ ...item, source: item.source ?? club.source })),
    [competitions, club.source],
  )
  const { data: matchGroups } = useClubMatches(club.id, competitionsWithSource)
  const record = useMemo(() => computeOverallRecord(competitions), [competitions])
  const taggedMatches = useMemo(
    () => (matchGroups ?? []).flatMap((group) => group.matches.map((match) => ({
      ...match,
      competition: group.competition,
    }))),
    [matchGroups],
  )
  const recentMatches = useMemo(() => getRecentMatches(taggedMatches, 4), [taggedMatches])
  const topPlayers = useMemo(() => getTopPlayers(players, 4), [players])
  const topPerformers = useMemo(() => getTopPerformers(players, 4), [players])
  const competitionNames = useMemo(
    () => new Set(competitions.map((item) => item.name)),
    [competitions],
  )

  return (
    <section className="club-detail-section" aria-labelledby="club-summary-title">
      <h2 id="club-summary-title">{t('detail.summaryTab')}</h2>
      <div className="stat-grid">
        <StatTile
          label={t('common.players')}
          value={players.length}
          subLabel={t('detail.summaryPlayersAllTime', { count: club.playerCount ?? 0 })}
        />
        <StatTile
          label={t('common.playedMatches')}
          value={record.matchCount}
          subLabel={t('detail.summaryMatchesAcrossSeasons', { count: club.seasons?.length ?? 0 })}
        />
        <StatTile
          label={t('common.winPercentage')}
          value={`${record.winRate}%`}
          subLabel={`${record.wins}${t('detail.winsAbbrev')} · ${record.draws}${t('detail.drawsAbbrev')} · ${record.losses}${t('detail.lossesAbbrev')}`}
        />
        <StatTile
          label={t('common.competitions')}
          value={competitionNames.size}
          subLabel={season ? t('detail.summaryThisSeason') : t('detail.allSeasons')}
        />
      </div>

      <div className="summary-grid">
        <div className="card-block card">
          <div className="card-block-header">
            <h3>{t('detail.summaryRecentMatches')}</h3>
            <button type="button" className="link-button" onClick={onSeeMatches}>{t('detail.seeAll')} →</button>
          </div>
          <RecordBar record={record} t={t} />
          {recentMatches.length === 0 ? (
            <p className="club-empty card">{t('detail.clubMatchesEmpty')}</p>
          ) : (
            <ul className="match-row-list">
              {recentMatches.map((match) => (
                <MatchRow key={match.id} match={match} returnSearch={returnSearch} t={t} />
              ))}
            </ul>
          )}
        </div>

        <div className="card-block card">
          <div className="card-block-header">
            <h3>{t('detail.summaryTopPlayers')}</h3>
            <button type="button" className="link-button" onClick={onSeePlayers}>{t('detail.seeAll')} →</button>
          </div>
          {topPlayers.length === 0 ? (
            <p className="club-empty card">{t('detail.registeredPlayersEmpty')}</p>
          ) : (
            <ul className="mini-list">
              {topPlayers.map((player) => <PlayerRow key={player.playerSeasonId} player={player} t={t} />)}
            </ul>
          )}
        </div>

        <div className="card-block card">
          <div className="card-block-header">
            <h3>{t('detail.summaryTopPerformer')}</h3>
            <button type="button" className="link-button" onClick={onSeePlayers}>{t('detail.seeAll')} →</button>
          </div>
          {topPerformers.length === 0 ? (
            <p className="club-empty card">{t('detail.summaryTopPerformerEmpty')}</p>
          ) : (
            <ul className="mini-list">
              {topPerformers.map((player) => <PerformerRow key={player.playerSeasonId} player={player} t={t} />)}
            </ul>
          )}
        </div>
      </div>
    </section>
  )
}

export default ClubSummaryPanel
