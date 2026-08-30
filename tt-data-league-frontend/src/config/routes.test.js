import { describe, expect, it } from 'vitest'
import { getBreadcrumbItems, getRouteMeta, routePaths } from './routes.js'

describe('route configuration', () => {
  it('builds safe dynamic club paths and filters return state', () => {
    expect(routePaths.clubDetails('club/id', '?view=matches&season=2024&unknown=value')).toBe(
      '/clubs/club%2Fid?view=matches&season=2024',
    )
  })

  it('builds the Club detail breadcrumb from search', () => {
    expect(getBreadcrumbItems('/clubs/club%2Fid', '?season=all&source=all')).toEqual([
      { label: 'General', path: '/' },
      { label: 'Cerca de clubs', path: '/clubs' },
      { label: 'Detall del club' },
    ])
  })

  it('links competition and edit breadcrumbs to the decoded Club detail route', () => {
    const search = '?view=matches&season=2024-2025&competition=Preferent'
    const expectedClubPath = '/clubs/club%2Fid?view=matches&season=2024-2025&competition=Preferent'

    expect(getBreadcrumbItems(
      '/clubs/club%2Fid/competition/2024-2025/Preferent',
      search,
    )).toEqual([
      { label: 'General', path: '/' },
      { label: 'Cerca de clubs', path: '/clubs' },
      { label: 'Detall del club', path: expectedClubPath },
      { label: 'Detall de competició' },
    ])
    expect(getBreadcrumbItems('/clubs/club%2Fid/edit', search)[2]).toEqual({
      label: 'Detall del club',
      path: expectedClubPath,
    })
  })

  it('matches nested Club routes without matching similarly prefixed paths', () => {
    expect(getRouteMeta('/clubs/club-id/competition/2024/Preferent').label).toBe('Detall de competició')
    expect(getRouteMeta('/clubs-other').label).toBe('Resum')
  })

  it('builds and recognizes canonical player detail paths', () => {
    expect(routePaths.playerDetails('player/id', '?source=RFETM&season=2025&other=x'))
      .toBe('/jugadors/player%2Fid?season=2025&source=RFETM')
    expect(getRouteMeta('/jugadors/player%2Fid').label).toBe('Detall del jugador')
  })

  it('centralizes protected administration routes and breadcrumbs', () => {
    expect(routePaths.administrationUsers).toBe('/administration/users')
    expect(getRouteMeta(routePaths.administration).role).toBe('ADMIN')
    expect(getRouteMeta(routePaths.administrationImport).role).toBe('ADMIN')
    expect(getBreadcrumbItems(routePaths.administrationUsers)).toEqual([
      { label: 'General', path: '/' },
      { label: 'Administració', path: '/administration' },
      { label: 'Usuaris i rols', path: '/administration/users' },
    ])
  })
})
