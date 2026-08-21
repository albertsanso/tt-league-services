import { useSearchParams } from 'react-router-dom'
import Badge from '../components/ui/Badge.jsx'
import SectionLabel from '../components/ui/SectionLabel.jsx'

function SearchResultsPage() {
  const [searchParams] = useSearchParams()
  const query = (searchParams.get('q') || '').trim()
  const hasMinimumQuery = query.length >= 2

  return (
    <section className="page-block">
      <h1 className="page-title">Resultats de cerca</h1>
      <p className="search-summary">
        Consulta transversal de clubs, jugadors i partits a partir del teu criteri.
      </p>

      <SectionLabel>Consulta actual</SectionLabel>
      <article className="placeholder-panel card">
        {hasMinimumQuery ? (
          <>
            <p className="search-summary">
              Cercant per: <span className="search-term">{query}</span>
            </p>
            <p>
              El mòdul de resultats s&apos;integrarà amb <code>/api/cerca</code> en fases
              posteriors.
            </p>
          </>
        ) : (
          <>
            <p>Introdueix un terme de cerca de 2 caràcters o més.</p>
            <p>
              Exemple: <span className="search-term">terrassa</span>{' '}
              <Badge tone="warning">mínim 2</Badge>
            </p>
          </>
        )}
      </article>
    </section>
  )
}

export default SearchResultsPage
