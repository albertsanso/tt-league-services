export const routesMeta = [
  { path: '/', label: 'Overview', section: 'General', auth: true },
  { path: '/clubs', label: 'Cerca de clubs', section: 'General', auth: true, permission: 'clubs:read' },
  { path: '/jugadors', label: 'Cerca de jugadors', section: 'General', auth: true, permission: 'players:read' },
  { path: '/partits', label: 'Cerca de partits', section: 'General', auth: true, permission: 'matches:read' },
  { path: '/cerca', label: 'Resultats de cerca', section: 'General', auth: true },
  { path: '/settings', label: 'Configuració', section: 'General', auth: true },
]

export function getRouteMeta(pathname) {
  return routesMeta.find((route) => (
    route.path === pathname || (route.path !== '/' && pathname.startsWith(`${route.path}/`))
  )) ?? routesMeta[0]
}

export function getBreadcrumbItems(pathname) {
  const activeRoute = getRouteMeta(pathname)

  return [
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
