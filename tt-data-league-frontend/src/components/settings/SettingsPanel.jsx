import { useMemo, useState } from 'react'
import { Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { createSetting, deleteSetting, updateSetting } from '../../api/settings.js'
import { useAuth } from '../../context/useAuth.js'
import { useSettings } from '../../hooks/useSettings.js'

const categories = ['GENERAL', 'IMPORT', 'NOTIFICATIONS']

function settingType(setting) {
  return setting.type?.toUpperCase() ?? 'STRING'
}

function SettingValue({ setting, value, onChange }) {
  const { t } = useTranslation()
  const type = settingType(setting)
  if (type === 'BOOLEAN') {
    return (
      <fieldset className="setting-toggle">
        <legend>{t('systemSettings.value')}</legend>
        <label><input type="radio" checked={value === 'true'} onChange={() => onChange('true')} />{t('systemSettings.enabled')}</label>
        <label><input type="radio" checked={value !== 'true'} onChange={() => onChange('false')} />{t('systemSettings.disabled')}</label>
      </fieldset>
    )
  }
  if (setting.allowedValues?.length > 0) {
    return (
      <select value={value} onChange={(event) => onChange(event.target.value)} aria-label={t('systemSettings.value')}>
        {setting.allowedValues.map((option) => <option key={option} value={option}>{option}</option>)}
      </select>
    )
  }
  return (
    <input
      type={type === 'INTEGER' ? 'number' : 'text'}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      aria-label={t('systemSettings.value')}
    />
  )
}

function SettingCard({ setting, pendingValue, onChange, onSave, onDelete, saving }) {
  const { t } = useTranslation()
  const value = pendingValue ?? setting.value ?? ''
  const label = t(`systemSettings.labels.${setting.name}`, { defaultValue: setting.name })
  return (
    <article className={`system-setting-card card ${pendingValue !== undefined ? 'is-modified' : ''}`}>
      <div className="system-setting-heading">
        <div>
          <h2>{label}</h2>
        </div>
      </div>

      <div className="system-setting-value">
        <SettingValue setting={setting} value={value} onChange={(next) => onChange(setting.name, next)} />
      </div>

      <div className="setting-actions">
        <button className="secondary-button" type="button" onClick={() => onSave(setting)} disabled={saving || pendingValue === undefined}>
          {saving ? t('systemSettings.saving') : t('systemSettings.save')}
        </button>
        <button className="danger-button" type="button" onClick={() => onDelete(setting)} disabled={saving}>
          {t('systemSettings.delete')}
        </button>
      </div>
    </article>
  )
}

function CreateSettingForm({ onCancel, onCreated }) {
  const { t } = useTranslation()
  const { token, clearSession } = useAuth()
  const [form, setForm] = useState({ name: '', category: 'GENERAL', value: '' })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setError('')
  }

  function validate() {
    if (!form.name.trim() || !form.value.trim()) {
      setError(t('systemSettings.required'))
      return false
    }
    setError('')
    return true
  }

  async function submit(event) {
    event.preventDefault()
    if (!validate()) return
    setSaving(true)
    try {
      await createSetting({ ...form, name: form.name.trim(), value: form.value.trim() }, token, undefined, clearSession)
      onCreated()
    } catch (err) {
      setError(err.message || t('systemSettings.saveError'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="system-setting-form card" aria-labelledby="system-setting-create-title">
      <h2 id="system-setting-create-title" className="section-title">{t('systemSettings.createTitle')}</h2>
      <form onSubmit={submit}>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="system-setting-form-grid">
          <label className="auth-field">{t('systemSettings.name')}<input value={form.name} onChange={(e) => update('name', e.target.value)} /></label>
          <label className="auth-field">{t('systemSettings.category')}<select value={form.category} onChange={(e) => update('category', e.target.value)}>{categories.map((category) => <option key={category} value={category}>{t(`systemSettings.categories.${category}`)}</option>)}</select></label>
          <label className="auth-field system-setting-value-field">{t('systemSettings.value')}<input value={form.value} onChange={(e) => update('value', e.target.value)} /></label>
        </div>
        <div className="user-form-actions">
          <button className="secondary-button" type="button" onClick={onCancel} disabled={saving}>{t('common.cancel')}</button>
          <button className="primary-button" type="submit" disabled={saving}>{saving ? t('systemSettings.saving') : t('systemSettings.create')}</button>
        </div>
      </form>
    </section>
  )
}

export default function SettingsPanel() {
  const { t } = useTranslation()
  const { token, clearSession } = useAuth()
  const [search, setSearch] = useState('')
  const [category, setCategory] = useState('')
  const [status, setStatus] = useState('all')
  const [applied, setApplied] = useState({ search: '', category: '' })
  const [refreshKey, setRefreshKey] = useState(0)
  const [pending, setPending] = useState({})
  const [creating, setCreating] = useState(false)
  const [message, setMessage] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const { data, loading, error, retry } = useSettings(applied, refreshKey)

  const settings = useMemo(() => data.filter((setting) => {
    const query = applied.search.toLowerCase()
    const text = `${setting.name} ${setting.description ?? ''}`.toLowerCase()
    const matchesSearch = !query || text.includes(query)
    const changed = pending[setting.name] !== undefined
    return matchesSearch && (status === 'all' || (status === 'modified' ? changed : !changed))
  }), [applied.search, data, pending, status])

  function refresh(messageText) {
    setPending({})
    setRefreshKey((key) => key + 1)
    setMessage(messageText)
  }

  async function save(setting) {
    try {
      await updateSetting(setting.id, pending[setting.name], setting.version, token, undefined, clearSession)
      refresh(t('systemSettings.saved'))
    } catch (err) {
      setMessage(err.status === 409 ? t('systemSettings.conflict') : (err.message || t('systemSettings.saveError')))
    }
  }

  async function removeSetting() {
    if (!confirmDelete) return
    try {
      await deleteSetting(confirmDelete.id, token, undefined, clearSession)
      setConfirmDelete(null)
      refresh(t('systemSettings.deleted'))
    } catch (err) {
      setConfirmDelete(null)
      setMessage(err.message || t('systemSettings.deleteError'))
    }
  }

  const groupedSettings = settings.reduce((groups, setting) => {
    const group = groups[setting.category] ?? []
    group.push(setting)
    return { ...groups, [setting.category]: group }
  }, {})

  return (
    <section className="page-block" aria-labelledby="system-settings-title">
      <div>
        <p className="section-label">{t('navigation.sectionAdministration')}</p>
        <div className="system-setting-title-row">
          <div><h1 id="system-settings-title" className="page-title">{t('systemSettings.title')}</h1><p className="page-description">{t('systemSettings.description')}</p></div>
        </div>
      </div>
      {message && <p className="form-success" role="status">{message}</p>}
      {!creating && <div className="users-admin-actions club-action-row"><button className="primary-button" type="button" onClick={() => setCreating(true)}>{t('systemSettings.create')}</button></div>}
      {creating && <CreateSettingForm onCancel={() => setCreating(false)} onCreated={() => { setCreating(false); refresh(t('systemSettings.created')) }} />}
      {confirmDelete && (
        <div className="confirm-dialog card" role="dialog" aria-labelledby="confirm-setting-delete-label">
          <p id="confirm-setting-delete-label">{t('systemSettings.confirmDelete', { name: confirmDelete.name })}</p>
          <div className="confirm-dialog-actions">
            <button className="secondary-button" type="button" onClick={() => setConfirmDelete(null)}>{t('common.cancel')}</button>
            <button className="danger-button" type="button" onClick={removeSetting}>{t('systemSettings.delete')}</button>
          </div>
        </div>
      )}
      <article className="system-search-card card">
        <form className="system-search-form" onSubmit={(event) => { event.preventDefault(); setApplied({ search, category }) }}>
          <fieldset className="system-search-field">
            <legend>{t('systemSettings.search')}</legend>
            <div className="club-search-input-wrap"><Search size={17} aria-hidden="true" /><input id="system-settings-search" className="club-search-input" type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t('systemSettings.searchPlaceholder')} /></div>
          </fieldset>
          <fieldset className="system-category-filter">
            <legend>{t('systemSettings.category')}</legend>
            <select value={category} onChange={(e) => setCategory(e.target.value)}><option value="">{t('systemSettings.categories.ALL')}</option>{categories.map((item) => <option key={item} value={item}>{t(`systemSettings.categories.${item}`)}</option>)}</select>
          </fieldset>
          <fieldset className="system-status-filter">
            <legend>{t('systemSettings.status')}</legend>
            <label className="radio-option"><input type="radio" checked={status === 'all'} onChange={() => setStatus('all')} />{t('systemSettings.all')}</label>
            <label className="radio-option"><input type="radio" checked={status === 'modified'} onChange={() => setStatus('modified')} />{t('systemSettings.modified')}</label>
            <label className="radio-option"><input type="radio" checked={status === 'default'} onChange={() => setStatus('default')} />{t('systemSettings.defaults')}</label>
          </fieldset>
          <div className="system-search-actions club-action-row"><button className="primary-button" type="submit">{t('common.search')}</button><button className="secondary-button" type="button" onClick={() => { setSearch(''); setCategory(''); setStatus('all'); setApplied({ search: '', category: '' }) }}>{t('systemSettings.clear')}</button></div>
        </form>
      </article>
      {loading && <p className="club-state card" role="status">{t('systemSettings.loading')}</p>}
      {error && <div className="card"><p className="form-error" role="alert">{error.status === 403 ? t('systemSettings.forbidden') : t('systemSettings.serverError')}</p><button className="secondary-button" type="button" onClick={retry}>{t('common.retry')}</button></div>}
      {!loading && !error && settings.length === 0 && <p className="club-state card">{t('systemSettings.empty')}</p>}
      {!loading && !error && settings.length > 0 && <div className="system-settings-list"><p className="users-count">{t('systemSettings.count', { count: settings.length })}</p>{Object.entries(groupedSettings).map(([group, groupSettings]) => <section key={group} className="system-setting-group" aria-labelledby={`system-setting-group-${group}`}><h2 id={`system-setting-group-${group}`} className="section-label">{t(`systemSettings.categories.${group}`, { defaultValue: group })}</h2>{groupSettings.map((setting) => <SettingCard key={setting.id ?? setting.name} setting={setting} pendingValue={pending[setting.name]} onChange={(name, value) => setPending((values) => ({ ...values, [name]: value }))} onSave={save} onDelete={setConfirmDelete} saving={false} />)}</section>)}</div>}
    </section>
  )
}
