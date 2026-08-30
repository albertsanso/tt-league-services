import Badge from '../components/ui/Badge.jsx'
import SectionLabel from '../components/ui/SectionLabel.jsx'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n, { persistLocale } from '../i18n/index.js'

function SettingsPage() {
  const { t } = useTranslation()
  const [locale, setLocale] = useState(i18n.language)
  function changeLocale(event) {
    const nextLocale = persistLocale(event.target.value)
    setLocale(nextLocale)
    void i18n.changeLanguage(nextLocale)
  }
  return (
    <section className="page-block">
      <h1 className="page-title">{t('settings.title')}</h1>
      <p className="page-description">{t('settings.description')}</p>

      <SectionLabel>{t('settings.preferences')}</SectionLabel>
      <article className="placeholder-panel card">
        <h2>{t('settings.language')}</h2>
        <p>{t('settings.languageDescription')}</p>
        <label className="auth-field" htmlFor="language-selector">
          <span>{t('settings.language')}</span>
          <select id="language-selector" value={locale} onChange={changeLocale}>
            <option value="ca">{t('settings.catalan')}</option>
            <option value="es">{t('settings.spanish')}</option>
            <option value="en">{t('settings.english')}</option>
          </select>
        </label>
      </article>
      <article className="placeholder-panel card">
        <h2>{t('settings.notifications')}</h2>
        <p>{t('settings.notificationsDescription')}</p>
      </article>

      <article className="placeholder-panel card">
        <h2>{t('settings.integrations')}</h2>
        <p>
          {t('settings.integrationsDescription')}{' '}
          <Badge tone="warning">{t('common.soon')}</Badge>
        </p>
      </article>
    </section>
  )
}

export default SettingsPage
