import { Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { useClubSearch } from '../hooks/useClubs.js'

function ClubsSearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const urlQuery = searchParams.get('q') ?? ''
  const [inputValue, setInputValue] = useState(urlQuery)
  const [validationError, setValidationError] = useState('')
  const query = urlQuery.trim()
  const { data: clubs, loading, error, retry } = useClubSearch(query)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => setInputValue(urlQuery), 0)
    return () => window.clearTimeout(timeoutId)
  }, [urlQuery])

  useEffect(() => {
    const normalizedValue = inputValue.trim()
    if (normalizedValue === urlQuery) {
      return undefined
    }

    const timeoutId = window.setTimeout(() => {
      if (normalizedValue) {
        setSearchParams({ q: normalizedValue })
      } else {
        setSearchParams({})
      }
    }, 300)

    return () => window.clearTimeout(timeoutId)
  }, [inputValue, setSearchParams, urlQuery])

  function handleSubmit(event) {
    event.preventDefault()
    const normalizedValue = inputValue.trim()
    if (normalizedValue.length < 2) {
      setValidationError('Introdueix com a mínim 2 caràcters.')
      return
    }

    setValidationError('')
    setSearchParams({ q: normalizedValue })
  }

  const hasQuery = query.length > 0
  const canSearch = inputValue.trim().length >= 2

  return (
    <section className="page-block" aria-labelledby="clubs-title">
      <div>
        <p className="section-label">Directori</p>
        <h1 id="clubs-title" className="page-title">Cerca de clubs</h1>
        <p className="page-description">
          Troba un club per consultar la seva identitat, equips i competicions.
        </p>
      </div>

      <form className="club-search-form" onSubmit={handleSubmit}>
        <label className="sr-only" htmlFor="club-search">Nom del club</label>
        <div className="club-search-input-wrap">
          <Search size={17} aria-hidden="true" />
          <input
            id="club-search"
            className="club-search-input"
            type="search"
            value={inputValue}
            onChange={(event) => {
              setInputValue(event.target.value)
              setValidationError('')
            }}
            placeholder="Cerca per nom..."
            autoComplete="off"
            aria-describedby="club-search-help"
          />
        </div>
        <button className="primary-button" type="submit" disabled={!canSearch}>
          Cercar
        </button>
      </form>
      <p id="club-search-help" className="search-summary" aria-live="polite">
        {validationError || 'Introdueix com a mínim 2 caràcters.'}
      </p>

      {!hasQuery ? (
        <p className="club-state card" role="status">
          Escriu el nom d’un club per començar la cerca.
        </p>
      ) : query.length < 2 ? (
        <p className="club-state card" role="alert">{'Introdueix com a mínim 2 caràcters.'}</p>
      ) : loading ? (
        <p className="club-state card" role="status" aria-live="polite">Cercant clubs...</p>
      ) : error ? (
        <div className="club-state card" role="alert">
          <p>No s’han pogut carregar els clubs. Torna-ho a provar.</p>
          <button className="secondary-button" type="button" onClick={retry}>Reintenta</button>
        </div>
      ) : clubs.length === 0 ? (
        <p className="club-state card" role="status">
          No s’han trobat clubs per a «{query}».
        </p>
      ) : (
        <ul className="club-result-list" aria-label="Clubs trobats">
          {clubs.map((club) => (
            <li key={club.id} className="club-result card">
              <Link
                to={`${routePaths.clubDetails(club.id)}?season=all&source=all`}
                className="club-result-link"
              >
                <span>
                  <strong>{club.name}</strong>
                  <span className="club-source">Font: {club.source}</span>
                </span>
                <span aria-hidden="true">→</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default ClubsSearchPage
