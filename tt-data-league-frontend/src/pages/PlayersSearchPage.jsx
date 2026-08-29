import { Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { routePaths } from '../config/routes.js'
import { usePlayerSearch } from '../hooks/usePlayers.js'

function PlayersSearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const urlQuery = searchParams.get('q') ?? ''
  const source = searchParams.get('source') ?? ''
  const [inputValue, setInputValue] = useState(urlQuery)
  const query = urlQuery.trim()
  const { data: players, loading, error, retry } = usePlayerSearch(query, source)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => setInputValue(urlQuery), 0)
    return () => window.clearTimeout(timeoutId)
  }, [urlQuery])
  useEffect(() => {
    const normalized = inputValue.trim()
    if (normalized === urlQuery) return undefined
    const timeoutId = window.setTimeout(() => {
      const next = new URLSearchParams(searchParams)
      if (normalized) next.set('q', normalized)
      else next.delete('q')
      setSearchParams(next)
    }, 300)
    return () => window.clearTimeout(timeoutId)
  }, [inputValue, searchParams, setSearchParams, urlQuery])

  function submit(event) {
    event.preventDefault()
    const normalized = inputValue.trim()
    if (normalized.length < 2) return
    const next = new URLSearchParams(searchParams)
    next.set('q', normalized)
    setSearchParams(next)
  }

  return (
    <section className="page-block" aria-labelledby="players-title">
      <div>
        <p className="section-label">Directori</p>
        <h1 id="players-title" className="page-title">Cerca de jugadors</h1>
        <p className="page-description">Troba un jugador i consulta la seva identitat canònica i trajectòria.</p>
      </div>
      <form className="club-search-form" onSubmit={submit}>
        <label className="sr-only" htmlFor="player-search">Nom del jugador</label>
        <div className="club-search-input-wrap">
          <Search size={17} aria-hidden="true" />
          <input
            id="player-search"
            className="club-search-input"
            type="search"
            value={inputValue}
            onChange={(event) => setInputValue(event.target.value)}
            placeholder="Cerca per nom..."
            autoComplete="off"
            aria-describedby="player-search-help"
          />
        </div>
        <label className="club-filter">
          <span>Font</span>
          <select
            aria-label="Filtra per font"
            value={source}
            onChange={(event) => {
              const next = new URLSearchParams(searchParams)
              if (event.target.value) next.set('source', event.target.value)
              else next.delete('source')
              setSearchParams(next)
            }}
          >
            <option value="">Totes les fonts</option>
            <option value="RFETM">RFETM</option>
            <option value="BCNESA">BCNESA</option>
            <option value="FCTT">FCTT</option>
          </select>
        </label>
        <button className="primary-button" type="submit" disabled={inputValue.trim().length < 2}>Cercar</button>
      </form>
      <p id="player-search-help" className="search-summary" aria-live="polite">
        Introdueix com a mínim 2 caràcters.
      </p>
      {!query ? (
        <p className="club-state card" role="status">Escriu el nom d’un jugador per començar la cerca.</p>
      ) : query.length < 2 ? (
        <p className="club-state card" role="alert">Introdueix com a mínim 2 caràcters.</p>
      ) : loading ? (
        <p className="club-state card" role="status" aria-live="polite">Cercant jugadors...</p>
      ) : error ? (
        <div className="club-state card" role="alert">
          <p>{error.status === 401 ? 'La sessió ha caducat.' : 'No s’han pogut carregar els jugadors.'}</p>
          <button className="secondary-button" type="button" onClick={retry}>Reintenta</button>
        </div>
      ) : players.length === 0 ? (
        <p className="club-state card" role="status">No s’han trobat jugadors per a «{query}».</p>
      ) : (
        <ul className="club-result-list" aria-label="Jugadors trobats">
          {players.map((player) => (
            <li key={player.id} className="club-result card">
              {player.canonicalPlayerId ? (
                <Link className="club-result-link" to={routePaths.playerDetails(player.canonicalPlayerId)}>
                  <span>
                    <strong>{player.name}</strong>
                    <span className="club-source">Font: {player.source}</span>
                    {player.canonicalPlayerName && player.canonicalPlayerName !== player.name ? (
                      <span className="club-source">Identitat canònica: {player.canonicalPlayerName}</span>
                    ) : null}
                  </span>
                  <span aria-hidden="true">→</span>
                </Link>
              ) : (
                <div className="club-result-link">
                  <span>
                    <strong>{player.name}</strong>
                    <span className="club-source">Font: {player.source}</span>
                    <span className="club-source">Identitat canònica pendent</span>
                  </span>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default PlayersSearchPage
