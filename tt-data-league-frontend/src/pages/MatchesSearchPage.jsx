import SectionLabel from '../components/ui/SectionLabel.jsx'

function MatchesSearchPage() {
  return (
    <section className="page-block">
      <h1 className="page-title">Cerca de partits</h1>
      <p className="page-description">
        Navega resultats oficials per jornada, competició i enfrontaments directes.
      </p>

      <SectionLabel>Mòdul en construcció</SectionLabel>
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
