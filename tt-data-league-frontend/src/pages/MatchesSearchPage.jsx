import SectionLabel from '../components/ui/SectionLabel.jsx'
import { useSearchParams } from 'react-router-dom'

function MatchesSearchPage() {
  const [searchParams] = useSearchParams()
  const clubId = searchParams.get('clubId')

  return (
    <section className="page-block">
      <h1 className="page-title">Cerca de partits</h1>
      <p className="page-description">
        Navega resultats oficials per jornada, competició i enfrontaments directes.
      </p>

      <SectionLabel>Mòdul en construcció</SectionLabel>
      {clubId ? (
        <p className="selected-club-filter" role="status">
          Filtre de club actiu · ID: <strong>{clubId}</strong>
        </p>
      ) : null}
      <article className="placeholder-panel card">
        <h2>Properament</h2>
        <p>
          Aquesta pàgina permetrà filtrar partits per data, lliga i jugadors, amb accés
          directe a l&apos;històric complet.
        </p>
      </article>
    </section>
  )
}

export default MatchesSearchPage
