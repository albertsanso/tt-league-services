import SectionLabel from '../components/ui/SectionLabel.jsx'

function ClubsSearchPage() {
  return (
    <section className="page-block">
      <h1 className="page-title">Cerca de clubs</h1>
      <p className="page-description">
        Punt d&apos;entrada per consultar clubs, equips federats i els seus resultats.
      </p>

      <SectionLabel>Mòdul en construcció</SectionLabel>
      <article className="placeholder-panel card">
        <h2>Properament</h2>
        <p>
          Aquesta vista inclourà filtres per categoria, província i temporada per
          accelerar la cerca de clubs.
        </p>
      </article>
    </section>
  )
}

export default ClubsSearchPage
