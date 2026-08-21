# TT League — Especificació UI per a Agent LLM

## Meta

| Camp              | Valor                                         |
|-------------------|-----------------------------------------------|
| Projecte          | TT League                                     |
| Tipus             | Aplicació web SPA                             |
| Stack             | React 18+ · Tailwind CSS 3+ · React Router 6+ |
| Idioma UI         | Català                                        |
| Responsive        | Desktop-first, adaptable a tablet i mòbil     |
| Icones            | Lucide React                                  |
| Estat             | React Context o Zustand                       |

---

## 1. Arquitectura de Layout Global

L'aplicació segueix un layout de 3 zones fixes: sidebar, top bar i àrea de contingut.

```
┌──────────────────────────────────────────────────────────────────────┐
│ VIEWPORT (100vw × 100vh)                                           │
│                                                                      │
│  ┌─────────┬────────────────────────────────────────────────────┐   │
│  │         │  TOP BAR  (h-16, sticky top)                      │   │
│  │         │  ┌─────────────────────┐    ┌──────────────────┐  │   │
│  │         │  │ ☰  Breadcrumb       │    │ 🔔  👤 username ▾│  │   │
│  │         │  └─────────────────────┘    └──────────────────┘  │   │
│  │ SIDEBAR ├────────────────────────────────────────────────────┤   │
│  │ (w-56)  │                                                    │   │
│  │         │  MAIN CONTENT AREA                                 │   │
│  │         │  (flex-1, overflow-y-auto, bg-gray-50)             │   │
│  │         │                                                    │   │
│  │         │  ┌────────────────────────────────────────────┐   │   │
│  │         │  │                                            │   │   │
│  │         │  │   Contingut dinàmic segons ruta activa     │   │   │
│  │         │  │                                            │   │   │
│  │         │  └────────────────────────────────────────────┘   │   │
│  │         │                                                    │   │
│  │         │  ┌────────────────────────────────────────────┐   │   │
│  │         │  │ FOOTER (text-center, text-sm, py-4)        │   │   │
│  │         │  │ "TT League · Projecte lliure per a la      │   │   │
│  │         │  │  comunitat del tennis de taula ♥"           │   │   │
│  │         │  └────────────────────────────────────────────┘   │   │
│  └─────────┴────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. SIDEBAR — Menú Lateral Esquerre

### Comportament

- Amplada expandida: `w-56` (224 px). Amplada col·lapsada: `w-16` (64 px, només icones).
- Transició suau: `transition-all duration-300`.
- Fons fosc: `bg-slate-900 text-white`.
- Sticky a tota l'alçada: `h-screen sticky top-0`.
- Botó de toggle (icona `☰` / `«`) situat a la capçalera del sidebar.
- En mòbil: overlay amb backdrop semitransparent, es tanca al fer clic fora.

### Estructura

```
┌────────────────────────┐
│  🏓  TT League    [«]  │  ← Logo + nom + toggle
├────────────────────────┤
│                        │
│  ◇  Overview      ◄────│── actiu: bg-indigo-600 rounded-lg
│  🏛  Clubs search      │     inactiu: hover:bg-slate-800
│  👤  Practitioners     │
│  🏆  Matches search    │
│                        │
├─ ANÀLISI ──────────────┤  ← Separador de secció (text-xs
│                        │     text-slate-400 uppercase)
│  📊  Analytics  [PRÒX] │  ← Badge "Pròximament"
│                        │     (bg-indigo-500 text-xs rounded)
│  ⚙️  Settings          │
│                        │
├────────────────────────┤
│ BOTTOM (mt-auto)       │
│ ┌────────────────────┐ │
│ │ ♥ Fet per amants   │ │  ← bg-slate-800 rounded-lg
│ │   del tennis taula │ │     p-4 m-3
│ │ Projecte lliure    │ │
│ │ per a tota la      │ │
│ │ comunitat          │ │
│ └────────────────────┘ │
└────────────────────────┘
```

### Elements del menú (definició de rutes)

| Icona (Lucide)    | Label               | Ruta               | Grup     | Estat    |
|-------------------|----------------------|--------------------|----------|----------|
| `Home`            | Overview             | `/`                | —        | Actiu    |
| `Building2`       | Clubs search         | `/clubs`           | —        | Actiu    |
| `User`            | Practitioners search | `/jugadors`        | —        | Actiu    |
| `Trophy`          | Matches search       | `/partits`         | —        | Actiu    |
| `BarChart3`       | Analytics            | `/analytics`       | ANÀLISI  | Disabled |
| `Settings`        | Settings             | `/settings`        | ANÀLISI  | Actiu    |

### Ítem actiu

- Fons: `bg-indigo-600` amb `rounded-lg`.
- Text: `text-white font-medium`.
- Resta: `text-slate-300 hover:bg-slate-800 rounded-lg`.

---

## 3. TOP BAR — Barra Superior

### Layout

```
┌──────────────────────────────────────────────────────────────┐
│  [☰]   General  ›  Overview                🔔   [TT] user ▾ │
│  │       └── Breadcrumb (auto)              │     │     │    │
│  │                                          │     │     └── Dropdown menú
│  └── toggle sidebar                         │     └── Avatar (inicials)
│                                             └── Notificacions (badge)
└──────────────────────────────────────────────────────────────┘
```

### Especificacions

- Alçada: `h-16`.
- Fons: `bg-white border-b border-gray-200`.
- Layout: `flex items-center justify-between px-6`.

### Breadcrumb (esquerra)

- Generació automàtica a partir de la ruta actual (`useLocation()`).
- Format: `Bloc Principal › Sub-funcionalitat › Detall`.
- Separador: `›` (`text-gray-400`).
- Últim element: `text-gray-900 font-medium`. Resta: `text-gray-500` amb link clicable.
- Mapa de rutes a labels:
    - `/` → `General › Overview`
    - `/clubs` → `General › Clubs search`
    - `/clubs/:id` → `Clubs › [Nom Club]`
    - `/jugadors` → `General › Practitioners search`
    - `/jugadors/:id` → `Jugadors › [Nom Jugador]`
    - `/partits` → `Resultats › Matches search`

### Bloc d'usuari (dreta)

```
┌──────────────────────────────┐
│  🔔         [TT]  tttest1 ▾  │
│   │           │       │      │
│   │           │       └─ Dropdown:
│   │           │          ┌──────────────┐
│   │           │          │ 👤 Perfil     │
│   │           │          │ ⚙️ Preferènc. │
│   │           │          │ ─────────── │
│   │           │          │ 🚪 Tancar    │
│   │           │          └──────────────┘
│   │           └─ Avatar circular (bg-indigo-600
│   │              text-white, inicials)
│   └─ Icona campana (Bell de Lucide)
│      Si notificacions > 0:
│        badge vermell absolut (w-2 h-2
│        bg-red-500 rounded-full top-0 right-0)
└──────────────────────────────┘
```

---

## 4. PÀGINA: Overview (ruta `/`)

Pantalla d'inici per defecte. Layout vertical (`flex flex-col gap-8 p-6`).

### 4.1 Bloc de Benvinguda

```
┌────────────────────────────────────────────────────────────────┐
│  bg-indigo-50/60  border border-indigo-100  rounded-2xl  p-8  │
│                                                                │
│  ┌─────────────────────────────────────┐  ┌──────────────┐    │
│  │ Benvingut a TT League!             │  │              │    │
│  │ (text-3xl font-bold)               │  │   🏓 IMG     │    │
│  │ "TT League" en color indigo-600    │  │  (pala TT)   │    │
│  │                                     │  │  w-32 h-32   │    │
│  │ Projecte lliure creat per amants   │  │  float-right │    │
│  │ del tennis de taula.               │  └──────────────┘    │
│  │ Posem a l'abast de la comunitat    │                       │
│  │ un buscador de resultats oficials  │                       │
│  │ de les darreres temporades.        │                       │
│  │                                     │                       │
│  │ ┌──────────────────────────────┐   │                       │
│  │ │ ✨ En properes fases incor- │   │                       │
│  │ │ porarem una capa d'analítica│   │                       │
│  │ │ i comparació basada en IA   │   │  ← bg-white/70        │
│  │ │ utilitzant els més actuals  │   │    border-indigo-200   │
│  │ │ LLM disponibles.           │   │    rounded-lg p-4      │
│  │ └──────────────────────────────┘   │    text-sm text-gray-600
│  └─────────────────────────────────────┘                       │
└────────────────────────────────────────────────────────────────┘
```

### 4.2 Cercador Global

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│   ┌──────────────────────────────────────────────┐ ┌────────┐ │
│   │ 🔍 Cerca clubs, jugadors o partits...        │ │ Cercar │ │
│   │                                              │ │        │ │
│   │  input: w-full (90%)                         │ │bg-indigo│ │
│   │  border border-gray-300 rounded-xl           │ │-600    │ │
│   │  h-12 px-4 text-base                         │ │text-whi│ │
│   │  focus:ring-2 ring-indigo-500                │ │rounded │ │
│   │  placeholder text-gray-400                   │ │-xl h-12│ │
│   └──────────────────────────────────────────────┘ └────────┘ │
│                                                                │
│   Layout: flex gap-4, input flex-1, botó w-auto px-8          │
│   Comportament:                                                │
│     - Enter o clic "Cercar" → navega a /cerca?q={query}       │
│     - Query mínima: 2 caràcters                                │
│     - Debounce: 300ms per a suggeriments futurs                │
└────────────────────────────────────────────────────────────────┘
```

### 4.3 Accés Ràpid

```
Títol: "Accés ràpid" (text-lg font-bold text-gray-900 mb-4)

┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ bg-orange-50      │  │ bg-emerald-50    │  │ bg-amber-50      │
│ border-orange-100 │  │ border-emerald-100│  │ border-amber-100 │
│ rounded-xl p-6    │  │ rounded-xl p-6   │  │ rounded-xl p-6   │
│                   │  │                  │  │                  │
│ [🏛] ← icon      │  │ [👤] ← icon     │  │ [🏆] ← icon     │
│  bg-orange-100    │  │  bg-emerald-100  │  │  bg-amber-100    │
│  rounded-lg p-2   │  │  rounded-lg p-2  │  │  rounded-lg p-2  │
│                   │  │                  │  │                  │
│ Cerca clubs       │  │ Cerca jugadors   │  │ Cerca partits    │
│ (font-semibold    │  │ (font-semibold   │  │ (font-semibold   │
│  text-orange-600) │  │  text-emerald-600│  │  text-amber-600) │
│                   │  │                  │  │                  │
│ Troba clubs,      │  │ Consulta el      │  │ Busca partits per│
│ equips, categories│  │ rendiment dels   │  │ data, competició │
│ i resultats       │  │ jugadors         │  │ o jugador        │
│ (text-sm          │  │ (text-sm         │  │ (text-sm         │
│  text-gray-500)   │  │  text-gray-500)  │  │  text-gray-500)  │
│                   │  │                  │  │                  │
│ Explorar ›        │  │ Explorar ›       │  │ Explorar ›       │
│ (text-orange-600  │  │ (text-emerald-600│  │ (text-amber-600  │
│  font-medium      │  │  font-medium     │  │  font-medium     │
│  hover:underline) │  │  hover:underline)│  │  hover:underline) │
└──────────────────┘  └──────────────────┘  └──────────────────┘

Layout: grid grid-cols-3 gap-6  (en mòbil: grid-cols-1)
Cada card és un <Link> clicable sencer (cursor-pointer, hover:shadow-md transition)
```

#### Destinacions dels accessos ràpids

| Card            | Navega a     |
|-----------------|--------------|
| Cerca clubs     | `/clubs`     |
| Cerca jugadors  | `/jugadors`  |
| Cerca partits   | `/partits`   |

### 4.4 Resum de la Comunitat

```
Títol: "Resum de la comunitat" (text-lg font-bold text-gray-900 mb-4)

┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ bg-white      │ │ bg-white      │ │ bg-white      │ │ bg-white      │
│ rounded-xl    │ │ rounded-xl    │ │ rounded-xl    │ │ rounded-xl    │
│ border        │ │ border        │ │ border        │ │ border        │
│ border-gray-  │ │ border-gray-  │ │ border-gray-  │ │ border-gray-  │
│ 100 p-6       │ │ 100 p-6       │ │ 100 p-6       │ │ 100 p-6       │
│               │ │               │ │               │ │               │
│ [👥]          │ │ [🏛]          │ │ [🏆]          │ │ [📈]          │
│ text-indigo   │ │ text-emerald  │ │ text-amber    │ │ text-purple   │
│               │ │               │ │               │ │               │
│  1.248        │ │   186         │ │  8.432        │ │  24/25        │
│ (text-3xl     │ │ (text-3xl     │ │ (text-3xl     │ │ (text-3xl     │
│  font-bold)   │ │  font-bold)   │ │  font-bold)   │ │  font-bold)   │
│               │ │               │ │               │ │               │
│ Jugadors      │ │ Clubs         │ │ Partits       │ │ Temporada     │
│ (text-sm      │ │ (text-sm      │ │ (text-sm      │ │ actual        │
│  text-gray-   │ │  text-gray-   │ │  text-gray-   │ │ (text-sm)     │
│  500)         │ │  500)         │ │  500)         │ │               │
│               │ │               │ │               │ │               │
│ +86 aquesta   │ │ +9 aquesta    │ │ +1.257 aquesta│ │ En curs       │
│ temporada     │ │ temporada     │ │ temporada     │ │ (text-purple  │
│ (text-xs      │ │ (text-xs      │ │ (text-xs      │ │  -600)        │
│  text-green-  │ │  text-green-  │ │  text-green-  │ │               │
│  500)         │ │  500)         │ │  500)         │ │               │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘

Layout: grid grid-cols-4 gap-6  (tablet: grid-cols-2, mòbil: grid-cols-1)
Dades: consumides des d'API (endpoint GET /api/stats/community)
```

### 4.5 Banner Promocional (Analytics)

```
┌────────────────────────────────────────────────────────────────┐
│  bg-gradient-to-r from-indigo-500 via-purple-500 to-purple-400│
│  rounded-2xl p-6 text-white                                   │
│                                                                │
│  ┌────┐                                                        │
│  │ 🤖 │  El futur és a punt d'arribar                         │
│  └────┘  Aviat podràs analitzar, comparar i descobrir         │
│          patrons amb el suport de la Intel·ligència            │
│          Artificial.                                           │
│                                              ┌──────────────┐ │
│                                              │Més informació│ │
│                                              │ bg-white     │ │
│                                              │ text-purple  │ │
│                                              │ rounded-lg   │ │
│                                              └──────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

---

## 5. Paleta de Colors (Design Tokens)

```
Primari (accions, sidebar actiu, links):
  indigo-600   #4F46E5
  indigo-700   #4338CA   (hover)
  indigo-50    #EEF2FF   (fons blocs)

Secundari (accents per secció):
  orange-500   #F97316   (Clubs)
  emerald-500  #10B981   (Jugadors)
  amber-500    #F59E0B   (Partits)
  purple-500   #8B5CF6   (Analytics)

Neutres:
  slate-900    #0F172A   (sidebar bg)
  slate-800    #1E293B   (sidebar hover)
  gray-50      #F9FAFB   (main bg)
  gray-100     #F3F4F6   (borders cards)
  gray-900     #111827   (text principal)
  gray-500     #6B7280   (text secundari)
  white        #FFFFFF   (cards, top bar)

Feedback:
  green-500    #22C55E   (increments positius)
  red-500      #EF4444   (errors, badge notif.)
```

---

## 6. Tipografia

```
Font stack:    font-sans → Inter, system-ui, sans-serif
              (importar Inter des de Google Fonts)

Escala:
  Títol pàgina:     text-3xl  font-bold     (30px)
  Títol secció:     text-lg   font-bold     (18px)
  Subtítol card:    text-base font-semibold (16px)
  Cos:              text-sm                  (14px)
  Caption/badge:    text-xs                  (12px)
  Stat number:      text-3xl  font-bold     (30px)
```

---

## 7. Estructura de Components React

```
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
    └── navigation.js          ← Config menú sidebar
```

---

## 8. Patrons d'Interacció i UX

### Navegació

- Sidebar + React Router: cada clic al menú carrega la ruta corresponent a `<Outlet/>`.
- Transicions de pàgina: fade-in suau (`animate-fadeIn`, 200ms).
- Breadcrumb sempre reflecteix la ruta actual, amb links navegables als nivells superiors.

### Responsivitat

```
Desktop (≥1024px):
┌──────────┬─────────────────────────────┐
│ Sidebar  │  TopBar                     │
│ w-56     │─────────────────────────────│
│ visible  │  Content (p-6)             │
│          │                             │
└──────────┴─────────────────────────────┘

Tablet (768-1023px):
┌────┬───────────────────────────────────┐
│ w-16│  TopBar                          │
│icons│──────────────────────────────────│
│only │  Content (p-4)                  │
│    │  grid-cols reduïdes              │
└────┴───────────────────────────────────┘

Mòbil (<768px):
┌───────────────────────────────────────┐
│  TopBar  [☰ toggle]                   │
│───────────────────────────────────────│
│  Content (p-4, grid-cols-1)           │
│                                       │
│  Sidebar: overlay amb backdrop        │
│  (z-50, bg-black/50 quan obert)       │
└───────────────────────────────────────┘
```

### Accessibilitat (a11y)

- Tots els elements interactius amb `focus-visible:ring-2`.
- Sidebar: `nav` amb `aria-label="Menú principal"`.
- Breadcrumb: `nav` amb `aria-label="Breadcrumb"`.
- Cards d'accés ràpid: rols `article` o links semàntics.
- Contrast mínim WCAG AA en totes les combinacions de color.
- `aria-current="page"` a l'element actiu del sidebar.

### Animacions

- Sidebar expand/collapse: `transition-all duration-300 ease-in-out`.
- Cards hover: `hover:shadow-md transition-shadow duration-200`.
- Stats counter: animació comptador de 0 al valor final (opcional, amb `useEffect` + requestAnimationFrame).
- Dropdown menú usuari: `transition-opacity duration-150`.

---

## 9. API i Dades (contracte simplificat)

```
GET /api/stats/community
Response:
{
  "jugadors":    { "total": 1248, "increment_temporada": 86 },
  "clubs":       { "total": 186,  "increment_temporada": 9 },
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

---

## 10. Regles d'Implementació per a l'Agent

1. **Un component per fitxer**. Màxim ~150 línies per component; si creix, extreure sub-components.
2. **Tailwind pur**. No CSS custom excepte per animacions especials. Utilitzar `@apply` amb moderació.
3. **Noms en anglès** per a codi (variables, funcions, components). Textos UI en català.
4. **React Router v6** amb `createBrowserRouter` i layout niuat.
5. **Estat mínim**: sidebar collapse (`useState` local al layout), dades de stats (fetch + `useState`).
6. **Accessibilitat primer**: cada element interactiu ha de ser accessible per teclat.
7. **No hardcodejar dades de stats**: preparar hooks per a fetch API, però permetre dades mock inicials.
8. **Mobile-first breakpoints** de Tailwind: `sm:`, `md:`, `lg:`, `xl:`.
9. **Separar configuració de navegació** en un fitxer `data/navigation.js` per facilitar manteniment.
10. **Lazy loading** de pàgines amb `React.lazy()` + `Suspense`.

---

## 11. Checklist de Validació

- [ ] Sidebar es col·lapsa i expandeix amb animació suau
- [ ] Sidebar indica visualment la ruta activa
- [ ] Breadcrumb reflecteix la ruta actual automàticament
- [ ] Breadcrumb nivells superiors són links navegables
- [ ] Menú d'usuari es desplega amb opcions
- [ ] Badge de notificacions visible quan n > 0
- [ ] Cercador global navega a resultats amb query param
- [ ] Cards d'accés ràpid naveguen a la ruta correcta
- [ ] Stats mostren dades (mock o API)
- [ ] Banner analytics amb gradient correcte
- [ ] Footer amb text del projecte
- [ ] Responsive: 3 breakpoints funcionals
- [ ] Contrast WCAG AA en tots els textos
- [ ] Focus visible en tots els elements interactius