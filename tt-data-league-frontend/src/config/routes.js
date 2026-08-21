export const routesMeta = [
  { path: '/', label: 'Overview', section: 'General' },
  { path: '/clubs', label: 'Cerca de clubs', section: 'General' },
  { path: '/jugadors', label: 'Cerca de jugadors', section: 'General' },
  { path: '/partits', label: 'Cerca de partits', section: 'General' },
  { path: '/cerca', label: 'Resultats de cerca', section: 'General' },
  { path: '/settings', label: 'Configuració', section: 'General' },
]

export function getRouteMeta(pathname) {
  return routesMeta.find((route) => route.path === pathname) ?? routesMeta[0]
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
