import SectionLabel from '../ui/SectionLabel.jsx'
import { useCommunityStats } from '../../hooks/useCommunityStats.js'
import StatCard from './StatCard.jsx'
import { useTranslation } from 'react-i18next'

function seasonStateLabel(status, t) {
  if (status === 'IN_PROGRESS') {
    return t('overview.seasonInProgress')
  }

  return t('overview.seasonUnavailable')
}

function isEmpty(stats) {
  return stats.players.total === 0 && stats.clubs.total === 0 && stats.matches.total === 0
}

function CommunityStats() {
  const { stats, loading, error, unauthorized } = useCommunityStats()
  const { t } = useTranslation()

  return (
    <section>
      <SectionLabel>{t('overview.statistics')}</SectionLabel>
      {error ? (
        <p className="stats-error" role="alert">
          {unauthorized ? t('overview.statsUnauthorized') : t('overview.statsError')}
        </p>
      ) : null}
      {!loading && !error && isEmpty(stats) ? (
        <p className="stats-empty" role="status">
          {t('overview.statsEmpty')}
        </p>
      ) : null}
      <div className="stats-grid">
        <StatCard
          label={t('common.players')}
          value={stats.players.total}
          loading={loading}
        />
        <StatCard
          label={t('common.clubs')}
          value={stats.clubs.total}
          loading={loading}
        />
        <StatCard
          label={t('common.matches')}
          value={stats.matches.total}
          loading={loading}
        />
        <StatCard
          label={t('common.season')}
          value={stats.season.name}
          status={seasonStateLabel(stats.season.status, t)}
          loading={loading}
        />
      </div>
    </section>
  )
}

export default CommunityStats
