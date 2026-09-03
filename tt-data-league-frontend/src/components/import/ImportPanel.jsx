import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/useAuth.js'
import { createImportPreview, getImportHistory, startImport, uploadImportFile } from '../../api/importJobs.js'
import { useImportSourceStatus } from '../../hooks/useImportSourceStatus.js'
import SectionLabel from '../ui/SectionLabel.jsx'
import LoadingState from '../ui/LoadingState.jsx'
import ErrorState from '../ui/ErrorState.jsx'
import EmptyState from '../ui/EmptyState.jsx'
import ImportFileControls from './ImportFileControls.jsx'
import ImportSourceSelector from './ImportSourceSelector.jsx'
import SeasonImportList from './SeasonImportList.jsx'
import ImportReportPanel from './ImportReportPanel.jsx'

const ACTION_MESSAGE_TIMEOUT = 20000

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
  const [history, setHistory] = useState({ data: [], loading: true, error: null })
  const [selectedSource, setSelectedSource] = useState('')
  const [selectedSeason, setSelectedSeason] = useState(null)
  const [file, setFile] = useState(null)
  const [job, setJob] = useState(null)
  const [uploadState, setUploadState] = useState({ status: 'idle', progress: 0, error: null })
  const [historyRefreshKey, setHistoryRefreshKey] = useState(0)
  const previousSourceStatuses = useRef(null)

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
      setJob((current) => current && !current.error ? null : current)
    }

    previousSourceStatuses.current = currentStatuses
  }, [sources.data])

  useEffect(() => {
    if (uploadState.status === 'idle' && !job) return undefined

    const timer = window.setTimeout(() => {
      setUploadState({ status: 'idle', progress: 0, error: null })
      setJob(null)
    }, ACTION_MESSAGE_TIMEOUT)

    return () => window.clearTimeout(timer)
  }, [job, uploadState.status])

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
  }, [clearSession, historyRefreshKey, token])

  const seasons = useMemo(() => {
    const map = new Map()
    history.data.forEach((item) => {
      const season = item.season ?? item.seasonId ?? item.seasonRange
      if (season !== undefined && !map.has(String(season))) map.set(String(season), item)
    })
    return [...map.entries()].map(([id, item]) => ({ ...item, id, season: id }))
  }, [history.data])

  const run = async (season, simulate = false) => {
    setSelectedSeason(season)
    try {
      const result = simulate
        ? await createImportPreview(token, { source: selectedSource, season: season.id, file: file?.name }, clearSession)
        : await startImport(token, season.jobId ?? season.id, clearSession)
      setJob(result)
    } catch (error) {
      setJob({ error })
    }
  }

  const loadFile = async () => {
    if (!isValidImportFile(file)) {
      setUploadState({ status: 'error', progress: 0, error: { validation: true } })
      return
    }

    setUploadState({ status: 'uploading', progress: 0, error: null })
    try {
      const result = await uploadImportFile(
        token,
        file,
        (progress) => setUploadState((current) => ({ ...current, progress })),
        clearSession,
      )
      setUploadState({ status: 'success', progress: 100, error: null })
      setJob(result)
      sources.refresh?.()
      setHistoryRefreshKey((value) => value + 1)
    } catch (error) {
      if (error.name === 'AbortError') return
      setUploadState({ status: 'error', progress: 0, error })
      setJob({ error })
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
        {history.loading ? <LoadingState>{t('importPanel.seasonsLoading')}</LoadingState>
          : history.error ? <ErrorState>{t(history.error.status === 403 ? 'importPanel.forbidden' : history.error.status === 401 ? 'importPanel.unauthorized' : 'importPanel.serverError')}</ErrorState>
            : seasons.length === 0 ? <EmptyState>{t('importPanel.seasonsEmpty')}</EmptyState>
              : <SeasonImportList seasons={seasons} onLoad={(season) => run(season)} onSimulate={(season) => run(season, true)} />}
        <ImportReportPanel season={selectedSeason} job={job} />
      </div>
    </section>
  )
}
