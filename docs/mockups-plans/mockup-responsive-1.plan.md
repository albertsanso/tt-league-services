# Pla de construcció del mockup responsive 1

## Objectiu

Transformar `tt-data-league-frontend`, actualment una aplicació Vite/React
mínima, en una SPA responsive mobile-first per a TT League. La interfície ha
de conservar totes les funcionalitats de la proposta d'escriptori, adaptar el
layout a mòbil, i mantenir els textos de la UI en català.

## Abast

Inclou:

- Layout compartit amb drawer mòbil, sidebar de tablet/escriptori i top bar.
- Navegació amb React Router i breadcrumbs generats a partir de la ruta.
- Pantalla Overview (`/`) amb benvinguda, cerca global, accessos ràpids,
  estadístiques i banner d'Analytics.
- Pantalles responsive per a clubs, jugadors, partits, resultats globals i
  configuració.
- Integració preparada per als contractes d'API de cerca i estadístiques.
- Accessibilitat de teclat i tàctil, focus, contrast, safe areas i
  `prefers-reduced-motion`.
- Validació visual a mòbil, tablet i escriptori.

No inclou:

- Implementació real d'Analytics; ha de continuar deshabilitada i mostrar
  `"Pròximament"`.
- Autenticació real o persistència de preferències d'usuari.
- Disseny d'una aplicació mòbil separada.
- Canvis als mòduls Java del backend que no siguin necessaris per exposar els
  contractes ja definits.

## Estat inicial i decisions tècniques

- El frontend és un projecte Vite amb React 19 i JavaScript JSX.
- La pantalla actual és el starter de Vite i s'ha de substituir, no adaptar
  incrementalment.
- Afegir React Router, Tailwind CSS i Lucide React com a dependències del
  frontend.
- Utilitzar `createBrowserRouter` amb un `DashboardLayout` i un `Outlet`.
- Utilitzar els mateixos components per a tots els viewports; només el layout
  i la presentació canvien amb breakpoints Tailwind.
- Mantenir l'estat mínim: drawer i menús al layout; dades remotes amb
  `fetch`/`useState`; Context només si cal compartir estat transversal.
- Utilitzar noms de components, funcions i variables en anglès, amb textos
  visibles en català.

## Pla d'implementació

### 1. Preparar les eines i els estils base — 30–45 min

Fitxers principals:

- `tt-data-league-frontend/package.json`
- `tt-data-league-frontend/package-lock.json`
- `tt-data-league-frontend/tailwind.config.js`
- `tt-data-league-frontend/postcss.config.js`
- `tt-data-league-frontend/src/index.css`
- `tt-data-league-frontend/index.html`

Tasques:

1. Afegir `react-router-dom`, `lucide-react`, Tailwind CSS i la configuració
   necessària de PostCSS/Vite.
2. Eliminar els estils i imports del starter de Vite.
3. Configurar `lang="ca"`, el títol `TT League` i el viewport.
4. Configurar Inter amb fallback `system-ui, sans-serif`.
5. Definir els tokens visuals de la proposta: indigo, orange, emerald,
   amber, purple, slate, gray, green i red.
6. Afegir els estils mínims no resolubles amb Tailwind: animació `fadeIn` i
   padding de `env(safe-area-inset-*)`.
7. Afegir `overflow-x-hidden`, `min-h-svh` i una base compatible amb
   navegadors mòbils.

### 2. Definir la configuració de navegació i les rutes — 45–60 min

Fitxers principals:

- `src/App.jsx`
- `src/data/navigation.js`
- `src/pages/OverviewPage.jsx`
- `src/pages/ClubsSearchPage.jsx`
- `src/pages/PlayersSearchPage.jsx`
- `src/pages/MatchesSearchPage.jsx`
- `src/pages/SearchResultsPage.jsx`
- `src/pages/SettingsPage.jsx`

Tasques:

1. Definir a `data/navigation.js` les entrades `Overview`, `Clubs search`,
   `Practitioners search`, `Matches search`, `Analytics` i `Settings`.
2. Associar les rutes `/`, `/clubs`, `/jugadors`, `/partits`, `/analytics` i
   `/settings`.
3. Afegir les rutes de detall `/clubs/:id` i `/jugadors/:id`, i la ruta
   `/cerca`.
4. Marcar Analytics com a visible però disabled, sense navegació.
5. Configurar lazy loading amb `React.lazy()` i `Suspense`.
6. Afegir una pantalla de càrrega accessible per a la transició de pàgines.
7. Mantenir funcionals els botons enrere/endavant del navegador.

### 3. Implementar el layout responsive compartit — 60–90 min

Fitxers principals:

- `src/layouts/DashboardLayout.jsx`
- `src/components/sidebar/Sidebar.jsx`
- `src/components/sidebar/SidebarItem.jsx`
- `src/components/sidebar/SidebarFooter.jsx`
- `src/hooks/useSidebarCollapse.js`

Tasques:

1. Crear el layout de tres zones: sidebar, top bar i àrea de contingut.
2. Fer que a menys de `md` la sidebar sigui un drawer superposat amb:
   `w-[min(18rem,85vw)]`, `h-screen`, `z-50` i `bg-slate-900`.
3. Afegir backdrop `bg-black/50`, tancament per clic fora, botó explícit i
   tecla Escape.
4. Gestionar focus: posar-lo dins del drawer, impedir que s'escapi, retornar-lo
   al botó d'obertura i donar-li un nom accessible.
5. Bloquejar el scroll del document mentre el drawer està obert, mantenint
   possible el scroll intern del menú.
6. Tancar el drawer automàticament després d'un canvi de ruta.
7. Implementar transicions de transformació i opacitat, reduïdes amb
   `prefers-reduced-motion`.
8. Mostrar a tablet una sidebar `w-16` només amb icones.
9. Mostrar a escriptori una sidebar `w-56`, col·lapsable a `w-16`.
10. Aplicar `aria-current="page"` a l'element actiu i àrees tàctils mínimes de
    44px.
11. Afegir el footer del drawer amb el text de la comunitat.

### 4. Implementar la top bar i la navegació contextual — 45–60 min

Fitxers principals:

- `src/components/topbar/TopBar.jsx`
- `src/components/topbar/Breadcrumb.jsx`
- `src/components/topbar/NotificationBell.jsx`
- `src/components/topbar/UserMenu.jsx`
- `src/hooks/useBreadcrumb.js`

Tasques:

1. Crear una top bar `h-14` en mòbil i `h-16` a partir de tablet.
2. Col·locar el botó de menú, breadcrumb, notificacions i usuari en un layout
   que no produeixi overflow a 320px.
3. Generar el breadcrumb automàticament a partir de `useLocation()`.
4. Implementar les rutes:
   - `/` → `General › Overview`
   - `/clubs` → `General › Clubs search`
   - `/clubs/:id` → `Clubs › [Nom Club]`
   - `/jugadors` → `General › Practitioners search`
   - `/jugadors/:id` → `Jugadors › [Nom Jugador]`
   - `/partits` → `Resultats › Matches search`
5. Afegir `nav aria-label="Breadcrumb"`, links als nivells superiors i
   preservació accessible dels nivells que es redueixin visualment.
6. Implementar la campana amb badge vermell quan hi hagi notificacions.
7. Implementar avatar amb inicials i menú amb `Perfil`, `Preferències` i
   `Tancar`.
8. Fer que el menú d'usuari es tanqui amb Escape o clic fora i mai surti del
   viewport.

### 5. Crear components UI reutilitzables — 30–45 min

Fitxers principals:

- `src/components/ui/Button.jsx`
- `src/components/ui/Card.jsx`
- `src/components/ui/Badge.jsx`
- `src/components/ui/LoadingState.jsx`
- `src/components/ui/ErrorState.jsx`

Tasques:

1. Centralitzar variants d'acció primària, secundària, disabled i icon-only.
2. Garantir `min-h-11`, focus visible, labels accessibles i estats disabled
   explícits.
3. Fer que les cards mantinguin el wrapping del text i no fixin altures que
   tallin traduccions.
4. Reutilitzar els estats de càrrega, error i buit a totes les pantalles.

### 6. Construir l'Overview mobile-first — 90–120 min

Fitxers principals:

- `src/pages/OverviewPage.jsx`
- `src/components/overview/WelcomeBanner.jsx`
- `src/components/overview/GlobalSearch.jsx`
- `src/components/overview/QuickAccessGrid.jsx`
- `src/components/overview/QuickAccessCard.jsx`
- `src/components/overview/CommunityStats.jsx`
- `src/components/overview/StatCard.jsx`
- `src/components/overview/AnalyticsBanner.jsx`

Tasques:

1. Compondre la pàgina amb `flex-col`, `gap-6` i `p-4` a mòbil.
2. Implementar el bloc de benvinguda amb text complet, imatge centrada i
   avís sobre Analytics futura.
3. Implementar la cerca global amb input `text-base`, botó en una línia
   separada a mòbil i fila a `sm`.
4. Rebutjar queries de menys de dos caràcters amb missatge visible i
   `aria-describedby`; permetre també l'enviament amb Enter.
5. Afegir debounce de 300 ms com a preparació per a suggeriments futurs.
6. Crear les tres cards d'accés ràpid com a `<Link>` clicable complet:
   `/clubs`, `/jugadors` i `/partits`.
7. Implementar les estadístiques en una columna a mòbil, dues columnes a
   `sm` i quatre a `lg`, sense tallar valors.
8. Afegir el banner gradient d'Analytics amb botó a tota amplada en mòbil.
9. No activar Analytics ni crear una pantalla funcional falsa.

### 7. Connectar dades i pantalles de cerca — 60–90 min

Fitxers principals:

- `src/hooks/useCommunityStats.js`
- `src/hooks/useSearch.js`
- `src/context/AppContext.jsx` (només si és necessari)
- `src/pages/SearchResultsPage.jsx`
- pàgines de clubs, jugadors i partits

Contractes:

```text
GET /api/stats/community
GET /api/cerca?q={query}&tipus={clubs|jugadors|partits|tots}
```

Tasques:

1. Implementar el fetch de `/api/stats/community` amb el model:
   `jugadors`, `clubs`, `partits` i `temporada`.
2. Permetre fixtures/mock data només per al desenvolupament local o mentre
   l'API no estigui disponible; no convertir-les en la solució definitiva.
3. Implementar resultats globals amb query param `q`, tipus, total, pàgina i
   mida de pàgina.
4. Afegir loading, error, empty state i retry visibles.
5. Preparar les pantalles específiques perquè comparteixin components de
   cerca i no duplicquin la lògica de dades.

### 8. Completar accessibilitat i qualitat responsive — 60–90 min

Tasques:

1. Revisar àrees tàctils de tots els links, botons i controls d'icona.
2. Verificar navegació completa amb teclat, focus visible, labels i landmarks.
3. Verificar contrast WCAG AA al drawer, badges, cards, focus i gradient.
4. Afegir suport de `prefers-reduced-motion`.
5. Verificar safe areas superior, inferior, esquerra i dreta.
6. Provar wrapping de català, breadcrumbs llargs, noms de clubs/jugadors i
   valors estadístics.
7. Confirmar que no hi ha scroll horitzontal a 320px, 375px ni 430px.
8. Confirmar que obrir el drawer no canvia la ruta i que navegar el tanca.

### 9. Validar i documentar — 45–60 min

Fitxers:

- `tt-data-league-frontend/README.md`

Tasques:

1. Executar `npm run lint`.
2. Executar `npm run build`.
3. Validar manualment els viewports 320px, 375px, 430px, tablet i escriptori,
   en orientació vertical i horitzontal.
4. Provar el drawer, dropdown, cerca, breadcrumbs, navegació i Analytics
   disabled amb teclat i tàctil.
5. Documentar instal·lació, desenvolupament, build, API esperada i criteris
   responsive al README del frontend.
6. Revisar el diff per evitar assets generats, secrets o fitxers `target/`.

## Criteris d'acceptació

- [ ] La SPA renderitza una Overview en català i no conserva el starter de Vite.
- [ ] No hi ha scroll horitzontal als viewports mòbils especificats.
- [ ] El drawer mòbil comença tancat, té backdrop, botó de tancament, Escape,
  focus gestionat i scroll lock.
- [ ] Tablet i escriptori mostren la sidebar corresponent i permeten
  col·lapsar-la quan aplica.
- [ ] El menú actiu mostra `aria-current="page"` i Analytics és visible però
  disabled amb `"Pròximament"`.
- [ ] El breadcrumb és automàtic, accessible i coherent amb la ruta.
- [ ] La cerca valida dues lletres mínimes i navega a `/cerca?q=...`.
- [ ] Les cards d'accés ràpid naveguen a les tres rutes correctes.
- [ ] Les estadístiques consumeixen o simulen de forma substituïble
  `GET /api/stats/community` i tenen estats de loading/error.
- [ ] El banner d'Analytics conserva el gradient i no activa funcionalitat
  inexistent.
- [ ] Tots els controls interactius tenen focus visible, labels accessibles i
  una àrea tàctil mínima de 44px.
- [ ] Es respecten WCAG AA, safe areas i `prefers-reduced-motion`.
- [ ] `npm run lint` i `npm run build` finalitzen correctament.

## Recursos necessaris

- Especificació: `docs/mockups-prompts/mockup-responsive-1.prompt.md`.
- Projecte frontend: `tt-data-league-frontend/`.
- React 19 i Vite ja existents.
- React Router, Tailwind CSS i Lucide React.
- Navegador amb eines responsive i inspecció d'accessibilitat.

## Estimació total

**8–11 hores**, segons el grau de detall de les pantalles de cerca i si els
endpoints del backend ja estan disponibles. La implementació del layout,
Overview i accessibilitat és prioritària; les pantalles de cerca poden començar
amb fixtures substituïbles mentre es connecten els endpoints reals.
