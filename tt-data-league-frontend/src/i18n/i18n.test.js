import { describe, expect, it } from 'vitest'
import i18next from 'i18next'
import { localeResources } from './index.js'
import {
  DEFAULT_LOCALE,
  LOCALE_STORAGE_KEY,
  getStoredLocale,
  isSupportedLocale,
  persistLocale,
} from './index.js'

describe('i18n configuration', () => {
  it('validates persisted locales and falls back to Catalan', () => {
    const storage = { getItem: () => 'fr' }
    expect(getStoredLocale(storage)).toBe(DEFAULT_LOCALE)
    expect(isSupportedLocale('ca')).toBe(true)
    expect(isSupportedLocale('fr')).toBe(false)
  })

  it('persists only supported locale values', () => {
    const values = {}
    const storage = {
      getItem: (key) => values[key],
      setItem: (key, value) => { values[key] = value },
    }
    expect(persistLocale('es', storage)).toBe('es')
    expect(values[LOCALE_STORAGE_KEY]).toBe('es')
    expect(persistLocale('invalid', storage)).toBe(DEFAULT_LOCALE)
    expect(values[LOCALE_STORAGE_KEY]).toBe(DEFAULT_LOCALE)
  })

  it('switches language and interpolates dynamic labels', async () => {
    const instance = i18next.createInstance()
    await instance.init({ resources: localeResources, lng: DEFAULT_LOCALE, fallbackLng: DEFAULT_LOCALE })
    await instance.changeLanguage('en')
    expect(instance.t('notification.pending', { count: 2 })).toBe('2 pending notifications')
    await instance.changeLanguage('ca')
    expect(instance.t('search.noClubs', { query: 'Terrassa' })).toContain('Terrassa')
  })

  it('translates administration navigation and destination shells', async () => {
    const instance = i18next.createInstance()
    await instance.init({ resources: localeResources, lng: DEFAULT_LOCALE, fallbackLng: DEFAULT_LOCALE })
    expect(instance.t('navigation.administration')).toBe('Administració')
    expect(instance.t('administration.administrationUsers.title')).toBe('Usuaris i rols')
    await instance.changeLanguage('en')
    expect(instance.t('navigation.administration')).toBe('Administration')
    expect(instance.t('administration.administrationImport.title')).toBe('Data import')
    await instance.changeLanguage('es')
    expect(instance.t('navigation.administrationSettings')).toBe('Configuración del sistema')
  })

  it('falls back to Catalan for missing resource keys', async () => {
    const instance = i18next.createInstance()
    await instance.init({ resources: localeResources, lng: 'en', fallbackLng: DEFAULT_LOCALE })
    expect(instance.t('settings.notifications')).toBe('Notifications')
    expect(instance.t('missing.key')).toBe('missing.key')
  })
})
