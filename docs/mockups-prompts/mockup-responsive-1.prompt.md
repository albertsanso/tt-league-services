# TT League — Especificació UI mòbil per a Agent LLM

## Meta

| Camp              | Valor                                                     |
|-------------------|-----------------------------------------------------------|
| Projecte          | TT League                                                 |
| Tipus             | Aplicació web SPA                                         |
| Stack             | React 18+ · Tailwind CSS 3+ · React Router 6+             |
| Idioma UI         | Català                                                    |
| Target principal  | Navegador mòbil, viewport estret i tàctil                 |
| Responsive        | Mobile-first, adaptable a tablet i escriptori             |
| Icones            | Lucide React                                               |
| Estat             | React Context o Zustand                                   |

Aquest document conserva tots els requisits funcionals de
`mockup-desktop-1.prompt.md`, però defineix la seva presentació i interacció
per a un navegador mòbil. La implementació ha de ser una única aplicació React
responsive: no crear una aplicació mòbil separada ni eliminar funcionalitats en
viewports petits.

---

## 1. Principis de disseny mòbil

- Disseny mobile-first amb `viewport` configurat i sense scroll horitzontal.
- Una sola columna per defecte; les columnes només s'amplien amb breakpoints
  quan hi ha espai suficient.
- Àrees tàctils d'almenys `44px × 44px`, inclosos botons d'icona, enllaços i
  controls del menú.
- Respectar `env(safe-area-inset-top)`,
  `env(safe-area-inset-right)`, `env(safe-area-inset-bottom)` i
  `env(safe-area-inset-left)` quan el navegador ho requereixi.
- No dependre del hover per comunicar estat o permetre una acció; el hover és
  només un reforç per a dispositius que el suportin.
- El text pot ocupar diverses línies. No truncar informació funcional
  important per aconseguir una única línia.
- Els controls i les targetes han de mantenir espai suficient entre ells per
  evitar pulsacions accidentals.

### Breakpoints

```text
Mòbil (<768px):
┌──────────────────────────────────────┐
│ TopBar h-14                    ☰     │
├──────────────────────────────────────┤
│                                      │
│ Content p-4, una columna             │
│                                      │
└──────────────────────────────────────┘

Tablet (768-1023px):
┌──────┬───────────────────────────────┐
│ w-16 │ TopBar                        │
│ icones│──────────────────────────────│
│      │ Content p-4, graelles reduïdes│
└──────┴───────────────────────────────┘

Escriptori (≥1024px):
┌──────────┬─────────────────────────────┐
│ Sidebar  │ TopBar                      │
│ w-56     │─────────────────────────────│
│ visible  │ Content p-6                 │
└──────────┴─────────────────────────────┘
```

Els canvis de layout han de fer-se amb classes responsive de Tailwind i no amb
detecció de l'agent d'usuari.

---

## 2. Arquitectura de layout global

L'aplicació segueix les mateixes tres zones lògiques —sidebar, top bar i àrea
de contingut—, però en un mòbil la sidebar és un drawer superposat.

```text
VIEWPORT MÒBIL (100vw × 100vh)
┌──────────────────────────────────────┐
│ TOP BAR (h-14, sticky top, z-40)     │
│ [☰]  General › Overview       🔔 [TT]│
├──────────────────────────────────────┤
│                                      │
│ MAIN CONTENT (overflow-y-auto)       │
│ bg-gray-50, p-4                      │
│                                      │
│  Contingut dinàmic segons ruta       │
│                                      │
│  FOOTER text-center, text-sm, py-4   │
│                                      │
└──────────────────────────────────────┘

Quan el drawer és obert:
┌──────────────────────────────────────┐
│ backdrop bg-black/50 (z-40)          │
│ ┌──────────────────────┐             │
│ │ SIDEBAR DRAWER       │             │
│ │ w-[min(18rem,85vw)]  │             │
│ │ z-50, h-screen       │             │
│ └──────────────────────┘             │
└──────────────────────────────────────┘
```

- La top bar és `h-14`, `sticky top-0 z-40`, amb fons blanc i vora inferior.
- El contingut ocupa tota l'amplada disponible i té `p-4`; afegir padding de
  safe area quan sigui necessari.
- El footer conserva el text:
  `"TT League · Projecte lliure per a la comunitat del tennis de taula ♥"`.
- El contingut principal ha de poder créixer verticalment i fer scroll sense
  desplaçar la pàgina sencera de manera inesperada.

---

## 3. SIDEBAR — Menú lateral en drawer mòbil

### Comportament

- En mòbil comença tancada i es mostra amb el botó `☰` de la top bar.
- És un drawer fix a l'esquerra, amb amplada
  `w-[min(18rem,85vw)]`, `h-screen`, `z-50`, `bg-slate-900 text-white`.
- Mentre està oberta es mostra un backdrop `bg-black/50` sota el drawer
  (`z-40`). Fer clic al backdrop tanca el menú.
- El botó de tancament és visible a la capçalera del drawer. També es tanca
  amb la tecla `Escape`, retorna el focus al botó que l'ha obert i no permet
  que el focus s'escapi del drawer mentre està obert.
- Bloquejar el scroll del document mentre el drawer està obert, sense
  bloquejar el scroll intern del menú si el seu contingut no cap.
- En canviar de ruta, tancar el drawer automàticament.
- La transició d'obertura i tancament és suau:
  `transition-transform duration-300 ease-in-out`; el backdrop utilitza una
  transició d'opacitat.
- A tablet es mostra una sidebar col·lapsada de `w-16`, només amb icones.
- A escriptori es mostra expandida amb `w-56` i permet col·lapsar-la a `w-16`
  amb `transition-all duration-300`.

### Estructura del drawer

```text
┌────────────────────────┐
│ 🏓 TT League       [×] │ ← logo, nom i tancament
├────────────────────────┤
│                        │
│ ◇ Overview        ◄────│── actiu: bg-indigo-600 rounded-lg
│ 🏛 Clubs search        │    mínim 44px d'alçada
│ 👤 Practitioners       │
│ 🏆 Matches search      │
│                        │
├─ ANÀLISI ──────────────┤ ← text-xs text-slate-400 uppercase
│ 📊 Analytics [PRÒX]    │ ← bg-indigo-500, text-xs, rounded
│ ⚙ Settings             │
├────────────────────────┤
│ BOTTOM (mt-auto)       │
│ ♥ Fet per amants       │ ← bg-slate-800 rounded-lg p-4 m-3
│   del tennis taula     │
│ Projecte lliure        │
│ per a tota la comunitat│
└────────────────────────┘
```

El footer del drawer pot ocupar diverses línies i ha de romandre llegible en
una amplada de pantalla petita.

### Elements del menú i rutes

| Icona (Lucide) | Label                | Ruta        | Grup    | Estat    |
|----------------|----------------------|-------------|---------|----------|
| `Home`         | Overview             | `/`         | —       | Actiu    |
| `Building2`    | Clubs search         | `/clubs`    | —       | Actiu    |
| `User`         | Practitioners search | `/jugadors` | —       | Actiu    |
| `Trophy`       | Matches search       | `/partits`  | —       | Actiu    |
| `BarChart3`    | Analytics            | `/analytics`| ANÀLISI | Disabled |
| `Settings`     | Settings             | `/settings` | ANÀLISI | Actiu    |

- Analytics és visible però deshabilitada, amb el badge `"Pròximament"`, i no
  ha de navegar.
- L'element actiu usa `bg-indigo-600 rounded-lg text-white font-medium` i
  `aria-current="page"`.
- Els elements inactius usen `text-slate-300 hover:bg-slate-800 rounded-lg`.
- La sidebar és un `nav` amb `aria-label="Menú principal"`.

---

## 4. TOP BAR — Barra superior mòbil

### Layout

```text
┌──────────────────────────────────────┐
│ [☰]  General › Overview   🔔  [TT] ▾ │
└──────────────────────────────────────┘
```

- Layout: `flex items-center gap-2 px-4`; el breadcrumb ocupa l'espai
  restant (`min-w-0 flex-1`).
- El botó de drawer és el primer element, amb àrea tàctil mínima de 44px.
- La top bar manté sempre accessibles notificacions i usuari, però pot reduir
  el breadcrumb a una sola línia amb ellipsis només quan no hi hagi espai.
- El nom d'usuari no s'ha d'amagar si això impedeix identificar el compte; en
  pantalles molt estretes es pot mostrar l'avatar i deixar el nom dins del
  dropdown.
- Fons: `bg-white border-b border-gray-200`; alçada mòbil `h-14`, tablet i
  escriptori `h-16`.

### Breadcrumb

- Generació automàtica a partir de la ruta actual (`useLocation()`).
- Format: `Bloc Principal › Sub-funcionalitat › Detall`.
- `nav aria-label="Breadcrumb"`.
- L'últim element és `text-gray-900 font-medium`; els anteriors són links
  `text-gray-500` i es poden activar amb teclat o tàctil.
- En mòbil, mostrar la ruta més específica que càpiga i conservar sempre
  l'últim element; els elements ocults visualment han de continuar sent
  comprensibles per a tecnologies d'assistència.
- Mapa de rutes:
  - `/` → `General › Overview`
  - `/clubs` → `General › Clubs search`
  - `/clubs/:id` → `Clubs › [Nom Club]`
  - `/jugadors` → `General › Practitioners search`
  - `/jugadors/:id` → `Jugadors › [Nom Jugador]`
  - `/partits` → `Resultats › Matches search`

### Bloc d'usuari

```text
┌─────────────────────────────┐
│ 🔔       [TT]  tttest1  ▾   │
│  │          │       │       │
│  │          │       └─ Dropdown:
│  │          │          ┌────────────┐
│  │          │          │ 👤 Perfil  │
│  │          │          │ ⚙ Preferènc.│
│  │          │          │ ────────── │
│  │          │          │ 🚪 Tancar  │
│  │          │          └────────────┘
│  │          └─ Avatar circular, bg-indigo-600
│  └─ Bell de Lucide
└─────────────────────────────┘
```

- La campana té una àrea tàctil de 44px. Si notificacions > 0, mostrar badge
  vermell absolut `w-2 h-2 bg-red-500 rounded-full top-0 right-0`.
- L'avatar és circular, `bg-indigo-600 text-white`, amb inicials.
- El dropdown s'obre amb clic o teclat, queda alineat al viewport i mai pot
  sortir per la dreta de la pantalla. Els seus controls tenen almenys 44px
  d'alçada.
- El dropdown inclou `Perfil`, `Preferències` i `Tancar`, amb
  `transition-opacity duration-150`, tancament amb `Escape` i clic fora.

---

## 5. PÀGINA: Overview (ruta `/`)

És la pantalla inicial per defecte. En mòbil usa
`flex flex-col gap-6 p-4`; els blocs ocupen `w-full` i no desborden el
viewport.

### 5.1 Bloc de Benvinguda

```text
┌──────────────────────────────────┐
│ bg-indigo-50/60                  │
│ border border-indigo-100         │
│ rounded-2xl p-5                  │
│                                  │
│ Benvingut a TT League!           │
│ text-2xl font-bold               │
│ "TT League" en indigo-600        │
│                                  │
│ ┌──────────────────────────────┐ │
│ │        🏓 IMG                │ │
│ │        pala TT               │ │
│ │        w-24 h-24             │ │
│ └──────────────────────────────┘ │
│                                  │
│ Projecte lliure creat per amants │
│ del tennis de taula.             │
│ Posem a l'abast de la comunitat  │
│ un buscador de resultats oficials │
│ de les darreres temporades.      │
│                                  │
│ ┌──────────────────────────────┐ │
│ │ ✨ En properes fases          │ │
│ │ incorporarem una capa        │ │
│ │ d'analítica i comparació     │ │
│ │ basada en IA utilitzant els  │ │
│ │ més actuals LLM disponibles. │ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```

- En mòbil, el text i la imatge s'apilen; la imatge pot usar `w-24 h-24`
  centrada per deixar espai al contingut textual.
- El bloc informatiu interior és `bg-white/70 border-indigo-200 rounded-lg
  p-4 text-sm text-gray-600` i ocupa l'amplada disponible.
- No retallar ni substituir el missatge de benvinguda ni l'avís sobre
  analítica futura.
- A `sm:` o superior es pot recuperar una composició en dues columnes si el
  text conserva una amplada llegible.

### 5.2 Cercador Global

```text
┌──────────────────────────────────┐
│ 🔍 Cerca clubs, jugadors o        │
│    partits...                     │
│                                  │
│ ┌──────────────────────────────┐ │
│ │ input w-full h-12 px-4       │ │
│ │ border-gray-300 rounded-xl   │ │
│ └──────────────────────────────┘ │
│ ┌──────────────────────────────┐ │
│ │          Cercar              │ │
│ │ bg-indigo-600 text-white    │ │
│ │ rounded-xl h-12 w-full      │ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```

- En mòbil, els controls són una columna: input a tota l'amplada i botó
  `w-full` a la línia següent. Entre tots dos hi ha `gap-3`.
- Input: `border border-gray-300 rounded-xl h-12 px-4 text-base`,
  `focus:ring-2 ring-indigo-500`, placeholder `text-gray-400`.
- El botó ha de ser fàcil de prémer amb una mà i tenir `focus-visible:ring-2`.
- Enter o clic a `"Cercar"` navega a `/cerca?q={query}`.
- Query mínima: 2 caràcters. Mostrar una validació clara i accessible si no
  s'arriba al mínim; no iniciar una cerca amb una query invàlida.
- Debounce de `300ms` per a suggeriments futurs.
- En `sm:` o superior es pot usar una fila amb input flexible i botó
  `w-auto px-8`, sense canviar el comportament.

### 5.3 Accés Ràpid

Títol: `"Accés ràpid"` (`text-lg font-bold text-gray-900 mb-4`).

```text
┌──────────────────────────────────┐
│ 🏛                               │
│ Cerca clubs                      │
│ Troba clubs, equips, categories  │
│ i resultats                      │
│                                  │
│ Explorar ›                       │
└──────────────────────────────────┘
┌──────────────────────────────────┐
│ 👤                               │
│ Cerca jugadors                   │
│ Consulta el rendiment dels       │
│ jugadors                         │
│                                  │
│ Explorar ›                       │
└──────────────────────────────────┘
┌──────────────────────────────────┐
│ 🏆                               │
│ Cerca partits                    │
│ Busca partits per data,          │
│ competició o jugador             │
│                                  │
│ Explorar ›                       │
└──────────────────────────────────┘
```

- Mòbil: `grid grid-cols-1 gap-4`; cada card té
  `rounded-xl p-5` i és prou gran per a una pulsació còmoda.
- Colors:
  - Clubs: `bg-orange-50 border-orange-100`, icona `bg-orange-100
    text-orange-600`.
  - Jugadors: `bg-emerald-50 border-emerald-100`, icona `bg-emerald-100
    text-emerald-600`.
  - Partits: `bg-amber-50 border-amber-100`, icona `bg-amber-100
    text-amber-600`.
- Cada card és un `<Link>` clicable sencer, amb `cursor-pointer`,
  `hover:shadow-md transition-shadow duration-200`; no fer que només
  `"Explorar"` sigui clicable.
- Destinacions:

| Card            | Navega a     |
|-----------------|--------------|
| Cerca clubs     | `/clubs`     |
| Cerca jugadors  | `/jugadors`  |
| Cerca partits   | `/partits`   |

- A `md:` es poden mostrar dues columnes i a `lg:` tres columnes.

### 5.4 Resum de la Comunitat

Títol: `"Resum de la comunitat"` (`text-lg font-bold text-gray-900 mb-4`).

```text
┌──────────────────────────────────┐
│ 👥  1.248                        │
│     Jugadors                     │
│     +86 aquesta temporada        │
└──────────────────────────────────┘
┌──────────────────────────────────┐
│ 🏛    186                        │
│       Clubs                      │
│       +9 aquesta temporada       │
└──────────────────────────────────┘
┌──────────────────────────────────┐
│ 🏆  8.432                        │
│     Partits                      │
│     +1.257 aquesta temporada     │
└──────────────────────────────────┘
┌──────────────────────────────────┐
│ 📈  24/25                        │
│     Temporada actual             │
│     En curs                      │
└──────────────────────────────────┘
```

- Mòbil: `grid grid-cols-1 gap-4`; cada card és `bg-white rounded-xl border
  border-gray-100 p-5`.
- Les icones són, respectivament, indigo, emerald, amber i purple.
- El número usa `text-3xl font-bold`; la descripció usa `text-sm
  text-gray-500`; increments usa `text-xs text-green-500`; l'estat de
  temporada usa `text-purple-600`.
- Les dades es consumeixen de `GET /api/stats/community`.
- A `sm:` es poden mostrar dues columnes i a `lg:` quatre, sempre que el text
  i els valors no quedin tallats.

### 5.5 Banner Promocional (Analytics)

```text
┌──────────────────────────────────┐
│ bg-gradient-to-b                 │
│ from-indigo-500 via-purple-500  │
│ to-purple-400                   │
│ rounded-2xl p-5 text-white       │
│                                  │
│ 🤖  El futur és a punt d'arribar │
│                                  │
│ Aviat podràs analitzar, comparar │
│ i descobrir patrons amb el suport│
│ de la Intel·ligència Artificial. │
│                                  │
│ ┌──────────────────────────────┐ │
│ │        Més informació        │ │
│ │ bg-white text-purple-600     │ │
│ │ rounded-lg w-full min-h-11   │ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```

- En mòbil el contingut s'apila i el botó ocupa tota l'amplada disponible,
  amb una àrea tàctil mínima de 44px.
- A `sm:` el botó pot recuperar una amplada automàtica i alinear-se al final.
- El banner és informatiu sobre una funcionalitat futura; no habilitar
  Analytics ni inventar-ne una pantalla funcional.

---

## 6. Paleta de colors (design tokens)

```text
Primari (accions, drawer actiu, links):
  indigo-600   #4F46E5
  indigo-700   #4338CA   (hover/focus)
  indigo-50    #EEF2FF   (fons blocs)

Secundari (accents per secció):
  orange-500   #F97316   (Clubs)
  emerald-500  #10B981   (Jugadors)
  amber-500    #F59E0B   (Partits)
  purple-500   #8B5CF6   (Analytics)

Neutres:
  slate-900    #0F172A   (drawer bg)
  slate-800    #1E293B   (drawer hover)
  gray-50      #F9FAFB   (main bg)
  gray-100     #F3F4F6   (vores de cards)
  gray-900     #111827   (text principal)
  gray-500     #6B7280   (text secundari)
  white        #FFFFFF   (cards, top bar)

Feedback:
  green-500    #22C55E   (increments positius)
  red-500      #EF4444   (errors, badge de notificació)
```

Mantenir contrast mínim WCAG AA, també en el drawer superposat i en els estats
de focus.

---

## 7. Tipografia

```text
Font stack:    font-sans → Inter, system-ui, sans-serif
               (importar Inter des de Google Fonts)

Escala mòbil:
  Títol pàgina:     text-2xl  font-bold     (24px)
  Títol secció:     text-lg   font-bold     (18px)
  Subtítol card:    text-base font-semibold (16px)
  Cos:              text-sm                  (14px)
  Caption/badge:    text-xs                  (12px)
  Stat number:      text-3xl  font-bold     (30px)
```

No fixar altures que tallin text traduït. Usar `break-words` o wrapping normal
quan calgui.

---

## 8. Estructura de components React

Conservar una estructura reutilitzable i comuna per a tots els viewports:

```text
src/
├── App.jsx                    ← Router principal
├── layouts/
│   └── DashboardLayout.jsx    ← Sidebar + TopBar + <Outlet/>
├── components/
│   ├── sidebar/
│   │   ├── Sidebar.jsx
│   │   ├── SidebarItem.jsx
│   │   └── SidebarFooter.jsx
│   ├── topbar/
│   │   ├── TopBar.jsx
│   │   ├── Breadcrumb.jsx
│   │   ├── NotificationBell.jsx
│   │   └── UserMenu.jsx
│   ├── overview/
│   │   ├── WelcomeBanner.jsx
│   │   ├── GlobalSearch.jsx
│   │   ├── QuickAccessGrid.jsx
│   │   ├── QuickAccessCard.jsx
│   │   ├── CommunityStats.jsx
│   │   ├── StatCard.jsx
│   │   └── AnalyticsBanner.jsx
│   └── ui/                    ← Components reutilitzables
│       ├── Badge.jsx
│       ├── Card.jsx
│       └── Button.jsx
├── pages/
│   ├── OverviewPage.jsx
│   ├── ClubsSearchPage.jsx
│   ├── PlayersSearchPage.jsx
│   ├── MatchesSearchPage.jsx
│   ├── SearchResultsPage.jsx
│   └── SettingsPage.jsx
├── hooks/
│   ├── useBreadcrumb.js
│   └── useSidebarCollapse.js
├── context/
│   └── AppContext.jsx
└── data/
    └── navigation.js          ← Configuració del menú sidebar
```

El component `DashboardLayout` ha de resoldre el drawer i el layout responsive,
no duplicar layouts diferents per mòbil i escriptori.

---

## 9. Patrons d'interacció i UX

### Navegació

- Sidebar + React Router: cada clic al menú carrega la ruta corresponent a
  `<Outlet/>`.
- En mòbil, obrir el drawer no canvia la ruta; el canvi de ruta el tanca.
- Transicions de pàgina: fade-in suau (`animate-fadeIn`, 200ms).
- El breadcrumb sempre reflecteix la ruta actual, amb links navegables als
  nivells superiors.
- El botó del navegador enrere ha de continuar funcionant amb normalitat.

### Formularis i feedback

- Tots els errors de validació es mostren al costat del control afectat i
  s'exposen amb `aria-describedby`; no usar només color.
- Els estats de càrrega i error de les dades de stats o cerca han de ser
  visibles i no poden provocar salts de layout excessius.
- Evitar obrir teclats o overlays de manera inesperada; els inputs han de tenir
  `font-size` d'almenys 16px per evitar zoom automàtic en iOS.

### Accessibilitat (a11y)

- Tots els elements interactius tenen `focus-visible:ring-2` i es poden operar
  amb teclat.
- El drawer usa `nav aria-label="Menú principal"`, `role="dialog"` quan
  correspongui, un nom accessible i gestió de focus.
- El backdrop no és l'única manera de tancar el drawer: oferir botó i Escape.
- El breadcrumb usa `nav aria-label="Breadcrumb"`.
- Les cards d'accés ràpid són links semàntics i poden usar rol `article` per al
  seu contingut visual, però no convertir un link en un botó.
- Afegir labels accessibles als botons que només mostren una icona.
- Respectar `prefers-reduced-motion` desactivant o reduint transicions no
  essencials.
- Contrast mínim WCAG AA en totes les combinacions de color.
- `aria-current="page"` a l'element actiu del menú.

### Animacions

- Drawer obrir/tancar:
  `transition-transform duration-300 ease-in-out`.
- Backdrop: transició d'opacitat.
- Cards: `hover:shadow-md transition-shadow duration-200` només com a reforç
  visual; l'estat no pot dependre de hover.
- Stats: animació del comptador de 0 al valor final opcional, amb `useEffect` +
  `requestAnimationFrame`.
- Dropdown d'usuari: `transition-opacity duration-150`.

---

## 10. API i dades (contracte simplificat)

La responsivitat no modifica cap contracte d'API ni cap funcionalitat de dades.

```text
GET /api/stats/community
Response:
{
  "jugadors":    { "total": 1248, "increment_temporada": 86 },
  "clubs":       { "total": 186, "increment_temporada": 9 },
  "partits":     { "total": 8432, "increment_temporada": 1257 },
  "temporada":   { "nom": "24/25", "estat": "En curs" }
}

GET /api/cerca?q={query}&tipus={clubs|jugadors|partits|tots}
Response:
{
  "resultats": [...],
  "total": 42,
  "pagina": 1,
  "per_pagina": 20
}
```

Les dades de stats han de provenir de l'API; es permeten dades mock inicials
per a desenvolupament, però no hardcodejar-les com a solució definitiva.

---

## 11. Regles d'implementació per a l'agent

1. **Un component per fitxer**. Màxim aproximadament 150 línies per component;
   si creix, extreure subcomponents.
2. **Tailwind pur**. No CSS custom excepte per animacions especials i els
   insets de safe area; utilitzar `@apply` amb moderació.
3. **Noms en anglès** per al codi (variables, funcions i components). Textos UI
   en català.
4. **React Router v6** amb `createBrowserRouter` i layout niuat.
5. **Estat mínim**: estat d'obertura/collapse del drawer al layout i dades de
   stats amb `fetch` + `useState`.
6. **Accessibilitat primer**: cada element interactiu ha de ser accessible amb
   teclat i tàctil.
7. **No hardcodejar dades de stats**: preparar hooks per al fetch de l'API,
   permetent dades mock inicials.
8. **Mobile-first breakpoints** de Tailwind: `sm:`, `md:`, `lg:`, `xl:`.
9. **Separar la configuració de navegació** a `data/navigation.js`.
10. **Lazy loading** de pàgines amb `React.lazy()` + `Suspense`.
11. **No duplicar funcionalitat per viewport**: utilitzar els mateixos
    components i contractes; adaptar només layout i presentació.
12. **No desactivar funcionalitats en mòbil**: totes les rutes i accions
    disponibles a escriptori també han de ser accessibles des del drawer o el
    contingut responsive.

---

## 12. Checklist de validació mòbil i responsive

- [ ] El viewport no produeix scroll horitzontal a 320px, 375px i 430px.
- [ ] El drawer comença tancat i s'obre amb el botó de la top bar.
- [ ] El drawer té backdrop, es tanca amb clic fora, botó de tancament i Escape.
- [ ] El focus es gestiona correctament en obrir i tancar el drawer.
- [ ] El scroll del document queda bloquejat mentre el drawer és obert.
- [ ] Cada opció del menú té una àrea tàctil mínima de 44px.
- [ ] El drawer indica visualment la ruta activa i usa `aria-current`.
- [ ] El breadcrumb reflecteix la ruta actual i els nivells superiors són links.
- [ ] El menú d'usuari es desplega sense sortir del viewport.
- [ ] La campana mostra el badge quan `n > 0`.
- [ ] El cercador global apila input i botó i navega amb query param.
- [ ] El cercador rebutja queries de menys de 2 caràcters amb feedback accessible.
- [ ] Les cards d'accés ràpid ocupen una columna i naveguen a la ruta correcta.
- [ ] Les estadístiques mostren dades mock o d'API sense tallar valors.
- [ ] El banner d'analytics manté el gradient i un botó tàctil a tota amplada.
- [ ] El footer conserva el text del projecte.
- [ ] A tablet i escriptori es recuperen progressivament les graelles i sidebar.
- [ ] El contingut respon a l'orientació vertical i horitzontal.
- [ ] Es respecten els safe areas en dispositius amb notch o barra de gestos.
- [ ] Hi ha contrast WCAG AA i focus visible en tots els elements interactius.
- [ ] `prefers-reduced-motion` redueix les animacions no essencials.
- [ ] Analytics continua visible com a funcionalitat deshabilitada,
  `"Pròximament"`, sense falsa funcionalitat.
