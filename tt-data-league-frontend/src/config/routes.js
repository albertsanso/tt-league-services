export const routePaths = {
  clubs: '/clubs',
  clubDetails: (clubId) => `/clubs/${encodeURIComponent(clubId)}`,
  clubEdit: (clubId) => `/clubs/${encodeURIComponent(clubId)}/edit`,
  players: (clubId) => `/jugadors?clubId=${encodeURIComponent(clubId)}`,
  matches: (clubId) => `/partits?clubId=${encodeURIComponent(clubId)}`,
}

export const routesMeta = [
  { path: '/', label: 'Overview', section: 'General', auth: true },
  { path: '/clubs', label: 'Cerca de clubs', section: 'General', auth: true, permission: 'clubs:read' },
  {
    path: '/clubs/:clubId/edit',
    label: 'Editar club',
    section: 'General',
    auth: true,
    permission: 'clubs:read',
    breadcrumb: [
      { label: 'General', path: '/' },
      { label: 'Cerca de clubs', path: '/clubs' },
      { label: 'Editar club' },
    ],
  },
  {
    path: '/clubs/:clubId',
    label: 'Detall del club',
    section: 'General',
    auth: true,
    permission: 'clubs:read',
    breadcrumb: [
      { label: 'General', path: '/' },
      { label: 'Cerca de clubs', path: '/clubs' },
      { label: 'Detall del club' },
    ],
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

  const dynamicRoute = routesMeta.find((route) => {
    if (route.path === '/clubs/:clubId/edit') {
      return /^\/clubs\/[^/]+\/edit$/.test(pathname)
    }
    if (route.path === '/clubs/:clubId') {
      return /^\/clubs\/[^/]+$/.test(pathname)
    }
    return route.path !== '/' && pathname.startsWith(`${route.path}/`)
  })

  return dynamicRoute ?? routesMeta[0]
}

export function getBreadcrumbItems(pathname) {
  const activeRoute = getRouteMeta(pathname)

  return activeRoute.breadcrumb ?? [
    {
      label: activeRoute.section,
      path: '/',
    },
    {
      label: activeRoute.label,
      path: activeRoute.path,
    },
  ]
}
