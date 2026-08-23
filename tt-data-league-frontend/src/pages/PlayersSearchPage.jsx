import SectionLabel from '../components/ui/SectionLabel.jsx'
import { useSearchParams } from 'react-router-dom'

function PlayersSearchPage() {
  const [searchParams] = useSearchParams()
  const clubId = searchParams.get('clubId')

  return (
    <section className="page-block">
      <h1 className="page-title">Cerca de jugadors</h1>
      <p className="page-description">
        Espai dedicat a consultar fitxes de jugadors i comparar rendiment esportiu.
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
          En aquesta secció hi haurà cerca per nom, llicència i evolució de resultats
          per temporada.
        </p>
      </article>
    </section>
  )
}

export default PlayersSearchPage
