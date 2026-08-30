import i18n from '../i18n/index.js'

const CLUB_DETAIL_QUERY_KEYS = ['view', 'season', 'source', 'competition']
const PLAYER_DETAIL_QUERY_KEYS = ['season', 'source', 'competition']

function withSearch(path, search, keys = CLUB_DETAIL_QUERY_KEYS) {
  if (!search) return path
  const rawSearch = typeof search === 'string' ? search : search.toString()
  const params = new URLSearchParams(rawSearch.replace(/^\?/, ''))
  const normalized = new URLSearchParams()
  keys.forEach((key) => {
    const value = params.get(key)
    if (value != null && value !== '') normalized.set(key, value)
  })
  const query = normalized.toString()
  return query ? `${path}?${query}` : path
}

export const routePaths = {
  home: '/',
  clubs: '/clubs',
  clubDetails: (clubId, returnSearch = '') => withSearch(
    `/clubs/${encodeURIComponent(clubId)}`,
    returnSearch,
  ),
  clubCompetitionDetails: (clubId, season, competition, returnSearch = '') => {
    const path = `/clubs/${encodeURIComponent(clubId)}/competition/${encodeURIComponent(season)}/${encodeURIComponent(competition)}`
    return withSearch(path, returnSearch)
  },
  clubEdit: (clubId, returnSearch = '') => withSearch(
    `/clubs/${encodeURIComponent(clubId)}/edit`,
    returnSearch,
  ),
  players: (clubId) => clubId
    ? `/jugadors?clubId=${encodeURIComponent(clubId)}`
    : '/jugadors',
  playerDetails: (playerId, returnSearch = '') => withSearch(
    `/jugadors/${encodeURIComponent(playerId)}`,
    returnSearch,
    PLAYER_DETAIL_QUERY_KEYS,
  ),
  matches: (clubId) => clubId ? `/partits?clubId=${encodeURIComponent(clubId)}` : '/partits',
  matchDetails: (matchId, returnSearch = '') => withSearch(
    `/partits/${encodeURIComponent(matchId)}`, returnSearch, ['source', 'season', 'competition'],
  ),
  administration: '/administration',
  administrationUsers: '/administration/users',
  administrationSettings: '/administration/settings',
  administrationImport: '/administration/import',
}

const translate = (key) => i18n.t(key)
const generalBreadcrumb = () => ({ label: translate('routes.general'), path: routePaths.home })
const clubsBreadcrumb = () => ({ label: translate('routes.clubSearch'), path: routePaths.clubs })
const playersBreadcrumb = () => ({ label: translate('routes.playerSearch'), path: routePaths.players() })

function clubDetailBreadcrumb() {
  return [
    generalBreadcrumb(),
    clubsBreadcrumb(),
    { label: translate('routes.clubDetail') },
  ]
}

function clubChildBreadcrumb({ clubId }, search, label) {
  return [
    generalBreadcrumb(),
    clubsBreadcrumb(),
    { label: translate('routes.clubDetail'), path: routePaths.clubDetails(clubId, search) },
    { label },
  ]
}

function playerDetailBreadcrumb() {
  return [
    generalBreadcrumb(),
    playersBreadcrumb(),
    { label: translate('routes.playerDetail') },
  ]
}

export const routesMeta = [
  { path: routePaths.home, label: translate('navigation.overview'), labelKey: 'navigation.overview', section: translate('routes.general'), auth: true },
  { path: routePaths.clubs, label: translate('routes.clubSearch'), labelKey: 'routes.clubSearch', section: translate('routes.general'), auth: true, permission: 'clubs:read' },
  {
    path: '/clubs/:clubId/edit',
    label: translate('routes.clubEdit'),
    labelKey: 'routes.clubEdit',
    section: translate('routes.general'),
    auth: true,
    permission: 'clubs:read',
    breadcrumb: (params, search) => clubChildBreadcrumb(params, search, translate('routes.clubEdit')),
  },
  {
    path: '/clubs/:clubId',
    label: translate('routes.clubDetail'),
    labelKey: 'routes.clubDetail',
    section: translate('routes.general'),
    auth: true,
    permission: 'clubs:read',
    breadcrumb: clubDetailBreadcrumb,
  },
  {
    path: '/clubs/:clubId/competition/:season/:competition',
    label: translate('routes.competitionDetail'),
    labelKey: 'routes.competitionDetail',
    section: translate('routes.general'),
    auth: true,
    permission: ['clubs:read', 'matches:read'],
    breadcrumb: (params, search) => clubChildBreadcrumb(params, search, translate('routes.competitionDetail')),
  },
  { path: '/jugadors', label: translate('routes.playerSearch'), labelKey: 'routes.playerSearch', section: translate('routes.general'), auth: true, permission: 'players:read' },
  {
    path: '/jugadors/:playerId',
    label: translate('routes.playerDetail'),
    labelKey: 'routes.playerDetail',
    section: translate('routes.general'),
    auth: true,
    permission: 'players:read',
    breadcrumb: playerDetailBreadcrumb,
  },
  { path: '/partits', label: translate('routes.matchSearch'), labelKey: 'routes.matchSearch', section: translate('routes.general'), auth: true, permission: 'matches:read' },
  {
    path: '/partits/:matchId',
    label: translate('routes.matchDetail'),
    labelKey: 'routes.matchDetail',
    section: translate('routes.general'),
    auth: true,
    permission: 'matches:read',
    breadcrumb: (params, search) => [
      generalBreadcrumb(),
      { label: translate('routes.matchSearch'), path: routePaths.matches() },
      { label: translate('routes.matchDetail'), path: routePaths.matchDetails(params.matchId, search) },
    ],
  },
  { path: '/cerca', label: translate('routes.searchResults'), labelKey: 'routes.searchResults', section: translate('routes.general'), auth: true },
  { path: '/settings', label: translate('routes.settings'), labelKey: 'routes.settings', section: translate('routes.general'), auth: true },
  {
    path: routePaths.administration,
    label: translate('routes.administration'),
    labelKey: 'routes.administration',
    section: translate('routes.general'),
    auth: true,
    role: 'ADMIN',
    breadcrumb: () => [
      generalBreadcrumb(),
      { label: translate('routes.administration') },
    ],
  },
  {
    path: routePaths.administrationUsers,
    label: translate('routes.administrationUsers'),
    labelKey: 'routes.administrationUsers',
    section: translate('routes.general'),
    auth: true,
    role: 'ADMIN',
    breadcrumb: (params, search) => [
      generalBreadcrumb(),
      { label: translate('routes.administration'), path: routePaths.administration },
      { label: translate('routes.administrationUsers'), path: `${routePaths.administrationUsers}${search || ''}` },
    ],
  },
  {
    path: routePaths.administrationSettings,
    label: translate('routes.administrationSettings'),
    labelKey: 'routes.administrationSettings',
    section: translate('routes.general'),
    auth: true,
    role: 'ADMIN',
  },
  {
    path: routePaths.administrationImport,
    label: translate('routes.administrationImport'),
    labelKey: 'routes.administrationImport',
    section: translate('routes.general'),
    auth: true,
    role: 'ADMIN',
  },
]

function localizedRoute(route) {
  return route.labelKey
    ? { ...route, label: translate(route.labelKey), section: translate('routes.general') }
    : route
}

export function getRouteMeta(pathname) {
  const exactRoute = routesMeta.find((route) => route.path === pathname)
  if (exactRoute) {
    return localizedRoute(exactRoute)
  }

  const dynamicRoute = routesMeta.filter((route) => route.path.includes(':')).find((route) => {
    if (route.path === '/clubs/:clubId/edit') {
      return /^\/clubs\/[^/]+\/edit$/.test(pathname)
    }
    if (route.path === '/clubs/:clubId') {
      return /^\/clubs\/[^/]+$/.test(pathname)
    }
    if (route.path === '/clubs/:clubId/competition/:season/:competition') {
      return /^\/clubs\/[^/]+\/competition\/[^/]+\/[^/]+$/.test(pathname)
    }
    if (route.path === '/jugadors/:playerId') {
      return /^\/jugadors\/[^/]+$/.test(pathname)
    }
    if (route.path === '/partits/:matchId') {
      return /^\/partits\/[^/]+$/.test(pathname)
    }
  })

  if (dynamicRoute) {
    return localizedRoute(dynamicRoute)
  }

  const nestedRoute = routesMeta.find((route) => (
    !route.path.includes(':')
      && route.path !== routePaths.home
      && pathname.startsWith(`${route.path}/`)
  ))

  return localizedRoute(nestedRoute ?? routesMeta[0])
}

function decodeRouteParameter(value) {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    if (error instanceof URIError) {
      return value
    }
    throw error
  }
}

function getRouteParameters(pathname, routePath) {
  const patterns = {
    '/clubs/:clubId/edit': /^\/clubs\/([^/]+)\/edit$/,
    '/clubs/:clubId': /^\/clubs\/([^/]+)$/,
    '/clubs/:clubId/competition/:season/:competition':
      /^\/clubs\/([^/]+)\/competition\/([^/]+)\/([^/]+)$/,
    '/jugadors/:playerId': /^\/jugadors\/([^/]+)$/,
    '/partits/:matchId': /^\/partits\/([^/]+)$/,
  }
  const match = patterns[routePath]?.exec(pathname)

  if (!match) {
    return {}
  }

  if (routePath === '/clubs/:clubId/competition/:season/:competition') {
    return {
      clubId: decodeRouteParameter(match[1]),
      season: decodeRouteParameter(match[2]),
      competition: decodeRouteParameter(match[3]),
    }

  }

  if (routePath === '/jugadors/:playerId') {
    return { playerId: decodeRouteParameter(match[1]) }
  }
  if (routePath === '/partits/:matchId') {
    return { matchId: decodeRouteParameter(match[1]) }
  }

  return { clubId: decodeRouteParameter(match[1]) }
}

export function getBreadcrumbItems(pathname, search = '') {
  const activeRoute = getRouteMeta(pathname)
  const breadcrumb = activeRoute.breadcrumb

  if (typeof breadcrumb === 'function') {
    return breadcrumb(getRouteParameters(pathname, activeRoute.path), search)
  }

  return breadcrumb ?? [
    {
      label: activeRoute.sectionKey ? translate(activeRoute.sectionKey) : translate('routes.general'),
      path: routePaths.home,
    },
    {
      label: activeRoute.labelKey ? translate(activeRoute.labelKey) : activeRoute.label,
      path: activeRoute.path,
    },
  ]
}
