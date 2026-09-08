import { useState } from 'react'
import { Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useClubSearch, useConsolidateClubs } from '../hooks/useClubs.js'

function longestName(clubs) {
  return clubs.reduce(
    (longest, club) => (club.name.length > longest.length ? club.name : longest),
    clubs[0]?.name ?? '',
  )
}

function ClubsConsolidationPanel() {
  const { t } = useTranslation()
  const [inputValue, setInputValue] = useState('')
  const [query, setQuery] = useState('')
  const [validationError, setValidationError] = useState('')
  const [selectedIds, setSelectedIds] = useState(new Set())
  const [dialogOpen, setDialogOpen] = useState(false)
  const [canonicalName, setCanonicalName] = useState('')
  const [primaryClubId, setPrimaryClubId] = useState(null)
  const [mutationError, setMutationError] = useState(null)
  const [mutationSuccess, setMutationSuccess] = useState(null)

  const { data: clubs, loading, error, retry } = useClubSearch(query)
  const { consolidate, loading: consolidating } = useConsolidateClubs()

  const results = clubs ?? []
  const canConsolidate = selectedIds.size >= 2
  const allSelected = results.length > 0 && results.every((club) => selectedIds.has(club.id))
  const selectedClubs = results.filter((club) => selectedIds.has(club.id))

  function handleSearchSubmit(event) {
    event.preventDefault()
    const normalizedValue = inputValue.trim()
    if (normalizedValue.length < 2) {
      setValidationError(t('overview.minCharacters'))
      return
    }
    setValidationError('')
    setQuery(normalizedValue)
    setSelectedIds(new Set())
  }

  function toggleSelected(clubId) {
    setSelectedIds((previous) => {
      const next = new Set(previous)
      if (next.has(clubId)) {
        next.delete(clubId)
      } else {
        next.add(clubId)
      }
      return next
    })
  }

  function toggleSelectAll() {
    setSelectedIds((previous) => {
      if (results.length > 0 && results.every((club) => previous.has(club.id))) {
        return new Set()
      }
      return new Set(results.map((club) => club.id))
    })
  }

  function openDialog() {
    if (!canConsolidate) return
    setCanonicalName(longestName(selectedClubs))
    setPrimaryClubId(selectedClubs[0]?.id ?? null)
    setMutationError(null)
    setMutationSuccess(null)
    setDialogOpen(true)
  }

  function closeDialog() {
    setDialogOpen(false)
  }

  async function handleConfirm() {
    try {
      await consolidate({
        clubIds: [...selectedIds],
        canonicalName,
        primaryClubId,
      })
      setDialogOpen(false)
      setSelectedIds(new Set())
      setMutationSuccess(t('clubsConsolidation.success'))
      setMutationError(null)
      retry()
    } catch (mutationErr) {
      setMutationError(mutationErr?.message || t('clubsConsolidation.error'))
      setMutationSuccess(null)
    }
  }

  const canConfirm = canonicalName.trim().length >= 2 && Boolean(primaryClubId)

  return (
    <section className="page-block" aria-labelledby="clubs-consolidation-title">
      <div>
        <p className="section-label">{t('navigation.sectionAdministration')}</p>
        <h1 id="clubs-consolidation-title" className="page-title">{t('clubsConsolidation.title')}</h1>
        <p className="page-description">{t('clubsConsolidation.description')}</p>
      </div>

      {mutationSuccess && (
        <p className="form-success" role="status" aria-live="polite">{mutationSuccess}</p>
      )}
      {mutationError && (
        <p className="form-error" role="alert">{mutationError}</p>
      )}

      <form className="club-search-form" onSubmit={handleSearchSubmit}>
        <label className="sr-only" htmlFor="clubs-consolidation-search">{t('search.fieldClub')}</label>
        <div className="club-search-input-wrap">
          <Search size={17} aria-hidden="true" />
          <input
            id="clubs-consolidation-search"
            className="club-search-input"
            type="search"
            value={inputValue}
            onChange={(event) => {
              setInputValue(event.target.value)
              setValidationError('')
            }}
            placeholder={t('search.byName')}
            autoComplete="off"
            aria-describedby="clubs-consolidation-search-help"
          />
        </div>
        <button className="primary-button" type="submit" disabled={inputValue.trim().length < 2}>
          {t('common.search')}
        </button>
      </form>
      <p id="clubs-consolidation-search-help" className="search-summary" aria-live="polite">
        {validationError || t('overview.minCharacters')}
      </p>

      <div className="club-action-row">
        <label className="checkbox-option">
          <input
            type="checkbox"
            checked={allSelected}
            onChange={toggleSelectAll}
            disabled={results.length === 0}
            aria-label={t('clubsConsolidation.selectAll')}
          />
          {t('clubsConsolidation.selectAll')}
        </label>
        <span className="search-summary">
          {t('clubsConsolidation.selectedCount', { count: selectedIds.size })}
        </span>
        <button
          className="primary-button"
          type="button"
          onClick={openDialog}
          disabled={!canConsolidate}
          aria-label={t('clubsConsolidation.consolidateAriaLabel')}
        >
          {t('clubsConsolidation.consolidate')}
        </button>
      </div>

      {query.length === 0 ? (
        <p className="club-state card" role="status">{t('search.writeClub')}</p>
      ) : loading ? (
        <p className="club-state card" role="status" aria-live="polite">{t('search.searchingClubs')}</p>
      ) : error ? (
        <div className="club-state card" role="alert">
          <p>{t('search.clubsLoadError')}</p>
          <button className="secondary-button" type="button" onClick={retry}>{t('common.retry')}</button>
        </div>
      ) : results.length === 0 ? (
        <p className="club-state card" role="status">{t('search.noClubs', { query })}</p>
      ) : (
        <ul className="club-result-list" aria-label={t('search.resultsClubs')}>
          {results.map((club) => (
            <li key={club.id} className="club-result card">
              <label className="checkbox-option">
                <input
                  type="checkbox"
                  checked={selectedIds.has(club.id)}
                  onChange={() => toggleSelected(club.id)}
                  aria-label={t('clubsConsolidation.selectClub', { name: club.name })}
                />
                <span>
                  <strong>{club.name}</strong>
                  <span className="club-source">
                    {t('common.source')}: {club.sources?.length ? club.sources.join(', ') : club.source}
                  </span>
                </span>
              </label>
            </li>
          ))}
        </ul>
      )}

      {dialogOpen && (
        <div className="confirm-dialog card" role="dialog" aria-labelledby="consolidate-dialog-title">
          <p id="consolidate-dialog-title">{t('clubsConsolidation.dialogTitle')}</p>

          <label className="auth-field" htmlFor="consolidate-canonical-name">
            {t('clubsConsolidation.canonicalNameLabel')}
            <input
              id="consolidate-canonical-name"
              type="text"
              value={canonicalName}
              onChange={(event) => setCanonicalName(event.target.value)}
              minLength={2}
              maxLength={255}
              required
            />
          </label>

          <fieldset className="user-form-roles">
            <legend>{t('clubsConsolidation.primaryClubLabel')}</legend>
            {selectedClubs.map((club) => (
              <label key={club.id} className="radio-option">
                <input
                  type="radio"
                  name="consolidate-primary-club"
                  checked={primaryClubId === club.id}
                  onChange={() => setPrimaryClubId(club.id)}
                />
                {club.name}
              </label>
            ))}
          </fieldset>

          <div className="confirm-dialog-actions">
            <button className="secondary-button" type="button" onClick={closeDialog} disabled={consolidating}>
              {t('common.cancel')}
            </button>
            <button
              className="primary-button"
              type="button"
              onClick={handleConfirm}
              disabled={!canConfirm || consolidating}
            >
              {consolidating ? t('clubsConsolidation.consolidating') : t('clubsConsolidation.confirm')}
            </button>
          </div>
        </div>
      )}
    </section>
  )
}

export default ClubsConsolidationPanel
