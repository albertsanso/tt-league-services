# Pla d'implementació — Mockup responsive inicial de TT League

## Objectiu

Construir un mockup funcional d'una SPA de TT League en català, amb tema clar
professional, layout de dashboard i la pàgina Overview com a primera
experiència. El mockup ha de ser navegable, responsive en desktop, tablet i
mòbil, accessible i preparat per substituir les dades mock per les respostes
de l'API.

## Abast

- Shell global amb sidebar, top bar, breadcrumb, dropdown d'usuari i footer.
- Ruta `/` amb hero, cercador global, accés ràpid, estadístiques i banner
  d'analítica.
- Rutes de navegació `/clubs`, `/jugadors`, `/partits`, `/cerca` i
  `/settings`, amb pàgines inicials senzilles perquè els enllaços siguin
  verificables. `/analytics` es mostrarà com a opció deshabilitada.
- Sistema de design tokens, tipografia i components reutilitzables.
- Estat local per al col·lapse del sidebar, el drawer mòbil, el dropdown i
  les dades de la comunitat.
- Responsive amb els tres comportaments especificats: desktop (a partir de
  1280 px), tablet (768–1279 px) i mòbil (menys de 768 px).
- Accessibilitat, focus visible, `prefers-reduced-motion` i transicions
  discretes.

## Recursos i eines

- React 18 o superior.
- Tailwind CSS 3 o superior i CSS global amb variables `:root`.
- React Router 6 o superior per a les rutes i el breadcrumb.
- Lucide React amb `stroke-width={1.5}` per a les icones.
- Inter per a la interfície i DM Mono o JetBrains Mono exclusivament per a
  xifres estadístiques.
- React Context o Zustand per a l'estat transversal; es recomana Context per
  mantenir el mockup lleuger.
- `IntersectionObserver` per activar el `countUp` de les estadístiques.
- Navegador amb DevTools responsive i una eina de comprovació de contrast
  WCAG AA.

## Pla pas a pas

### 1. Preparar l'estructura base i els tokens — 45 min

1. Crear o confirmar l'entrada de la SPA React i la configuració de Tailwind.
2. Afegir les fonts Inter i DM Mono/JetBrains Mono mitjançant una càrrega
   explícita i fiable.
3. Definir al `:root` tots els tokens de superfícies, accents, text, vores i
   feedback indicats a l'especificació.
4. Configurar el reset global, el color de fons `#F0F4F8`, els stacks
   tipogràfics i els estats de focus.
5. Verificar que no s'introdueixen gradients, paletes pastel per secció,
   ombres grans ni arrodoniments superiors a `rounded-lg`.

**Resultat:** base visual estable amb un únic tema clar i tokens reutilitzables.

### 2. Crear primitives i components UI compartits — 45 min

1. Implementar `SectionLabel` amb el label institucional i la línia
   horitzontal extensible.
2. Implementar `AccentBar` i `Badge` amb les variants mínimes necessàries.
3. Definir focus rings comuns per a botons, links, inputs i controls.
4. Fer que tots els components acceptin text i comportament per props, evitant
   duplicar markup entre targetes i seccions.
5. Mantenir cada component per sota de 150 línies.

**Resultat:** primitives coherents per construir totes les superfícies del
mockup.

### 3. Implementar la navegació i el layout del dashboard — 1 h 15 min

1. Crear la configuració de navegació amb les icones i rutes exactes:
   `LayoutDashboard`, `Building2`, `Users`, `Swords`, `BarChart3` i `Settings`.
2. Implementar `Sidebar`, `SidebarItem`, `SidebarSectionLabel` i
   `SidebarFooter`.
3. Aplicar el sidebar expandit `w-60`, el col·lapsat `w-16`, la transició de
   300 ms i els estats actiu, inactiu i disabled.
4. Implementar `TopBar`, `Breadcrumb`, `NotificationBell` i `UserDropdown`.
5. Connectar el toggle del sidebar a l'estat compartit i afegir el drawer
   overlay en mòbil amb backdrop i transició lateral.
6. Construir `DashboardLayout` amb `Outlet`, skip link i footer.
7. Afegir els atributs ARIA requerits: navegació principal, breadcrumb,
   `aria-current`, `aria-expanded`, `role="menu"` i tancament amb `Escape`.

**Resultat:** shell navegable i accessible, independent del contingut de cada
pàgina.

### 4. Configurar rutes i pàgines inicials — 35 min

1. Declarar les rutes amb React Router 6 i muntar-les sota
   `DashboardLayout`.
2. Fer lazy loading de les pàgines.
3. Crear `OverviewPage` com a ruta `/`.
4. Crear pàgines inicials per a clubs, jugadors, partits, resultats de cerca i
   configuració, conservant el mateix shell i una jerarquia visual coherent.
5. Fer que Analytics aparegui al sidebar com a disabled i no com una ruta
   activa.
6. Fer que el breadcrumb es derivi de la ruta actual mitjançant
   `useBreadcrumb`.

**Resultat:** totes les destinacions visibles tenen una navegació verificable i
el context de ruta és automàtic.

### 5. Construir el contingut de l'Overview — 1 h 30 min

1. Implementar `HeroBanner` amb el text de benvinguda en català, `AccentBar`,
   nota informativa i formes geomètriques abstractes subtils.
2. Implementar `GlobalSearch` amb lupa, placeholder, focus ring, botó
   `Cercar`, validació mínima de dos caràcters i debounce de 300 ms.
3. Fer que Enter o el clic naveguin a `/cerca?q={query}` i conservar la query
   codificada.
4. Implementar `QuickAccessGrid` i `QuickAccessCard` per a clubs, jugadors i
   partits, amb layout 3/2/1 columnes i targetes d'alçada uniforme.
5. Implementar `CommunityStats` i `StatCard` amb les quatre mètriques:
   `1.248`, `186`, `8.432` i `24/25`, així com els deltes i l'estat de
   temporada.
6. Implementar `AnalyticsBanner` amb fons `#1E3A5F`, text secundari blau clar
   i CTA blanc sòlid.
7. Compondre la pàgina amb `gap-10`, padding responsive i
   `max-w-[1360px] mx-auto`.

**Resultat:** Overview visualment completa i alineada amb l'especificació de
contingut, jerarquia i interaccions.

### 6. Afegir dades mock i el hook d'estadístiques — 40 min

1. Definir el model de dades corresponent a
   `GET /api/stats/community`.
2. Implementar `useCommunityStats()` amb una font mock explícita i una
   interfície preparada per a `fetch`.
3. Representar carregant i error de forma visible, sense convertir errors en
   dades aparentment correctes.
4. Centralitzar els valors de demostració i conservar els noms de propietat de
   l'API (`jugadors`, `clubs`, `partits`, `temporada`).
5. Deixar documentada la forma de `GET /api/cerca` per a la futura pàgina de
   resultats, sense inventar dades que no defineix l'especificació.

**Resultat:** la UI funciona sense backend i manté un contracte clar per a la
integració posterior.

### 7. Implementar animacions i preferències de moviment — 35 min

1. Crear `useCountUp` amb durada de 800 ms i easing `easeOutExpo`.
2. Activar l'animació quan la targeta entra al viewport amb
   `IntersectionObserver` i threshold `0.3`.
3. Mostrar el valor final immediatament quan
   `prefers-reduced-motion: reduce` estigui actiu.
4. Aplicar només les transicions prescrites: hover de targetes, hover dels
   ítems, expand/collapse del sidebar, CTA, fletxa d'exploració i dropdown.
5. Assegurar que les animacions no bloquegen el teclat ni el tancament del
   drawer/dropdown.

**Resultat:** moviment útil i discret, amb una alternativa accessible sense
animació.

### 8. Completar el responsive layout — 45 min

1. Validar desktop amb sidebar fix `w-60`, contingut `p-8`, stats en quatre
   columnes i accés ràpid en tres.
2. Adaptar tablet a sidebar `w-16`, contingut `p-6` i ambdues graelles en dues
   columnes.
3. Adaptar mòbil a top bar amb menú, contingut `p-4`, accés ràpid en una
   columna i stats en una graella compacta 2x2.
4. Activar el sidebar com a drawer overlay mòbil amb `bg-black/40`, focus
   gestionat i tancament en seleccionar una ruta o prémer `Escape`.
5. Comprovar que el cercador, banner i textos no provoquen overflow horitzontal.

**Resultat:** comportament responsive funcional als tres breakpoints indicats,
incloent navegació tàctil.

### 9. Passada d'accessibilitat i qualitat visual — 45 min

1. Revisar landmarks, ordre de tabulació, noms accessibles de les icones i
   estat actiu de la ruta.
2. Verificar focus visible en tots els elements interactius i el skip-to-content.
3. Verificar el focus trap del dropdown, `Escape`, `aria-expanded` i menú.
4. Revisar els contrastos especificats i corregir qualsevol text que no
   compleixi WCAG AA.
5. Revisar que el mono només s'utilitza per a les estadístiques numèriques i
   que els labels no tenen uppercase innecessari.
6. Comparar cada secció amb el checklist visual de l'especificació.

**Resultat:** mockup usable amb teclat, contrast adequat i coherència visual.

### 10. Verificació final i documentació d'execució — 30 min

1. Executar el build i les comprovacions disponibles del projecte frontend.
2. Recórrer manualment totes les rutes i els estats d'interacció principals.
3. Comprovar la consola del navegador per errors de rutes, claus React o
   peticions fallides inesperades.
4. Revisar desktop, tablet i mòbil en amplades representatives, incloent
   `prefers-reduced-motion`.
5. Actualitzar el README del mòdul només amb els passos d'instal·lació,
   execució, configuració i assumpcions del mockup si aquests canvien.
6. Revisar el diff per garantir que només s'han inclòs fitxers del mockup i
   que no hi ha sortides generades ni secrets.

**Resultat:** implementació preparada per a revisió i integració posterior.

## Estructura de fitxers prevista

```text
src/
├── App.jsx
├── index.css
├── layouts/
│   └── DashboardLayout.jsx
├── components/
│   ├── sidebar/
│   │   ├── Sidebar.jsx
│   │   ├── SidebarItem.jsx
│   │   ├── SidebarSectionLabel.jsx
│   │   └── SidebarFooter.jsx
│   ├── topbar/
│   │   ├── TopBar.jsx
│   │   ├── Breadcrumb.jsx
│   │   ├── NotificationBell.jsx
│   │   └── UserDropdown.jsx
│   ├── overview/
│   │   ├── HeroBanner.jsx
│   │   ├── GlobalSearch.jsx
│   │   ├── QuickAccessGrid.jsx
│   │   ├── QuickAccessCard.jsx
│   │   ├── CommunityStats.jsx
│   │   ├── StatCard.jsx
│   │   └── AnalyticsBanner.jsx
│   └── ui/
│       ├── SectionLabel.jsx
│       ├── AccentBar.jsx
│       └── Badge.jsx
├── pages/
│   ├── OverviewPage.jsx
│   ├── ClubsSearchPage.jsx
│   ├── PlayersSearchPage.jsx
│   ├── MatchesSearchPage.jsx
│   ├── SearchResultsPage.jsx
│   └── SettingsPage.jsx
├── hooks/
│   ├── useBreadcrumb.js
│   ├── useSidebarCollapse.js
│   └── useCountUp.js
├── config/
│   ├── navigation.js
│   └── routes.js
└── styles/
    └── theme.js
```

## Criteris d'acceptació

- [ ] El tema usa `#F0F4F8` com a fons general, `#FFFFFF` per a les cards i
  `#1E3A5F` com a únic accent institucional principal.
- [ ] El sidebar mostra correctament els estats actiu, inactiu i disabled, i
  es pot col·lapsar o obrir com a drawer.
- [ ] El top bar mostra breadcrumb, notificacions i dropdown d'usuari amb els
  estats de teclat i ARIA definits.
- [ ] Cada secció de l'Overview usa `SectionLabel` amb línia horitzontal.
- [ ] El cercador valida dos caràcters, aplica debounce i navega a la ruta de
  resultats.
- [ ] Les targetes d'accés ràpid tenen les tres destinacions i els layouts
  3/2/1 corresponents.
- [ ] Les estadístiques tenen font mono, vora superior accentuada, `countUp`,
  `IntersectionObserver` i mode reduït.
- [ ] El banner d'analítica usa fons fosc i CTA blanc sense gradient.
- [ ] Els tres breakpoints són funcionals sense overflow horitzontal.
- [ ] Els interactius tenen focus visible i la navegació compleix WCAG AA
  segons els contrastos especificats.
- [ ] Les transicions respecten `prefers-reduced-motion`.
- [ ] Cap component supera 150 línies i no s'han afegit fitxers generats o
  secrets.

## Estimació total

**7 hores i 10 minuts**, sense incloure la integració posterior amb serveis API
reals ni la creació de pantalles de negoci completes per a clubs, jugadors i
partits.
