import { Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import SectionLabel from '../ui/SectionLabel.jsx'

function GlobalSearch() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setDebouncedQuery(query.trim())
    }, 300)

    return () => window.clearTimeout(timeoutId)
  }, [query])

  const canSearch = query.trim().length >= 2
  const helperMessage = canSearch
    ? 'Prem Enter o fes clic a Cercar.'
    : 'Introdueix com a mínim 2 caràcters.'

  function onSubmit(event) {
    event.preventDefault()
    const normalizedQuery = debouncedQuery || query.trim()

    if (normalizedQuery.length < 2) {
      return
    }

    navigate(`/cerca?q=${encodeURIComponent(normalizedQuery)}`)
  }

  return (
    <section>
      <SectionLabel>Cerca global</SectionLabel>
      <form className="global-search-form" onSubmit={onSubmit}>
        <div className="global-search-field">
          <label className="sr-only" htmlFor="global-search">
            Cerca clubs, jugadors o partits
          </label>
          <Search className="global-search-icon" size={16} strokeWidth={1.5} aria-hidden="true" />
          <input
            id="global-search"
            className="global-search-input"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Cerca clubs, jugadors o partits..."
            autoComplete="off"
            aria-describedby="global-search-help"
          />
          <p id="global-search-help" className="global-search-meta" aria-live="polite">
            {helperMessage}
          </p>
        </div>
        <button className="primary-button" type="submit" disabled={!canSearch}>
          Cercar
        </button>
      </form>
    </section>
  )
}

export default GlobalSearch
