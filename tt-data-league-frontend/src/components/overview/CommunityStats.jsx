import SectionLabel from '../ui/SectionLabel.jsx'
import { useCommunityStats } from '../../hooks/useCommunityStats.js'
import StatCard from './StatCard.jsx'
import { useTranslation } from 'react-i18next'

function seasonStateLabel(state, t) {
  if (state === 'en_curs') {
    return t('overview.seasonInProgress')
  }

  return t('overview.seasonUnavailable')
}

function CommunityStats() {
  const { stats, loading, error } = useCommunityStats()
  const { t } = useTranslation()

  return (
    <section>
      <SectionLabel>{t('overview.statistics')}</SectionLabel>
      {error ? (
        <p className="stats-error" role="alert">
          {t('overview.statsError')}
        </p>
      ) : null}
      <div className="stats-grid">
        <StatCard
          label={t('common.players')}
          value={stats.jugadors.total}
          delta={stats.jugadors.delta_temporada}
          loading={loading}
        />
        <StatCard
          label={t('common.clubs')}
          value={stats.clubs.total}
          delta={stats.clubs.delta_temporada}
          loading={loading}
        />
        <StatCard
          label={t('common.matches')}
          value={stats.partits.total}
          delta={stats.partits.delta_temporada}
          loading={loading}
        />
        <StatCard
          label={t('common.season')}
          value={stats.temporada.nom}
          status={seasonStateLabel(stats.temporada.estat, t)}
          loading={loading}
        />
      </div>
    </section>
  )
}

export default CommunityStats
