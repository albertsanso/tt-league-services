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
    expect(getRouteMeta('/clubs-other').label).toBe('Overview')
  })
})
