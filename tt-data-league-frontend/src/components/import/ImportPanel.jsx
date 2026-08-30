import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useImportSources } from '../../hooks/useImportSources.js'
import { useAuth } from '../../context/useAuth.js'
import {
  cancelImport,
  createImportPreview,
  getImportHistory,
  rollbackImport,
  startImport,
  validateImport,
} from '../../api/importJobs.js'

function displayError(error, t) {
  if (!error) return ''
  if (error.status === 401) return t('importPanel.unauthorized')
  if (error.status === 403) return t('importPanel.forbidden')
  return error.message || t('importPanel.serverError')
}

export default function ImportPanel() {
  const { t } = useTranslation()
  const { token, clearSession } = useAuth()
  const { data: sources, loading, error, retry } = useImportSources()
  const [source, setSource] = useState('')
  const [job, setJob] = useState(null)
  const [history, setHistory] = useState([])
  const [query, setQuery] = useState('')
  const [actionError, setActionError] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    getImportHistory(token, query, clearSession, controller.signal)
      .then(setHistory)
      .catch((caught) => {
        if (caught.name !== 'AbortError') setActionError(caught)
      })
    return () => controller.abort()
  }, [clearSession, query, token])

  const run = async (action) => {
    setBusy(true)
    setActionError(null)
    try {
      const result = await action()
      setJob(result)
      setHistory((items) => [result, ...items.filter((item) => item.id !== result.id)])
    } catch (caught) {
      setActionError(caught)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="page-block" aria-labelledby="import-panel-title">
      <h1 id="import-panel-title" className="page-title">{t('importPanel.title')}</h1>
      <p className="page-description">{t('importPanel.description')}</p>
      <section aria-labelledby="import-sources-heading">
        <h2 id="import-sources-heading">{t('importPanel.sourcesTitle')}</h2>
        {loading && <p aria-live="polite" aria-busy="true">{t('importPanel.loading')}</p>}
        {!loading && error && <div role="alert"><p>{displayError(error, t)}</p><button type="button" onClick={retry}>{t('common.retry')}</button></div>}
        {!loading && !error && sources.length === 0 && <p>{t('importPanel.sourcesEmpty')}</p>}
        {!loading && !error && sources.length > 0 && <ul aria-label={t('importPanel.sourcesTitle')}>
          {sources.map((item) => <li key={item.id}><strong>{item.label}</strong> — {t(`importPanel.sourceDescription.${item.id}`, { defaultValue: item.id })}</li>)}
        </ul>}
      </section>
      <section aria-labelledby="import-actions-heading">
        <h2 id="import-actions-heading">{t('importPanel.actionsTitle')}</h2>
        <label htmlFor="import-source">{t('importPanel.sourceLabel')}</label>
        <select id="import-source" value={source} onChange={(event) => setSource(event.target.value)}>
          <option value="">{t('importPanel.chooseSource')}</option>
          {sources.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
        </select>
        <button type="button" disabled={!source || busy} onClick={() => run(() => createImportPreview(token, { source, mappingVersion: '1', preview: true }, clearSession))}>{t('importPanel.preview')}</button>
        {actionError && <p role="alert">{displayError(actionError, t)}</p>}
        {job && <div aria-live="polite">
          <p>{t('importPanel.status')}: {job.status}</p>
          <button type="button" disabled={busy || job.status !== 'PREVIEW'} onClick={() => run(() => validateImport(token, job.id, clearSession))}>{t('importPanel.validate')}</button>
          <button type="button" disabled={busy || job.status !== 'READY'} onClick={() => run(() => startImport(token, job.id, clearSession))}>{t('importPanel.start')}</button>
          <button type="button" disabled={busy || ['SUCCEEDED', 'ROLLED_BACK'].includes(job.status)} onClick={() => run(() => cancelImport(token, job.id, clearSession))}>{t('importPanel.cancel')}</button>
          <button type="button" disabled={busy || job.status !== 'SUCCEEDED'} onClick={() => run(() => rollbackImport(token, job.id, clearSession))}>{t('importPanel.rollback')}</button>
        </div>}
      </section>
      <section aria-labelledby="import-history-heading">
        <h2 id="import-history-heading">{t('importPanel.historyTitle')}</h2>
        <label htmlFor="import-history-search">{t('importPanel.searchLabel')}</label>
        <input id="import-history-search" value={query} onChange={(event) => setQuery(event.target.value)} />
        {history.length === 0 ? <p>{t('importPanel.historyEmpty')}</p> : <ul>{history.map((item) => <li key={item.id}>{item.source} — {item.status}</li>)}</ul>}
      </section>
    </section>
  )
}
