import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/useAuth.js'
import { createImportPreview, startImport, uploadImportFile } from '../../api/importJobs.js'
import { normalizeImportPreview } from '../../hooks/useImportPreviewStatus.js'
import { isActiveImportRunStatus, useImportProcessStatus } from '../../hooks/useImportProcessStatus.js'
import { useImportSourceStatus } from '../../hooks/useImportSourceStatus.js'
import { useImportResources } from '../../hooks/useImportResources.js'
import SectionLabel from '../ui/SectionLabel.jsx'
import ImportFileControls from './ImportFileControls.jsx'
import ImportSourceSelector from './ImportSourceSelector.jsx'
import ImportResourceList from './ImportResourceList.jsx'
import ImportPreviewWorkspace from './ImportPreviewWorkspace.jsx'
import ImportProcessWorkspace from './ImportProcessWorkspace.jsx'
import ImportReportPanel from './ImportReportPanel.jsx'

const ACTION_MESSAGE_TIMEOUT = 20000
const ACTIVE_RUN_STORAGE_KEY = 'import-panel:active-run'
const UPLOAD_POLL_INTERVAL_MS = 2000
const UPLOAD_POLL_MAX_ATTEMPTS = 10

function emptyPreviewState() {
  return {
    resource: null,
    loading: false,
    result: null,
    error: null,
  }
}

function emptyImportState() {
  return {
    source: null,
    resource: null,
    submitting: false,
    runId: null,
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

function readStoredRun() {
  try {
    const raw = window.sessionStorage.getItem(ACTIVE_RUN_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed?.runId || !parsed?.resource) return null
    return parsed
  } catch {
    return null
  }
}

function writeStoredRun(source, resource, runId) {
  try {
    if (!runId || !resource) {
      window.sessionStorage.removeItem(ACTIVE_RUN_STORAGE_KEY)
      return
    }
    window.sessionStorage.setItem(ACTIVE_RUN_STORAGE_KEY, JSON.stringify({ source, resource, runId }))
  } catch {
    // best-effort UI convenience; ignore storage failures (e.g. private browsing)
  }
}

export default function ImportPanel() {
  const { t } = useTranslation()
  const { token, clearSession } = useAuth()
  const sources = useImportSourceStatus()
  const [selectedSource, setSelectedSource] = useState(() => readStoredRun()?.source ?? '')
  const [selectedSeason, setSelectedSeason] = useState(null)
  const [file, setFile] = useState(null)
  const [previewState, setPreviewState] = useState(emptyPreviewState)
  const [importState, setImportState] = useState(() => {
    const stored = readStoredRun()
    return stored
      ? { source: stored.source, resource: stored.resource, submitting: false, runId: stored.runId, error: null }
      : emptyImportState()
  })
  const [uploadState, setUploadState] = useState({ status: 'idle', progress: 0, error: null })
  const [uploadPollActive, setUploadPollActive] = useState(false)
  const previousSourceStatuses = useRef(null)
  const lastHandledRunTransition = useRef(null)
  const preUploadResourceIds = useRef(new Set())
  const uploadPollAttempts = useRef(0)
  const resources = useImportResources(selectedSource)
  const runStatus = useImportProcessStatus(importState.runId)

  useEffect(() => {
    const currentStatuses = new Map(sources.data.map((source) => [source.id ?? source.code, source.status]))
    const previousStatuses = previousSourceStatuses.current
    const transitioned = previousStatuses
      ? [...currentStatuses]
        .filter(([id, status]) => ['available', 'error'].includes(status) && previousStatuses.get(id) !== status)
        .map(([id]) => id)
      : []

    if (transitioned.length > 0) {
      setUploadState((current) => current.status === 'success'
        ? { status: 'idle', progress: 0, error: null }
        : current)
      if (selectedSource && transitioned.includes(selectedSource)) {
        resources.refresh()
      }
    }

    previousSourceStatuses.current = currentStatuses
  }, [sources.data, selectedSource, resources])

  useEffect(() => {
    if (uploadState.status === 'idle') return undefined

    const timer = window.setTimeout(() => {
      setUploadState({ status: 'idle', progress: 0, error: null })
    }, ACTION_MESSAGE_TIMEOUT)

    return () => window.clearTimeout(timer)
  }, [uploadState.status])

  useEffect(() => {
    writeStoredRun(importState.source, importState.resource, importState.runId)
  }, [importState.source, importState.resource, importState.runId])

  // Poll after a successful upload until the resulting import resource shows up as PENDING:
  // the backend registers it asynchronously, so the immediate refresh right after upload can
  // still miss it.
  useEffect(() => {
    if (!uploadPollActive || resources.loading) return undefined

    const priorIds = preUploadResourceIds.current
    const pendingResourceAppeared = resources.data.some(
      (resource) => !priorIds.has(resource.id) && resource.status?.toUpperCase() === 'PENDING',
    )

    if (pendingResourceAppeared || uploadPollAttempts.current >= UPLOAD_POLL_MAX_ATTEMPTS) {
      setUploadPollActive(false)
      uploadPollAttempts.current = 0
      return undefined
    }

    const timer = window.setTimeout(() => {
      uploadPollAttempts.current += 1
      resources.refresh()
    }, UPLOAD_POLL_INTERVAL_MS)

    return () => window.clearTimeout(timer)
  }, [uploadPollActive, resources])

  const handleSelectSource = (source) => {
    setUploadPollActive(false)
    uploadPollAttempts.current = 0
    setSelectedSource(source)
  }

  useEffect(() => {
    const runId = runStatus.runId
    const status = runStatus.data?.status
    if (!runId || !status) return

    const transitionKey = `${runId}:${status}`
    if (lastHandledRunTransition.current === transitionKey) return
    lastHandledRunTransition.current = transitionKey

    // Refresh on every status change (including queued/running), so the resource card's status
    // badge and processing indicator reflect the backend's current state without requiring a
    // manual refresh.
    if (importState.source && importState.source === selectedSource) {
      resources.refresh()
    }
  }, [runStatus.runId, runStatus.data?.status, importState.source, selectedSource, resources])

  // Only one import process may run at a time; while the active run hasn't reached a terminal
  // status yet (or its outcome hasn't been fetched), starting another one is blocked.
  const importInProgress = importState.submitting
    || (Boolean(importState.runId) && (!runStatus.data || isActiveImportRunStatus(runStatus.data.status)))

  const startPreview = async (resource) => {
    if (!resource || importInProgress) return
    try {
      setSelectedSeason(resource)
      setImportState(emptyImportState())
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
    if (!resource || importInProgress) return
    setSelectedSeason(resource)
    setPreviewState(emptyPreviewState())
    setImportState({ source: selectedSource, resource, submitting: true, runId: null, error: null })
    try {
      const accepted = await startImport(token, resource.jobId ?? resource.id, clearSession)
      const runId = accepted?.response?.runId ?? accepted?.runId ?? null
      if (!runId) {
        throw new Error('No s’ha rebut cap identificador d’execució.')
      }
      setImportState({ source: selectedSource, resource, submitting: false, runId, error: null })
    } catch (error) {
      setImportState({ source: selectedSource, resource, submitting: false, runId: null, error })
    }
  }

  const clearImport = () => {
    setImportState(emptyImportState())
    setSelectedSeason(null)
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
      setFile(null)
      sources.refresh?.()
      preUploadResourceIds.current = new Set(resources.data.map((resource) => resource.id))
      uploadPollAttempts.current = 0
      resources.refresh()
      setUploadPollActive(true)
    } catch (error) {
      if (error.name === 'AbortError') return
      setUploadState({ status: 'error', progress: 0, error })
    }
  }

  const handleFileChange = (nextFile) => {
    setFile(nextFile)
    setUploadState({ status: 'idle', progress: 0, error: null })
  }

  const processWorkspaceState = {
    loading: importState.submitting || (Boolean(importState.runId) && runStatus.loading),
    error: importState.error || runStatus.error,
    run: runStatus.data,
  }

  return (
    <section className="page-block import-panel" aria-labelledby="import-panel-title">
      <SectionLabel>{t('importPanel.title')}</SectionLabel>
      <h1 id="import-panel-title" className="page-title">{t('importPanel.title')}</h1>
      <p className="page-description">{t('importPanel.description')}</p>
      <ImportFileControls file={file} onFileChange={handleFileChange} onLoad={loadFile} uploadState={uploadState} />
      <div className="import-panel-grid">
        <ImportSourceSelector sources={sources} selected={selectedSource} onSelect={handleSelectSource} />
        <div>
          {!selectedSource ? <p>{t('importPanel.chooseSource')}</p>
          : resources.loading ? <p role="status">{t('importPanel.resourcesLoading')}</p>
          : resources.error ? <div role="alert">{t(resources.error.status === 403 ? 'importPanel.forbidden' : resources.error.status === 401 ? 'importPanel.unauthorized' : 'importPanel.serverError')} <button type="button" onClick={resources.retry}>{t('common.retry')}</button></div>
            : resources.data.length === 0 ? <p>{t('importPanel.resourcesEmpty')}</p>
              : <ImportResourceList
                  resources={resources.data}
                  onSimulate={(resource) => runResource(resource, true)}
                  onImport={(resource) => runResource(resource)}
                  disabled={importInProgress}
                />}
        </div>
        {importState.resource
          ? <ImportProcessWorkspace
              resource={importState.resource}
              process={processWorkspaceState}
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
