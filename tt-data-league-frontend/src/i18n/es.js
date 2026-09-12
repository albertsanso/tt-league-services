import ca from './ca.js'

const es = {
  ...ca,
  common: { ...ca.common, search: 'Buscar', retry: 'Reintentar', cancel: 'Cancelar', close: 'Cerrar', source: 'Fuente', sources: 'Fuentes', season: 'Temporada', seasons: 'Temporadas', competition: 'Competición', competitions: 'Competiciones', players: 'Jugadores', clubs: 'Clubes', matches: 'Partidos', result: 'Resultado', date: 'Fecha', opponent: 'Oponente', opponentTeam: 'Equipo oponente', score: 'Marcador', wins: 'Victorias', draws: 'Empates', losses: 'Derrotas', playedMatches: 'Partidos jugados', winPercentage: 'Victorias (%)', first: 'Primera', previous: 'Anterior', next: 'Siguiente', last: 'Última', pageOf: 'Página {{page}} de {{count}}', category: 'Categoría', license: 'Licencia', sourceLabel: 'Fuente: {{source}}' },
  navigation: { ...ca.navigation, sectionNavigation: 'Navegación', sectionAnalysis: 'Análisis', overview: 'Resumen', clubs: 'Buscar clubes', players: 'Buscar jugadores', matches: 'Buscar partidos', analytics: 'Analítica', settings: 'Configuración', sidebar: 'Barra lateral', main: 'Navegación principal', breadcrumb: 'Migas de pan', close: 'Cerrar menú', expand: 'Expandir barra lateral', collapse: 'Contraer barra lateral', openMain: 'Abrir menú principal', closeMain: 'Cerrar menú principal', sectionAdministration: 'Administración', administration: 'Administración', administrationUsers: 'Usuarios y roles', administrationSettings: 'Configuración del sistema', administrationImport: 'Importación de datos', administrationClubs: 'Consolidación de clubes', administrationToggle: 'Mostrar u ocultar las opciones de administración' },
  routes: { ...ca.routes, general: 'General', clubSearch: 'Buscar clubes', playerSearch: 'Buscar jugadores', clubDetail: 'Detalle del club', clubEdit: 'Editar club', competitionDetail: 'Detalle de competición', playerDetail: 'Detalle del jugador', matchSearch: 'Buscar partidos', matchActa: 'Acta del partido', searchResults: 'Resultados de búsqueda', settings: 'Configuración', administration: 'Administración', administrationUsers: 'Usuarios y roles', administrationSettings: 'Configuración del sistema', administrationImport: 'Importación de datos', administrationClubs: 'Consolidación de clubes' },
  shell: { ...ca.shell, skip: 'Ir al contenido principal', footer: 'TT League · Proyecto abierto para la comunidad del tenis de mesa.', openProject: 'Proyecto abierto para la comunidad del tenis de mesa.', closeNavigation: 'Cerrar menú de navegación', loading: 'Cargando contenido...', checkingSession: 'Comprobando la sesión...' },
  notification: { pending: '{{count}} notificaciones pendientes', none: 'Sin notificaciones pendientes' },
  user: { ...ca.user, default: 'Usuario', profile: 'Mi perfil', preferences: 'Preferencias', logout: 'Cerrar sesión' },
  overview: { ...ca.overview, welcome: 'Bienvenido a', description: 'Plataforma abierta de datos para la comunidad del tenis de mesa. Consulta resultados oficiales, estadísticas de jugadores y clasificaciones de las últimas temporadas.', note: 'En próximas fases incorporaremos una capa de analítica y comparación basada en IA, utilizando los LLM más actuales.', globalSearch: 'Búsqueda global', searchLabel: 'Buscar clubes, jugadores o partidos', searchPlaceholder: 'Buscar clubes, jugadores o partidos...', searchHint: 'Pulsa Enter o haz clic en Buscar.', minCharacters: 'Introduce como mínimo 2 caracteres.', quickAccess: 'Acceso rápido', explore: 'Explorar', findClubs: 'Encuentra clubes, equipos y resultados por categoría.', findPlayers: 'Consulta el rendimiento y las estadísticas de los jugadores.', findMatches: 'Busca partidos por fecha, competición o jugador.', statistics: 'Estadísticas', currentSeason: 'esta temp.', seasonInProgress: 'En curso', seasonUnavailable: 'No disponible', analytics: 'Analítica avanzada', analyticsDescription: 'Pronto podrás analizar, comparar y descubrir patrones con ayuda de inteligencia artificial.', moreInformation: 'Más información', statsError: 'No se han podido cargar las estadísticas en tiempo real.', statsEmpty: 'Todavía no hay estadísticas disponibles para la comunidad.', statsUnauthorized: 'La sesión no permite consultar las estadísticas de la comunidad.' },
  settings: { ...ca.settings, title: 'Configuración', description: 'Ajustes generales de la plataforma y preferencias de visualización.', preferences: 'Preferencias', notifications: 'Notificaciones', notificationsDescription: 'Gestiona los avisos de nuevos datos y cambios de temporada.', integrations: 'Integraciones', integrationsDescription: 'El módulo de analítica avanzada todavía no está disponible.', language: 'Idioma', languageDescription: 'Selecciona el idioma de la interfaz.', catalan: 'Catalán', spanish: 'Español', english: 'Inglés' },
  systemSettings: { ...ca.systemSettings, title: 'Configuración del sistema', description: 'Ajustes seguros para la configuración general, las notificaciones y la importación.', search: 'Buscar', category: 'Categoría', download: 'Descargar copia', restore: 'Restaurar copia', loading: 'Cargando configuración...', empty: 'No hay ajustes para estos filtros.', unauthorized: 'La sesión ha caducado.', forbidden: 'No tienes permisos para gestionar la configuración.', conflict: 'La configuración ha cambiado. Recarga antes de guardar.', serverError: 'No se ha podido cargar la configuración.', preview: 'Previsualizar', previewSuccess: 'Los valores son válidos. No se ha guardado ningún cambio.', apply: 'Aplicar cambios', saved: 'Configuración guardada.', restored: 'Copia restaurada correctamente.', restoreConfirm: 'La restauración sustituirá toda la configuración. ¿Continuar?', fileTooLarge: 'Las copias no pueden superar 1 MiB.', categories: { ALL: 'Todas', GENERAL: 'General', NOTIFICATIONS: 'Notificaciones', IMPORT: 'Importación' }, importFolderHint: 'Ruta absoluta de la carpeta del servidor donde se guardan los archivos importados (p. ej. c:\\tt-repository).' },
  administration: { administration: { title: 'Administración', description: 'Selecciona una opción de administración para continuar.' }, administrationUsers: { title: 'Usuarios y roles', description: 'Gestiona usuarios, roles y permisos de acceso.' }, administrationSettings: { title: 'Configuración del sistema', description: 'La configuración del sistema estará disponible próximamente.' }, administrationImport: { title: 'Importación de datos', description: 'El panel de importación de datos estará disponible próximamente.' }, administrationClubs: { title: 'Consolidación de clubes', description: 'Fusiona clubes duplicados en un único club canónico.' } },
  search: { ...ca.search, directory: 'Directorio', clubTitle: 'Buscar clubes', clubDescription: 'Encuentra un club para consultar su identidad, equipos y competiciones.', playerTitle: 'Buscar jugadores', playerDescription: 'Encuentra un jugador y consulta su identidad canónica y trayectoria.', fieldClub: 'Nombre del club', fieldPlayer: 'Nombre del jugador', byName: 'Buscar por nombre...', writeClub: 'Escribe el nombre de un club para empezar la búsqueda.', writePlayer: 'Escribe el nombre de un jugador para empezar la búsqueda.', searchingClubs: 'Buscando clubes...', searchingPlayers: 'Buscando jugadores...', clubsLoadError: 'No se han podido cargar los clubes. Inténtalo de nuevo.', playersLoadError: 'No se han podido cargar los jugadores.', sessionExpired: 'La sesión ha caducado.', noClubs: 'No se han encontrado clubes para «{{query}}».', noPlayers: 'No se han encontrado jugadores para «{{query}}».', resultsClubs: 'Clubes encontrados', resultsPlayers: 'Jugadores encontrados', canonicalPending: 'Identidad canónica pendiente', sources: 'Fuentes: {{sources}}', seasons: 'Temporadas: {{seasons}}', playerCount: '{{count}} jugadores · {{seasons}} temporadas' },
  matchesPage: { ...ca.matchesPage, title: 'Buscar partidos', description: 'Navega por resultados oficiales por jornada, competición y enfrentamientos directos.', building: 'Filtros de partidos', activeFilter: 'Filtro de club activo · ID: {{id}}', comingSoon: 'Próximamente', body: 'Busca partidos por fuente, temporada, competición, fecha y jugador.', filters: 'Filtros de partidos', source: 'Fuente', season: 'Temporada', competition: 'Competición', select: 'Selecciona', fromDate: 'Fecha inicial', toDate: 'Fecha final', playerName: 'Nombre del jugador', clubName: 'Nombre del club', playerLocation: 'Localización', either: 'Local o visitante', home: 'Local', away: 'Visitante', results: 'Resultados', loading: 'Cargando...', empty: 'No se han encontrado partidos.', error: 'No se han podido cargar los partidos.', unauthorized: 'No tienes permiso para consultar partidos.', paginationAriaLabel: 'Paginación de partidos', back: 'Volver a los partidos', lineups: 'Alineaciones', games: 'Partidos', round: 'Jornada', group: 'Grupo', phase: 'Fase', venue: 'Pabellón', winner: 'Ganador', referee: 'Árbitro', protested: 'Partido protestado', allSources: 'Todas las fuentes', dateRange: 'Intervalo de fechas', viewActa: 'Ver acta', actaUnavailable: 'Acta no disponible', actaJornada: 'Jornada {{round}}', actaVenue: 'Lugar', actaReferee: 'Árbitro', actaGamesTotal: 'Juegos', actaNoData: 'No hay datos de acta disponibles para este partido.' },
  results: { ...ca.results, title: 'Resultados de búsqueda', summary: 'Consulta transversal de clubes, jugadores y partidos a partir de tu criterio.', current: 'Consulta actual', searching: 'Buscando por: {{query}}', future: 'El módulo de resultados se integrará con {{endpoint}} en fases posteriores.', enter: 'Introduce un término de búsqueda de 2 caracteres o más.', example: 'Ejemplo: {{term}}', minimum: 'mínimo 2' },
  detail: { ...ca.detail, identityClub: 'Identidad del club', identityCanonical: 'Identidad canónica', editClub: 'Editar el club', clubNotFound: 'Club no encontrado', clubNotFoundDescription: 'El club solicitado no existe o el identificador no es válido.', clubLoadError: 'No se ha podido cargar el club', clubLoadDescription: 'Ha habido un problema al consultar esta información.', backSearch: 'Volver a la búsqueda', loadingClub: 'Cargando el club...', editDescription: 'Actualiza el nombre visible del club.', nameValidation: 'El nombre del club necesita al menos 2 caracteres.', permissionEdit: 'No tienes permisos para editar este club.', updateError: 'No se ha podido actualizar el club. Inténtalo de nuevo.', save: 'Guardar cambios', saving: 'Guardando...', updated: 'El nombre del club se ha actualizado correctamente.', clubViews: 'Vistas del club', allSources: 'Todas las fuentes', allSeasons: 'Todas las temporadas', noSeasons: 'Sin temporadas', allCompetitions: 'Todas las competiciones', federatedRecords: 'Registros federados: {{records}}', registeredPlayersEmpty: 'No hay jugadores registrados para los filtros seleccionados.', clubPlayers: 'Jugadores del club', competitionsEmpty: 'No hay resúmenes de competición disponibles para los filtros seleccionados.', clubCompetitions: 'Competiciones del club', matchesAvailable: '{{count}} partidos disponibles', clubMatchesEmpty: 'No hay partidos disponibles para los filtros seleccionados.', clubMatchesLoadError: 'No se han podido cargar los partidos del club.', unknownTeam: 'Equipo desconocido', viewCompetition: 'Ver la competición', competitionNotFound: 'Competición no encontrada', competitionNotFoundDescription: 'No se ha encontrado la competición solicitada para esta temporada.', competitionLoadError: 'No se ha podido cargar la competición', matchesQueryError: 'Ha habido un problema al consultar los partidos.', backClub: 'Volver al club', loadingCompetition: 'Cargando la competición...', competitionMatchesLabel: 'Partidos de la competición', competitionEmpty: 'No hay partidos disponibles para esta competición.', pendingResult: 'Resultado pendiente', win: 'Victoria', loss: 'Derrota', draw: 'Empate', round: 'Jornada {{round}}', playerNotFound: 'Jugador no encontrado', playerNotFoundDescription: 'El jugador solicitado no existe o el identificador no es válido.', playerLoadError: 'No se ha podido cargar el jugador', sessionExpiredDetails: 'La sesión ha caducado. Vuelve a iniciar sesión para continuar.', updatingPlayer: 'Actualizando los datos del jugador...', uuid: 'UUID: {{id}}', selectSeason: 'Selecciona la temporada', playerViews: 'Vistas del jugador', opponentAnalysis: 'Análisis de oponentes', opponentViews: 'Vistas del análisis de oponentes', statisticsHistory: 'Historial estadístico', statisticsEmpty: 'No hay datos estadísticos disponibles para los filtros seleccionados.', chartLegend: 'Leyenda del gráfico', historyValues: 'Valores del historial estadístico', opponentResults: 'Resultados por oponente', opponentsEmpty: 'Ningún oponente coincide con la búsqueda.', opponentSearch: 'Buscar un oponente', categoryFavorable: 'Oponentes favorables', categoryHard: 'Oponentes difíciles', categoryProblem: 'Oponentes problemáticos', categoryFavorableEmpty: 'No hay oponentes favorables para los filtros seleccionados.', categoryHardEmpty: 'No hay oponentes difíciles para los filtros seleccionados.', categoryProblemEmpty: 'No hay oponentes problemáticos para los filtros seleccionados.', categorySummary: '{{count}} oponentes en esta categoría.', searchSummary: '{{count}} oponentes coinciden con la búsqueda.', showMore: 'Mostrar {{count}} oponentes más', categoryFavorableLabel: 'Favorable', categoryHardLabel: 'Difícil', categoryProblemLabel: 'Problemático', categoryUnknown: 'Sin categoría', unavailableOpponent: 'Oponente no disponible', unavailableDate: 'Fecha no disponible', unavailableScore: 'Marcador no disponible', unavailableDetails: 'Detalle de oponentes no disponible.', game: 'Juego {{number}}', doubles: 'Dobles', singles: 'Individual', opponents: 'Oponentes', unavailablePlural: 'No disponibles', resultTeams: 'Resultado:', teams: 'Equipos:', careerSummary: 'Resumen de carrera', currentStreakLabel: 'Racha actual', longestWinStreak: 'Racha de victorias más larga', singlesWinPercentage: 'Porcentaje de victorias (individual)', doublesWinPercentage: 'Porcentaje de victorias (dobles)', averageSetMargin: 'Margen medio de sets', averageScore: 'Puntuación media', matchOrder: 'Orden de los partidos', spectrumChartAria: 'Espectro de resultados: calidad de cada partido jugado, desde derrota contundente hasta victoria contundente, en orden cronológico.' },
  auth: { ...ca.auth, loginTitle: 'Iniciar sesión', loginDescription: 'Accede a los datos de la liga de tenis de mesa.', loggingIn: 'Accediendo...', createAccount: 'Crear una cuenta', forgotPassword: '¿Has olvidado la contraseña?', registerTitle: 'Crea tu cuenta', registerDescription: 'Regístrate para consultar la información de la liga.', creatingAccount: 'Creando cuenta...', createAccountButton: 'Crear la cuenta', existingAccount: 'Ya tengo una cuenta', recoveryTitle: 'Recupera la contraseña', recoveryDescription: 'Indica tu correo y te enviaremos instrucciones si hay una cuenta asociada.', recoverySent: 'Si la cuenta existe, recibirás instrucciones para recuperar la contraseña.', sending: 'Enviando...', sendInstructions: 'Enviar instrucciones', newPassword: 'Nueva contraseña', newPasswordDescription: 'Elige una contraseña nueva para tu cuenta.', repeatPassword: 'Repite la contraseña', updating: 'Actualizando...', updatePassword: 'Actualizar contraseña', passwordUpdated: 'La contraseña se ha actualizado. Ya puedes iniciar sesión.', backLogin: 'Volver al inicio de sesión', passwordMismatch: 'Las contraseñas no coinciden.', registerSuccess: 'Cuenta creada. Ya puedes iniciar sesión.', invalidResponse: 'La respuesta de autenticación no es válida.', forbidden: 'Acceso no autorizado', forbiddenDescription: 'No tienes permisos para consultar esta página.', backHome: 'Volver al inicio' },
}

es.detail.opponentSearchTab = 'Buscar oponentes'
es.detail.opponentCategorizationTab = 'Categorización de oponentes'
es.detail.chartAria = 'Dispersión conectada: series de partidos jugados y porcentaje de victorias de todas las temporadas seleccionadas en un único gráfico. Escala vertical de partidos jugados y escala de porcentaje de victorias del 0% al 100%'
es.detail.opponentSort = 'Ordenar por'
es.detail.opponentSortDefault = 'Por defecto'
es.detail.opponentSortWinPercentage = 'Porcentaje de victorias'
es.detail.opponentSortMatches = 'Partidos jugados'
es.detail.opponentSortLastPlayed = 'Último enfrentamiento'
es.detail.recentForm = 'Forma reciente'
es.detail.streak = 'Racha'
es.detail.streakWin = 'Racha de {{count}} victorias'
es.detail.streakLoss = 'Racha de {{count}} derrotas'
es.detail.streakDraw = 'Racha de {{count}} empates'
es.detail.noStreak = '—'
es.detail.qualityStrongWin = 'Victoria contundente'
es.detail.qualityWin = 'Victoria clara'
es.detail.qualityCloseWin = 'Victoria ajustada'
es.detail.qualityDraw = 'Empate'
es.detail.qualityCloseLoss = 'Derrota ajustada'
es.detail.qualityLoss = 'Derrota clara'
es.detail.qualityStrongLoss = 'Derrota contundente'
es.detail.headToHeadColumn = 'Cara a cara'
es.detail.showHeadToHead = 'Mostrar el historial cara a cara'
es.detail.hideHeadToHead = 'Ocultar el historial cara a cara'
es.detail.headToHeadTitle = 'Historial cara a cara con {{name}}'
es.detail.headToHeadEmpty = 'No hay historial detallado disponible.'
es.detail.summaryTab = 'Resumen'
es.detail.statsTab = 'Estadísticas'
es.detail.summaryPlayersAllTime = '{{count}} en total'
es.detail.summaryMatchesAcrossSeasons = 'en {{count}} temporadas'
es.detail.winsAbbrev = 'V'
es.detail.drawsAbbrev = 'E'
es.detail.lossesAbbrev = 'D'
es.detail.summaryThisSeason = 'esta temporada'
es.detail.summaryRecentMatches = 'Partidos recientes'
es.detail.summaryTopPlayers = 'Jugadores con más competiciones'
es.detail.seeAll = 'Ver todo'
es.detail.summaryPlayerCompetitionsCount = '{{count}} competiciones'
es.detail.summaryRecordAria = 'Balance global: {{wins}} victorias, {{draws}} empates, {{losses}} derrotas'
es.detail.statsRecordTitle = 'Balance por competición'
es.detail.statsWinRateTrendTitle = 'Porcentaje de victorias por temporada'
es.detail.statsTrendEmpty = 'Todavía no hay suficientes datos de temporadas para mostrar una tendencia.'
es.detail.statsTrendAria = 'Tendencia del porcentaje de victorias por temporada: porcentaje de partidos ganados en cada temporada con datos disponibles, del 0% al 100%.'
es.detail.summaryTopPerformer = 'Mejor rendimiento'
es.detail.summaryPlayerWinRate = '{{winRate}}% · {{wins}}V-{{draws}}E-{{losses}}D'
es.detail.summaryTopPerformerEmpty = 'Ningún jugador tiene todavía suficientes partidos para aparecer en este ranking.'
es.detail.summaryPlayerMatchCount = '{{count}} partidos'
es.systemSettings.delete = 'Eliminar'
es.systemSettings.deleted = 'Configuración eliminada.'
es.systemSettings.deleteError = 'No se ha podido eliminar la configuración.'
es.systemSettings.confirmDelete = '¿Eliminar la configuración «{{name}}»?'

es.usersAdmin = {
  ...ca.usersAdmin,
  createUser: 'Crear usuario',
  createTitle: 'Nuevo usuario',
  editTitle: 'Editar {{username}}',
  searchLabel: 'Buscar por usuario o correo',
  searchStringLabel: 'Cadena de búsqueda:',
  searchPlaceholder: 'Buscar...',
  statusFilter: 'Estado',
  activeOnly: 'Activos',
  inactiveOnly: 'Inactivos',
  clearFilter: 'Limpiar',
  filterAriaLabel: 'Filtros de usuarios',
  listAriaLabel: 'Lista de usuarios',
  paginationAriaLabel: 'Paginación de usuarios',
  loading: 'Cargando usuarios...',
  empty: 'No se han encontrado usuarios.',
  loadError: 'No se han podido cargar los usuarios.',
  unauthorized: 'La sesión no permite gestionar usuarios.',
  forbidden: 'No tienes permisos para gestionar usuarios.',
  count: '{{count}} usuarios',
  active: 'Activo',
  inactive: 'Inactivo',
  edit: 'Editar',
  editAriaLabel: 'Editar {{username}}',
  deactivate: 'Desactivar',
  activate: 'Activar',
  deactivateAriaLabel: 'Desactivar {{username}}',
  activateAriaLabel: 'Activar {{username}}',
  confirmDeactivate: '¿Confirmas la desactivación de {{username}}?',
  confirmActivate: '¿Confirmas la activación de {{username}}?',
  confirm: 'Confirmar',
  usernameLabel: 'Nombre de usuario',
  emailLabel: 'Correo electrónico',
  passwordLabel: 'Contraseña',
  rolesLabel: 'Roles',
  requiredFields: 'El nombre de usuario y el correo electrónico son obligatorios.',
  passwordRequired: 'La contraseña es obligatoria para nuevos usuarios.',
  validationError: 'Los datos introducidos no son válidos.',
  conflictError: 'El nombre de usuario o el correo ya existe.',
  saveError: 'No se ha podido guardar el usuario.',
  saveSuccess: 'Usuario guardado correctamente.',
  activeToggleSuccess: 'Estado del usuario actualizado.',
  saving: 'Guardando...',
  save: 'Guardar',
  delete: 'Eliminar',
  deleteAriaLabel: 'Eliminar {{username}}',
  confirmDelete: 'Eliminar definitivamente al usuario {{username}}? Esta acción no se puede deshacer.',
  confirmDeleteAction: 'Eliminar definitivamente',
  deleteSuccess: 'Usuario eliminado correctamente.',
  deleteError: 'No se ha podido eliminar el usuario.',
  deleteActiveError: 'Solo se pueden eliminar usuarios desactivados.',
  role: {
    ADMIN: 'Administrador',
    CLUB_MANAGER: 'Gestor de clubes',
    ANALYST: 'Analista',
    PRACTITIONER: 'Practicante',
  },
  permission: {
    'users:read': 'Leer usuarios',
    'users:write': 'Editar usuarios',
    'clubs:read': 'Leer clubes',
    'clubs:write': 'Editar clubes',
    'players:read': 'Leer jugadores',
    'matches:read': 'Leer partidos',
    'analytics:read': 'Leer analítica',
  },
}

export default es

es.importPanel = {
  ...ca.importPanel,
  title: 'Importación de datos',
  description: 'Gestiona la importación de datos desde las fuentes federativas soportadas.',
  sourcesTitle: 'Fuentes soportadas',
  loading: 'Cargando fuentes...',
  sourcesEmpty: 'No hay fuentes configuradas en este entorno.',
  historyTitle: 'Historial de importación',
  historyComingSoon: 'El historial de importación estará disponible próximamente.',
  actionsTitle: 'Ejecutar una importación',
  sourceLabel: 'Fuente',
  chooseSource: 'Selecciona una fuente',
  preview: 'Previsualizar',
  status: 'Estado',
  validate: 'Validar',
  start: 'Iniciar',
  cancel: 'Cancelar',
  rollback: 'Revertir',
  searchLabel: 'Buscar en el historial',
  historyEmpty: 'No hay importaciones.',
  unauthorized: 'La sesión ha caducado.',
  forbidden: 'No tienes permisos para gestionar importaciones.',
  serverError: 'No se han podido cargar las fuentes de importación.',
  sourceDescription: {
    RFETM: 'Real Federación Española de Tenis de Mesa',
    BCNESA: 'Federación Catalana de Tenis de Mesa (BCNESA)',
    FCTT: 'Federación Catalana de Tenis de Mesa (FCTT)',
  },
  fileChooser: 'Archivo de importación',
  load: 'Cargar',
  simulate: 'Simular',
  import: 'Importar',
  resource: 'Recurso de importación',
  favourite: 'Marcar {{source}} como fuente favorita',
  sourceStatus: {
    available: 'Disponible',
    loading: 'Comprobando',
    unavailable: 'No disponible',
    error: 'Error',
  },
  statusLoading: 'Comprobando el estado de las fuentes...',
  statusUpdated: 'Estado de las fuentes actualizado.',
  statusError: 'No se ha podido actualizar el estado de las fuentes.',
  seasonsTitle: 'Temporadas de importación',
  seasonsLoading: 'Cargando temporadas...',
  seasonsEmpty: 'No hay temporadas de importación.',
  neverRun: 'Aún no ejecutada',
  ready: 'Preparada',
  reportTitle: 'Informe y estado',
  reportEmpty: 'Selecciona una temporada o inicia una importación.',
  fileReport: 'Importación de archivo',
  actionSuccess: 'Operación iniciada correctamente.',
  actionError: 'No se ha podido ejecutar la operación.',
  uploading: 'Subiendo el archivo…',
  uploadProgress: 'Progreso de la subida',
  uploadProgressValue: 'Subida: {{progress}}%',
  uploadSuccess: 'Archivo aceptado. El estado se está actualizando.',
  uploadError: 'No se ha podido subir el archivo. Inténtalo de nuevo.',
  retryUpload: 'Reintentar subida',
  invalidFile: 'Selecciona un archivo ZIP de importación no vacío.',
  resourcesTitle: 'Recursos de importación',
  resourcesLoading: 'Cargando recursos de importación...',
  resourcesEmpty: 'No hay recursos de importación para esta fuente.',
  resourcesImportedTitle: 'Importados',
  resourcesPendingTitle: 'Pendientes',
  resourceProcessing: 'Importando…',
  resourceReady: 'Preparado',
  resourceType: 'Tipo de recurso',
  resourceSeason: 'Temporada',
  resourceUploaded: 'Subido',
  resourceProcessed: 'Procesado',
  notProcessed: 'Aún no procesado',
  unavailable: 'No disponible',
  previewTitle: 'Previsualización de importación',
  previewEmpty: 'Selecciona un recurso y pulsa Simular para revisar el resultado.',
  previewLoading: 'Simulando {{resource}}...',
  previewSuccess: 'La simulación ha validado {{count}} elemento(s). Revisa los avisos antes de importar.',
  previewEmptyResult: 'La simulación no ha encontrado ningún resultado para validar.',
  previewFailure: 'La simulación ha fallado. Revisa los errores de procesamiento.',
  previewRetry: 'Volver a simular',
  previewProceed: 'Proceder a importar',
  previewProceeding: 'Iniciando importación...',
  previewFindings: 'Validaciones',
  previewErrors: 'Errores de procesamiento',
  previewNoFindings: 'No hay avisos de validación.',
  previewNoErrors: 'No hay errores de procesamiento.',
  previewFilesSeen: 'Archivos leídos',
  previewItemsValidated: 'Elementos validados',
  previewSkipped: 'Omitidos',
  previewStatus: {
    loading: 'Cargando',
    success: 'Correcta',
    'empty-result': 'Sin resultados',
    failure: 'Fallida',
  },
  processTitle: 'Resultado de la importación',
  processEmpty: 'Selecciona un recurso y pulsa Importar para revisar el resultado.',
  processLoading: 'Importando {{resource}}...',
  processSuccess: 'La importación ha guardado {{count}} elemento(s).',
  processEmptyResult: 'La importación no ha producido ningún resultado.',
  processFailure: 'La importación ha fallado. Revisa los errores de procesamiento.',
  processRetry: 'Reintentar importación',
  processBackToResources: 'Volver a los recursos',
  processFindings: 'Validaciones',
  processErrors: 'Errores de procesamiento',
  processNoFindings: 'No hay avisos de validación.',
  processNoErrors: 'No hay errores de procesamiento.',
  processFilesSeen: 'Archivos leídos',
  processItemsPersisted: 'Elementos guardados',
  processSkipped: 'Omitidos',
  processProgressLabel: 'Progreso de la importación',
  processProgressIndeterminate: 'Calculando el progreso...',
  processProgressValue: 'Progreso: {{percentage}}%',
  processProcessed: 'Procesados',
  processTotal: 'Total',
  processErrorsCount: 'Errores',
  processStatus: {
    queued: 'En cola',
    running: 'En curso',
    loading: 'Cargando',
    success: 'Correcta',
    'empty-result': 'Sin resultados',
    failure: 'Fallida',
  },
}

es.clubsConsolidation = {
  ...ca.clubsConsolidation,
  title: 'Consolidación de clubes',
  description: 'Selecciona varios clubes duplicados para fusionarlos en un único club canónico, conservando equipos, jugadores y partidos.',
  selectAll: 'Selecciona todos los resultados',
  selectClub: 'Selecciona {{name}}',
  selectedCount: '{{count}} clubes seleccionados',
  consolidate: 'Consolida',
  consolidateAriaLabel: 'Consolida los clubes seleccionados',
  dialogTitle: 'Consolida los clubes seleccionados',
  canonicalNameLabel: 'Nombre canónico',
  primaryClubLabel: 'Club principal',
  confirm: 'Consolida',
  consolidating: 'Consolidando...',
  success: 'Los clubes se han consolidado correctamente.',
  error: 'No se han podido consolidar los clubes.',
}
