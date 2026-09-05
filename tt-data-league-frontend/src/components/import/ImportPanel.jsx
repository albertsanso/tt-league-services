import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/useAuth.js'
import { createImportPreview, getImportHistory, startImport, uploadImportFile } from '../../api/importJobs.js'
import { normalizeImportPreview } from '../../hooks/useImportPreviewStatus.js'
import { normalizeImportProcess } from '../../hooks/useImportProcessResult.js'
import { useImportSourceStatus } from '../../hooks/useImportSourceStatus.js'
import { useImportResources } from '../../hooks/useImportResources.js'
import SectionLabel from '../ui/SectionLabel.jsx'
import ImportFileControls from './ImportFileControls.jsx'
import ImportSourceSelector from './ImportSourceSelector.jsx'
import ImportResourceList from './ImportResourceList.jsx'
import SeasonImportList from './SeasonImportList.jsx'
import ImportPreviewWorkspace from './ImportPreviewWorkspace.jsx'
import ImportProcessWorkspace from './ImportProcessWorkspace.jsx'
import ImportReportPanel from './ImportReportPanel.jsx'

const ACTION_MESSAGE_TIMEOUT = 20000

function emptyPreviewState() {
  return {
    resource: null,
    loading: false,
    result: null,
    error: null,
  }
}

function isValidImportFile(candidate) {
  return candidate
    && candidate.size > 0
    && (candidate.type === 'application/zip'
      || candidate.type === 'application/x-zip-compressed'
      || /\.zip$/i.test(candidate.name ?? ''))
}

export default function ImportPanel() {
  const { t } = useTranslation()
  const { token, clearSession } = useAuth()
  const sources = useImportSourceStatus()
  const [selectedSource, setSelectedSource] = useState('')
  const [selectedSeason, setSelectedSeason] = useState(null)
  const [file, setFile] = useState(null)
  const [previewState, setPreviewState] = useState(emptyPreviewState)
  const [importState, setImportState] = useState({ resource: null, loading: false, result: null, error: null })
  const [history, setHistory] = useState({ data: [], loading: true, error: null })
  const [uploadState, setUploadState] = useState({ status: 'idle', progress: 0, error: null })
  const previousSourceStatuses = useRef(null)
  const resources = useImportResources(selectedSource)

  useEffect(() => {
    const controller = new AbortController()
    getImportHistory(token, '', clearSession, controller.signal)
      .then((payload) => {
        const data = Array.isArray(payload) ? payload : (payload?.items ?? payload?.content ?? [])
        setHistory({ data: Array.isArray(data) ? data : [], loading: false, error: null })
      })
      .catch((error) => {
        if (error.name !== 'AbortError') setHistory({ data: [], loading: false, error })
      })
    return () => controller.abort()
  }, [clearSession, token])

  const seasons = useMemo(() => {
    const map = new Map()
    history.data.forEach((item) => {
      const season = item.season ?? item.seasonId ?? item.seasonRange
      if (season !== undefined && !map.has(String(season))) map.set(String(season), item)
    })
    return [...map.entries()].map(([id, item]) => ({ ...item, id, season: id }))
  }, [history.data])

  useEffect(() => {
    const currentStatuses = new Map(sources.data.map((source) => [source.id ?? source.code, source.status]))
    const previousStatuses = previousSourceStatuses.current
    const sourceCompletedAction = previousStatuses
      && [...currentStatuses].some(([id, status]) => (
        ['available', 'error'].includes(status)
        && previousStatuses.get(id) !== status
      ))

    if (sourceCompletedAction) {
      setUploadState((current) => current.status === 'success'
        ? { status: 'idle', progress: 0, error: null }
        : current)
    }

    previousSourceStatuses.current = currentStatuses
  }, [sources.data])

  useEffect(() => {
    if (uploadState.status === 'idle') return undefined

    const timer = window.setTimeout(() => {
      setUploadState({ status: 'idle', progress: 0, error: null })
    }, ACTION_MESSAGE_TIMEOUT)

    return () => window.clearTimeout(timer)
  }, [uploadState.status])

  const startPreview = async (resource) => {
    if (!resource) return
    try {
      setSelectedSeason(resource)
      setImportState({ resource: null, loading: false, result: null, error: null })
      setPreviewState({ ...emptyPreviewState(), resource, loading: true })
      const result = await createImportPreview(token, resource.jobId ?? resource.id, clearSession)
      setPreviewState({ ...emptyPreviewState(), resource, result: normalizeImportPreview(result) })
    } catch (error) {
      setPreviewState({ ...emptyPreviewState(), resource, error })
    }
  }

  const proceedFromPreview = async (resource) => {
    await startProcess(resource)
  }

  const startProcess = async (resource) => {
    if (!resource) return
    setSelectedSeason(resource)
    setPreviewState(emptyPreviewState())
    setImportState({ resource, loading: true, result: null, error: null })
    try {
      const result = await startImport(token, resource.jobId ?? resource.id, clearSession)
      setImportState({ resource, loading: false, result: normalizeImportProcess(result), error: null })
    } catch (error) {
      setImportState({ resource, loading: false, result: null, error })
    }
  }

  const clearImport = () => {
    setImportState({ resource: null, loading: false, result: null, error: null })
    setSelectedSeason(null)
  }

  const run = async (season, simulate = false) => {
    setSelectedSeason(season)
    if (simulate) {
      await startPreview(season)
      return
    }
    await startProcess(season)
  }

  const runResource = async (resource, simulate = false) => {
    setSelectedSeason(resource)
    if (simulate) {
      await startPreview(resource)
      return
    }
    await startProcess(resource)
  }

  const loadFile = async () => {
    if (!isValidImportFile(file)) {
      setUploadState({ status: 'error', progress: 0, error: { validation: true } })
      return
    }

    setUploadState({ status: 'uploading', progress: 0, error: null })
    try {
      await uploadImportFile(
        token,
        file,
        (progress) => setUploadState((current) => ({ ...current, progress })),
        clearSession,
      )
      setUploadState({ status: 'success', progress: 100, error: null })
      sources.refresh?.()
      resources.refresh()
      getImportHistory(token, '', clearSession)
        .then((payload) => {
          const data = Array.isArray(payload) ? payload : (payload?.items ?? payload?.content ?? [])
          setHistory({ data: Array.isArray(data) ? data : [], loading: false, error: null })
        })
        .catch((error) => setHistory({ data: [], loading: false, error }))
    } catch (error) {
      if (error.name === 'AbortError') return
      setUploadState({ status: 'error', progress: 0, error })
    }
  }

  const handleFileChange = (nextFile) => {
    setFile(nextFile)
    setUploadState({ status: 'idle', progress: 0, error: null })
  }

  return (
    <section className="page-block import-panel" aria-labelledby="import-panel-title">
      <SectionLabel>{t('importPanel.title')}</SectionLabel>
      <h1 id="import-panel-title" className="page-title">{t('importPanel.title')}</h1>
      <p className="page-description">{t('importPanel.description')}</p>
      <ImportFileControls file={file} onFileChange={handleFileChange} onLoad={loadFile} uploadState={uploadState} />
      <div className="import-panel-grid">
        <ImportSourceSelector sources={sources} selected={selectedSource} onSelect={setSelectedSource} />
        <div>
          {!selectedSource ? <p>{t('importPanel.chooseSource')}</p>
          : resources.loading ? <p role="status">{t('importPanel.resourcesLoading')}</p>
          : resources.error ? <div role="alert">{t(resources.error.status === 403 ? 'importPanel.forbidden' : resources.error.status === 401 ? 'importPanel.unauthorized' : 'importPanel.serverError')} <button type="button" onClick={resources.retry}>{t('common.retry')}</button></div>
            : resources.data.length === 0 ? <p>{t('importPanel.resourcesEmpty')}</p>
              : <ImportResourceList resources={resources.data} onSimulate={(resource) => runResource(resource, true)} onImport={(resource) => runResource(resource)} />}
          {history.loading ? <p role="status">{t('importPanel.seasonsLoading')}</p>
            : history.error ? <p role="alert">{t('importPanel.serverError')}</p>
              : seasons.length > 0 ? <SeasonImportList seasons={seasons} onLoad={(season) => run(season)} onSimulate={(season) => run(season, true)} /> : null}
        </div>
        {importState.resource
          ? <ImportProcessWorkspace
              resource={importState.resource}
              process={importState}
              onRetry={startProcess}
              onBackToResources={clearImport}
            />
          : previewState.resource
          ? <ImportPreviewWorkspace
              resource={previewState.resource}
              preview={previewState}
              onRetry={startPreview}
              onProceed={proceedFromPreview}
            />
          : <ImportReportPanel season={selectedSeason} />}
      </div>
    </section>
  )
}
