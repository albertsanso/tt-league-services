import SectionLabel from '../ui/SectionLabel.jsx'
import { useCommunityStats } from '../../hooks/useCommunityStats.js'
import StatCard from './StatCard.jsx'

function seasonStateLabel(state) {
  if (state === 'en_curs') {
    return 'En curs'
  }

  return 'No disponible'
}

function CommunityStats() {
  const { stats, loading, error } = useCommunityStats()

  return (
    <section>
      <SectionLabel>Estadístiques</SectionLabel>
      {error ? (
        <p className="stats-error" role="alert">
          {error}
        </p>
      ) : null}
      <div className="stats-grid">
        <StatCard
          label="Jugadors"
          value={stats.jugadors.total}
          delta={stats.jugadors.delta_temporada}
          loading={loading}
        />
        <StatCard
          label="Clubs"
          value={stats.clubs.total}
          delta={stats.clubs.delta_temporada}
          loading={loading}
        />
        <StatCard
          label="Partits"
          value={stats.partits.total}
          delta={stats.partits.delta_temporada}
          loading={loading}
        />
        <StatCard
          label="Temporada"
          value={stats.temporada.nom}
          status={seasonStateLabel(stats.temporada.estat)}
          loading={loading}
        />
      </div>
    </section>
  )
}

export default CommunityStats
