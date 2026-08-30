import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  downloadSettingsBackup, previewSettings, restoreSettingsBackup, updateSettings,
} from '../../api/settings.js'
import { useAuth } from '../../context/useAuth.js'
import { useSettings } from '../../hooks/useSettings.js'

const categories = ['ALL', 'UI', 'NOTIFICATIONS', 'IMPORT', 'DISPLAY']

function displayError(error, t) {
  if (!error) return ''
  if (error.status === 401) return t('systemSettings.unauthorized')
  if (error.status === 403) return t('systemSettings.forbidden')
  if (error.status === 409) return t('systemSettings.conflict')
  return error.message || t('systemSettings.serverError')
}

function SettingInput({ setting, label, value, onChange }) {
  if (setting.type === 'BOOLEAN') {
    return <input type="checkbox" checked={Boolean(value)} onChange={(event) => onChange(event.target.checked)} aria-label={label} />
  }
  if (setting.type === 'STRING') {
    return (
      <select value={value} onChange={(event) => onChange(event.target.value)} aria-label={label}>
        {setting.allowedValues.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    )
  }
  return (
    <input
      type="number"
      value={value}
      min={setting.minimum ?? undefined}
      max={setting.maximum ?? undefined}
      onChange={(event) => onChange(Number(event.target.value))}
      aria-label={setting.label}
    />
  )
}

export default function SettingsPanel() {
  const { t } = useTranslation()
  const { token, clearSession } = useAuth()
  const [category, setCategory] = useState('ALL')
  const [search, setSearch] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)
  const filters = useMemo(() => ({ category: category === 'ALL' ? '' : category, search }), [category, search])
  const { data, loading, error, retry } = useSettings(filters, refreshKey)
  const [draft, setDraft] = useState({})
  const [status, setStatus] = useState(null)
  const [busy, setBusy] = useState(false)

  const valueFor = (setting) => Object.prototype.hasOwnProperty.call(draft, setting.key)
    ? draft[setting.key] : setting.value
  const change = (setting, value) => setDraft((current) => ({ ...current, [setting.key]: value }))
  const discard = () => { setDraft({}); setStatus(null) }

  async function preview() {
    if (!Object.keys(draft).length) return
    setBusy(true)
    setStatus(null)
    try {
      await previewSettings(draft, token, undefined, clearSession)
      setStatus({ kind: 'success', text: t('systemSettings.previewSuccess') })
    } catch (requestError) {
      setStatus({ kind: 'error', text: displayError(requestError, t) })
    } finally { setBusy(false) }
  }

  async function apply() {
    if (!Object.keys(draft).length) return
    setBusy(true)
    setStatus(null)
    const versions = Object.fromEntries(data.filter((item) => draft[item.key] !== undefined)
      .map((item) => [item.key, item.version]))
    try {
      await updateSettings(draft, versions, token, undefined, clearSession)
      discard()
      setRefreshKey((value) => value + 1)
      setStatus({ kind: 'success', text: t('systemSettings.saved') })
    } catch (requestError) {
      setStatus({ kind: 'error', text: displayError(requestError, t) })
    } finally { setBusy(false) }
  }

  async function backup() {
    try {
      const value = await downloadSettingsBackup(token, undefined, clearSession)
      const blob = new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'tt-league-settings-v1.json'
      link.click()
      URL.revokeObjectURL(url)
    } catch (requestError) {
      setStatus({ kind: 'error', text: displayError(requestError, t) })
    }
  }

  function restore(event) {
    const file = event.target.files?.[0]
    if (!file) return
    if (file.size > 1024 * 1024) {
      setStatus({ kind: 'error', text: t('systemSettings.fileTooLarge') })
      return
    }
    if (!window.confirm(t('systemSettings.restoreConfirm'))) return
    const reader = new FileReader()
    reader.onload = async () => {
      try {
        setBusy(true)
        await restoreSettingsBackup(JSON.parse(reader.result), token, undefined, clearSession)
        setRefreshKey((value) => value + 1)
        setStatus({ kind: 'success', text: t('systemSettings.restored') })
      } catch (requestError) { setStatus({ kind: 'error', text: displayError(requestError, t) }) } finally { setBusy(false) }
    }
    reader.readAsText(file)
  }

  return (
    <section className="page-block" aria-labelledby="administration-settings-title">
      <h1 id="administration-settings-title" className="page-title">{t('systemSettings.title')}</h1>
      <p className="page-description">{t('systemSettings.description')}</p>
      <div className="settings-toolbar">
        <label>{t('systemSettings.search')} <input value={search} onChange={(event) => setSearch(event.target.value)} /></label>
        <label>{t('systemSettings.category')}
          <select value={category} onChange={(event) => setCategory(event.target.value)}>
            {categories.map((item) => <option key={item} value={item}>{t(`systemSettings.categories.${item}`)}</option>)}
          </select>
        </label>
        <button type="button" onClick={backup}>{t('systemSettings.download')}</button>
        <label className="button-like">{t('systemSettings.restore')}<input type="file" accept="application/json,.json" onChange={restore} hidden /></label>
      </div>
      {loading && <p role="status">{t('systemSettings.loading')}</p>}
      {error && (
        <p role="alert">
          {displayError(error, t)}
          <button type="button" onClick={retry}>{t('common.retry')}</button>
        </p>
      )}
      {status && <p role={status.kind === 'error' ? 'alert' : 'status'}>{status.text}</p>}
      {!loading && !error && data.length === 0 && <p>{t('systemSettings.empty')}</p>}
      <div className="settings-list">
        {data.map((setting) => (
          <article className="card settings-card" key={setting.key}>
            <div>
              <h2>{t(`systemSettings.labels.${setting.key}`, { defaultValue: setting.label })}</h2>
              <p>{setting.description}</p><small>{setting.key}</small>
            </div>
            <SettingInput
              setting={setting}
              label={t(`systemSettings.labels.${setting.key}`, { defaultValue: setting.label })}
              value={valueFor(setting)}
              onChange={(value) => change(setting, value)}
            />
          </article>
        ))}
      </div>
      {Object.keys(draft).length > 0 && (
        <div className="settings-actions">
          <button type="button" onClick={preview} disabled={busy}>{t('systemSettings.preview')}</button>
          <button type="button" onClick={apply} disabled={busy}>{t('systemSettings.apply')}</button>
          <button type="button" onClick={discard} disabled={busy}>{t('common.cancel')}</button>
        </div>
      )}
    </section>
  )
}
