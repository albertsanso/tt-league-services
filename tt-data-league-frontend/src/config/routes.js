const CLUB_DETAIL_QUERY_KEYS = ['view', 'season', 'source', 'competition']

function normalizeClubDetailSearch(search) {
  if (!search) {
    return ''
  }

  const rawSearch = typeof search === 'string' ? search : search.toString()
  const params = new URLSearchParams(rawSearch.replace(/^\?/, ''))
  const normalized = new URLSearchParams()

  CLUB_DETAIL_QUERY_KEYS.forEach((key) => {
    const value = params.get(key)
    if (value != null && value !== '') {
      normalized.set(key, value)
    }
  })

  return normalized.toString()
}

function withSearch(path, search) {
  const query = normalizeClubDetailSearch(search)
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
  players: (clubId) => `/jugadors?clubId=${encodeURIComponent(clubId)}`,
  matches: (clubId) => `/partits?clubId=${encodeURIComponent(clubId)}`,
}

const generalBreadcrumb = () => ({ label: 'General', path: routePaths.home })
const clubsBreadcrumb = () => ({ label: 'Cerca de clubs', path: routePaths.clubs })

function clubDetailBreadcrumb() {
  return [
    generalBreadcrumb(),
    clubsBreadcrumb(),
    { label: 'Detall del club' },
  ]
}

function clubChildBreadcrumb({ clubId }, search, label) {
  return [
    generalBreadcrumb(),
    clubsBreadcrumb(),
    { label: 'Detall del club', path: routePaths.clubDetails(clubId, search) },
    { label },
  ]
}

export const routesMeta = [
  { path: routePaths.home, label: 'Overview', section: 'General', auth: true },
  { path: routePaths.clubs, label: 'Cerca de clubs', section: 'General', auth: true, permission: 'clubs:read' },
  {
    path: '/clubs/:clubId/edit',
    label: 'Editar club',
    section: 'General',
    auth: true,
    permission: 'clubs:read',
    breadcrumb: (params, search) => clubChildBreadcrumb(params, search, 'Editar club'),
  },
  {
    path: '/clubs/:clubId',
    label: 'Detall del club',
    section: 'General',
    auth: true,
    permission: 'clubs:read',
    breadcrumb: clubDetailBreadcrumb,
  },
  {
    path: '/clubs/:clubId/competition/:season/:competition',
    label: 'Detall de competició',
    section: 'General',
    auth: true,
    permission: ['clubs:read', 'matches:read'],
    breadcrumb: (params, search) => clubChildBreadcrumb(params, search, 'Detall de competició'),
  },
  { path: '/jugadors', label: 'Cerca de jugadors', section: 'General', auth: true, permission: 'players:read' },
  { path: '/partits', label: 'Cerca de partits', section: 'General', auth: true, permission: 'matches:read' },
  { path: '/cerca', label: 'Resultats de cerca', section: 'General', auth: true },
  { path: '/settings', label: 'Configuració', section: 'General', auth: true },
]

export function getRouteMeta(pathname) {
  const exactRoute = routesMeta.find((route) => route.path === pathname)
  if (exactRoute) {
    return exactRoute
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
  })

  if (dynamicRoute) {
    return dynamicRoute
  }

  const nestedRoute = routesMeta.find((route) => (
    !route.path.includes(':')
      && route.path !== routePaths.home
      && pathname.startsWith(`${route.path}/`)
  ))

  return nestedRoute ?? routesMeta[0]
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
      label: activeRoute.section,
      path: routePaths.home,
    },
    {
      label: activeRoute.label,
      path: activeRoute.path,
    },
  ]
}
