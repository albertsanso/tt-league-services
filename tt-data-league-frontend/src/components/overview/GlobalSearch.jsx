import { Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import SectionLabel from '../ui/SectionLabel.jsx'
import { useTranslation } from 'react-i18next'

function GlobalSearch() {
  const navigate = useNavigate()
  const { t } = useTranslation()
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
    ? t('overview.searchHint')
    : t('overview.minCharacters')

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
      <SectionLabel>{t('overview.globalSearch')}</SectionLabel>
      <form className="global-search-form" onSubmit={onSubmit}>
        <div className="global-search-field">
          <label className="sr-only" htmlFor="global-search">
            {t('overview.searchLabel')}
          </label>
          <Search className="global-search-icon" size={16} strokeWidth={1.5} aria-hidden="true" />
          <input
            id="global-search"
            className="global-search-input"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t('overview.searchPlaceholder')}
            autoComplete="off"
            aria-describedby="global-search-help"
          />
          <p id="global-search-help" className="global-search-meta" aria-live="polite">
            {helperMessage}
          </p>
        </div>
        <button className="primary-button" type="submit" disabled={!canSearch}>
          {t('common.search')}
        </button>
      </form>
    </section>
  )
}

export default GlobalSearch
