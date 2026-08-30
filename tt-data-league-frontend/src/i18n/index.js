import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import ca from './ca.js'
import es from './es.js'
import en from './en.js'

export const SUPPORTED_LOCALES = ['ca', 'es', 'en']
export const DEFAULT_LOCALE = 'ca'
export const LOCALE_STORAGE_KEY = 'tt-league.locale'
export const localeResources = { ca: { translation: ca }, es: { translation: es }, en: { translation: en } }

export function isSupportedLocale(value) {
  return SUPPORTED_LOCALES.includes(value)
}

export function getStoredLocale(storage = typeof window === 'undefined' ? null : window.localStorage) {
  try {
    const value = storage?.getItem(LOCALE_STORAGE_KEY)
    return isSupportedLocale(value) ? value : DEFAULT_LOCALE
  } catch {
    return DEFAULT_LOCALE
  }
}

export function persistLocale(locale, storage = typeof window === 'undefined' ? null : window.localStorage) {
  const validLocale = isSupportedLocale(locale) ? locale : DEFAULT_LOCALE
  try {
    storage?.setItem(LOCALE_STORAGE_KEY, validLocale)
  } catch {
    // Storage can be unavailable in privacy-restricted browsers.
  }
  return validLocale
}

if (!i18n.isInitialized) {
  await i18n
    .use(initReactI18next)
    .init({
      resources: localeResources,
      lng: getStoredLocale(),
      fallbackLng: DEFAULT_LOCALE,
      interpolation: { escapeValue: false },
      returnNull: false,
    })
}

i18n.on('languageChanged', (locale) => persistLocale(locale))

export default i18n
