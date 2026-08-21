# TT League — Especificació UI v3: Tema Clar Professional

## Meta

| Camp              | Valor                                              |
|-------------------|----------------------------------------------------|
| Projecte          | TT League                                          |
| Tipus             | Aplicació web SPA                                  |
| Stack             | React 18+ · Tailwind CSS 3+ · React Router 6+     |
| Idioma UI         | Català                                             |
| Responsive        | Desktop-first, adaptable a tablet i mòbil          |
| Icones            | Lucide React (stroke-width 1.5)                    |
| Estat             | React Context o Zustand                            |
| Font principal    | "Inter", system-ui, sans-serif                     |
| Font xifres       | "DM Mono", "JetBrains Mono", monospace             |
| Direcció estètica | Clar · Blaus suaus · Contrast fosc · Professional  |

---

## 0. Direcció de Disseny

### Filosofia

Un entorn lluminós, net i reposat que transmet confiança institucional.
Els blaus clars estableixen calma; els textos i accents foscos aporten
autoritat i llegibilitat. L'estètica s'inspira en dashboards de
federacions esportives i plataformes de dades públiques, no en apps
consumer ni en eines de productivitat.

### Principis visuals

- **Base lluminosa, contrast alt**: superfícies blaves molt clares (`slate-50`, `sky-50`) amb tipografia fosca (`slate-900`, `slate-800`).
- **Accent únic**: blau institucional profund (`#1E3A5F`) per a accions, sidebar actiu i elements d'autoritat.
- **Geometria continguda**: `rounded-lg` com a màxim. Sense arrodoniments excessius.
- **Jerarquia per pes tipogràfic**, no per color. Títols foscos, subtítols mitjans, captions suaus.
- **Separació per espai i línies fines**, no per ombres grans ni fons de colors variats.
- **Sobrietat cromàtica**: no més de 3 tons de blau simultanis en pantalla.

### Anti-patrons a evitar

- ❌ Fons pastel multicolor per secció (orange-50, emerald-50...).
- ❌ Icones dins cercles de color viu → massa informal.
- ❌ Gradients decoratius → frivolitat.
- ❌ Rounded-2xl o rounded-3xl → sensació d'app de consum.
- ❌ Ombres shadow-lg o shadow-xl → aspecto "flotant" exagerat.
- ❌ Il·lustracions, emoji o mascotes.
- ❌ Uppercase excessiu fora de labels de secció.
- ❌ Massa blanc pur (#FFFFFF) sense matís → fatiga visual. Preferir blaus clars.

---

## 1. Paleta de Colors (Design Tokens)

```
══════════════════════════════════════════════════
  SUPERFÍCIES
══════════════════════════════════════════════════
  --surface-app       #F0F4F8   (fons general, blau-gris molt clar)
  --surface-sidebar   #1E3A5F   (sidebar, blau fosc institucional)
  --surface-sidebar-h #264A73   (sidebar hover)
  --surface-sidebar-a #163350   (sidebar actiu, un punt més fosc)
  --surface-card      #FFFFFF   (cards principals)
  --surface-card-sub  #F7FAFC   (fons secundari dins cards)
  --surface-topbar    #FFFFFF   (top bar, blanc net)
  --surface-input     #F0F4F8   (fons inputs)
  --surface-input-foc #FFFFFF   (input amb focus)

══════════════════════════════════════════════════
  ACCENT PRINCIPAL: BLAU INSTITUCIONAL
══════════════════════════════════════════════════
  --accent-primary    #1E3A5F   (botons principals, links, sidebar)
  --accent-hover      #2B4D78   (hover botons)
  --accent-light      #E8EFF6   (fons subtil accent, seleccions)
  --accent-border     #1E3A5F/15  rgba(30,58,95,0.15)

══════════════════════════════════════════════════
  ACCENT SECUNDARI: BLAU CEL
══════════════════════════════════════════════════
  --secondary         #3B82C4   (links secundaris, elements interactius)
  --secondary-hover   #2D6DA8
  --secondary-light   #DBEAFE   (badges informatius)

══════════════════════════════════════════════════
  TEXT
══════════════════════════════════════════════════
  --text-primary      #0F1D2F   (text principal, quasi negre blavós)
  --text-secondary    #475569   (text secundari, slate-600)
  --text-muted        #94A3B8   (labels, captions)
  --text-on-dark      #F0F4F8   (text sobre sidebar fosc)
  --text-on-dark-sec  #A3BFDB   (text secundari sobre sidebar)

══════════════════════════════════════════════════
  BORDERS I SEPARADORS
══════════════════════════════════════════════════
  --border-default    #E2E8F0   (border principal, slate-200)
  --border-subtle     #F1F5F9   (border molt suau, slate-100)
  --border-strong     #CBD5E1   (border emfatitzat, slate-300)
  --border-accent     #1E3A5F/20

══════════════════════════════════════════════════
  FEEDBACK
══════════════════════════════════════════════════
  --color-success     #0D7C4A   (verd fosc, professional)
  --color-success-bg  #ECFDF5   (fons verd clar)
  --color-error       #B91C1C   (vermell fosc)
  --color-error-bg    #FEF2F2
  --color-warning     #B45309   (taronja fosc)
  --color-warning-bg  #FFFBEB
```

---

## 2. Tipografia

```
══════════════════════════════════════════════════
  FONT STACK
══════════════════════════════════════════════════
  Principal:  "Inter", system-ui, sans-serif
  Xifres:     "DM Mono", "JetBrains Mono", monospace
              (únicament per a estadístiques numèriques)

══════════════════════════════════════════════════
  ESCALA TIPOGRÀFICA
══════════════════════════════════════════════════
  Hero / Stat:      text-4xl   font-bold     font-mono    tracking-tight
                    color: text-primary

  Títol pàgina:     text-2xl   font-semibold
                    color: text-primary

  Títol secció:     text-xs    font-semibold  uppercase    tracking-[0.12em]
                    color: text-muted
                    (estil "label institucional", sempre amb
                     una línia horitzontal fina a la dreta:
                     flex items-center gap-3 + div flex-1 h-px bg-border)

  Subtítol card:    text-sm    font-semibold
                    color: text-primary

  Cos:              text-sm    font-normal    leading-relaxed
                    color: text-secondary

  Caption:          text-xs    font-medium
                    color: text-muted

  Badge:            text-[10px] font-semibold uppercase tracking-wider
```

### Títol de secció amb línia (component SectionLabel)

```
  ACCÉS RÀPID ──────────────────────────────────────────
  │            │
  │            └── div flex-1 h-px bg-[#E2E8F0]
  └── text-xs font-semibold uppercase tracking-[0.12em] text-[#94A3B8]

  Resultat visual: label discret a l'esquerra amb línia
  horitzontal que s'estén fins al final. Seriós, net, institucional.
```

---

## 3. Arquitectura de Layout Global

```
┌──────────────────────────────────────────────────────────────────────┐
│  VIEWPORT  ·  bg: #F0F4F8  ·  100vw × 100vh                       │
│                                                                      │
│  ┌──────────┬───────────────────────────────────────────────────┐    │
│  │          │  TOP BAR                                         │    │
│  │          │  bg: #FFFFFF  ·  h-14  ·  border-b #E2E8F0       │    │
│  │          │  shadow-sm (shadow-[0_1px_3px_rgba(0,0,0,0.04)]) │    │
│  │          │                                                   │    │
│  │          │  ┌──────────────────┐      ┌──────────────────┐  │    │
│  │          │  │ General › Overview│      │ 🔔  [AV] user ▾ │  │    │
│  │          │  └──────────────────┘      └──────────────────┘  │    │
│  │ SIDEBAR  ├───────────────────────────────────────────────────┤    │
│  │ w-60     │                                                   │    │
│  │          │  MAIN CONTENT                                     │    │
│  │ bg:      │  overflow-y-auto  ·  p-8                          │    │
│  │ #1E3A5F  │  max-w-[1360px]  ·  mx-auto                     │    │
│  │          │                                                   │    │
│  │ text:    │  ┌───────────────────────────────────────────┐    │    │
│  │ #F0F4F8  │  │  Contingut dinàmic (Outlet)               │    │    │
│  │          │  └───────────────────────────────────────────┘    │    │
│  │          │                                                   │    │
│  │          │  ┌───────────────────────────────────────────┐    │    │
│  │          │  │  FOOTER · text-xs · text-muted · py-6     │    │    │
│  │          │  │  TT League · Projecte lliure per a la     │    │    │
│  │          │  │  comunitat del tennis de taula             │    │    │
│  │          │  └───────────────────────────────────────────┘    │    │
│  └──────────┴───────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 4. SIDEBAR — Navegació Principal

### Estil general

- Fons: `#1E3A5F` (blau fosc institucional).
- Amplada: `w-60` expandit, `w-16` col·lapsat.
- `h-screen sticky top-0 flex flex-col`.
- Transició: `transition-all duration-300 ease-out`.
- Sense border dret (el contrast de color ja separa).

### Estructura

```
┌────────────────────────────────┐
│  bg: #1E3A5F                   │
│  text: #F0F4F8                 │
│                                │
│   TT LEAGUE              [«]  │  ← py-5 px-5
│   (text-base font-bold         │    "TT" → font-bold
│    tracking-tight)             │    "LEAGUE" → font-light
│   ─── (w-8 h-0.5 bg-white/20  │    Línia decorativa sota
│        mt-1)                   │
│                                │
├─ NAVEGACIÓ ────────────────────┤  ← text-[10px] uppercase
│  (mt-8)                        │    tracking-[0.15em]
│                                │    text-[#A3BFDB]/60
│  ┌──────────────────────────┐  │    px-5 pb-2
│  │  ▌ Overview              │  │
│  │  │                       │  │  ← ACTIU:
│  │  border-l-2 #FFFFFF      │  │    border-l-2 white
│  │  bg: rgba(255,255,255,   │  │    bg: white/10
│  │      0.10)               │  │    text: #FFFFFF
│  │  text: #FFFFFF           │  │    font-medium
│  │  font-medium             │  │
│  └──────────────────────────┘  │
│                                │
│     Cerca de clubs             │  ← INACTIU:
│                                │    text: #A3BFDB
│     Cerca de jugadors          │    hover: bg white/8
│                                │       text: #F0F4F8
│     Cerca de partits           │    py-2.5 px-5 mx-3
│                                │    rounded-md
│                                │    transition 150ms
├─ ANÀLISI ──────────────────────┤
│                                │
│     Analytics    ┌───────┐     │  ← Badge:
│                  │ AVIAT  │    │    bg: white/15
│                  └───────┘     │    text-[10px]
│                                │    text-[#A3BFDB]
│     Configuració               │    rounded px-2 py-0.5
│                                │
├────────────────────────────────┤
│  BOTTOM (mt-auto)              │
│  ┌──────────────────────────┐  │
│  │  bg: white/8             │  │  ← rounded-lg p-4 m-3
│  │  border: white/10        │  │
│  │                          │  │
│  │  Projecte obert per a    │  │  ← text-xs text-[#A3BFDB]
│  │  la comunitat del        │  │    leading-relaxed
│  │  tennis de taula.        │  │
│  │                          │  │
│  │  v2.1.0                  │  │  ← text-[10px] text-white/30
│  └──────────────────────────┘  │
│                                │
└────────────────────────────────┘
```

### Taula de navegació

| Icona (Lucide)    | Label              | Ruta          | Grup       | Estat    |
|-------------------|--------------------|---------------|------------|----------|
| `LayoutDashboard` | Overview           | `/`           | NAVEGACIÓ  | Actiu    |
| `Building2`       | Cerca de clubs     | `/clubs`      | NAVEGACIÓ  | Normal   |
| `Users`           | Cerca de jugadors  | `/jugadors`   | NAVEGACIÓ  | Normal   |
| `Swords`          | Cerca de partits   | `/partits`    | NAVEGACIÓ  | Normal   |
| `BarChart3`       | Analytics          | `/analytics`  | ANÀLISI   | Disabled |
| `Settings`        | Configuració       | `/settings`   | ANÀLISI   | Normal   |

### Regles d'estat

```
Actiu:
  border-l-2 border-white
  bg-white/10
  text-white  font-medium

Inactiu:
  text-[#A3BFDB]
  hover:text-white
  hover:bg-white/[0.08]
  transition-colors duration-150

Disabled:
  text-[#A3BFDB]/40
  pointer-events-none
```

---

## 5. TOP BAR — Barra Superior

```
┌──────────────────────────────────────────────────────────────────┐
│  bg: #FFFFFF · h-14 · border-b: #E2E8F0 · px-6                 │
│  shadow-[0_1px_3px_rgba(0,0,0,0.04)]                            │
│  flex items-center justify-between                               │
│                                                                  │
│  ┌─ ESQUERRA ────────────────────┐  ┌─ DRETA ───────────────┐   │
│  │                               │  │                       │   │
│  │  [☰]  General › Overview      │  │  🔔     [AV] user ▾   │   │
│  │   │    │         │            │  │   │       │     │     │   │
│  │   │    │         └ text-primary│  │   │       │     └ dropdown│
│  │   │    │      font-medium     │  │   │       │              │   │
│  │   │    └ link text-muted      │  │   │       └ Avatar:     │   │
│  │   │      hover:text-secondary │  │   │         w-8 h-8     │   │
│  │   │                           │  │   │         rounded-md  │   │
│  │   └ toggle sidebar            │  │   │         bg-[#1E3A5F]│   │
│  │     text-muted                │  │   │         text-white  │   │
│  │     hover:text-primary        │  │   │         text-xs     │   │
│  │                               │  │   │         font-semibold│  │
│  └───────────────────────────────┘  │   │                     │   │
│                                      │   └ Bell icon           │   │
│  Breadcrumb separador: ›            │     text-[#475569]      │   │
│  (text-muted/40, mx-2)              │     hover:text-primary  │   │
│                                      │     Si n>0: badge       │   │
│                                      │       w-1.5 h-1.5      │   │
│                                      │       bg-[#B91C1C]     │   │
│                                      │       rounded-full     │   │
│                                      │       absolute         │   │
│                                      └───────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### Dropdown d'usuari

```
┌───────────────────────────┐
│  bg: #FFFFFF              │
│  border: #E2E8F0          │
│  rounded-lg               │
│  shadow-lg shadow-black/8 │
│  min-w-[200px]            │
│  mt-2 py-1                │
│                           │
│  ┌───────────────────┐   │
│  │  tttest1           │   │  ← text-sm font-medium text-primary
│  │  user@email.com    │   │    text-xs text-muted
│  └───────────────────┘   │    px-4 py-3 border-b border-[#E2E8F0]
│                           │
│    El meu perfil          │  ← px-4 py-2 text-sm text-secondary
│    Preferències           │    hover:bg-[#F0F4F8]
│                           │    hover:text-primary
│  ┌───────────────────┐   │
│  │ border-t #E2E8F0  │   │
│  └───────────────────┘   │
│    Tancar sessió          │  ← text-[#B91C1C]
│                           │    hover:bg-[#FEF2F2]
└───────────────────────────┘
```

---

## 6. PÀGINA: Overview (ruta `/`)

Layout: `flex flex-col gap-10 p-8 max-w-[1360px] mx-auto`

---

### 6.1 Capçalera Hero — Bloc de Benvinguda

```
┌────────────────────────────────────────────────────────────────────┐
│  bg: #FFFFFF  ·  border: #E2E8F0  ·  rounded-lg  ·  relative     │
│  overflow: hidden                                                  │
│                                                                    │
│  ┌── CONTINGUT (p-8, relative z-10) ──────────────────────────┐   │
│  │                                                             │   │
│  │   Benvingut a                                              │   │
│  │   TT League                                                │   │
│  │   ═══════ (w-12 h-1 bg-[#1E3A5F] mt-3 mb-5)              │   │
│  │                                                             │   │
│  │   Plataforma oberta de dades per a la comunitat            │   │
│  │   del tennis de taula. Consulta resultats oficials,        │   │
│  │   estadístiques de jugadors i classificacions              │   │
│  │   de les darreres temporades.                              │   │
│  │                                                             │   │
│  │   ┌───────────────────────────────────────────────────┐    │   │
│  │   │                                                   │    │   │
│  │   │  En properes fases incorporarem una capa          │    │   │
│  │   │  d'analítica i comparació basada en IA,           │    │   │
│  │   │  utilitzant els més actuals LLM disponibles.      │    │   │
│  │   │                                                   │    │   │
│  │   │  bg: #F0F4F8                                      │    │   │
│  │   │  border-l-2 border-[#1E3A5F]/30                   │    │   │
│  │   │  rounded-r-md  p-4  text-sm text-secondary       │    │   │
│  │   └───────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                    │
│  ┌── DECORACIÓ (absolute, right, top, z-0) ─────────────────┐    │
│  │                                                           │    │
│  │   Formes geomètriques abstractes molt subtils:           │    │
│  │   - Cercle gran (#1E3A5F/[0.03]) a la cantonada          │    │
│  │     superior dreta, w-64 h-64, -top-20 -right-20         │    │
│  │   - Cercle petit (#1E3A5F/[0.05]) w-32 h-32             │    │
│  │     bottom-8 right-16                                     │    │
│  │   Propòsit: trencar la monotonia del blanc                │    │
│  │   sense afegir il·lustracions figuratives.                │    │
│  │                                                           │    │
│  └───────────────────────────────────────────────────────────┘    │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘

"Benvingut a":
  text-sm  font-medium  text-muted  uppercase  tracking-[0.1em]

"TT League":
  text-2xl  font-bold  text-primary  tracking-tight

Línia accent sota títol:
  div  w-12  h-1  bg-[#1E3A5F]  rounded-full  mt-3  mb-5

Paràgraf:
  text-sm  text-secondary  leading-relaxed  max-w-lg
```

---

### 6.2 Cercador Global

```
  CERCA GLOBAL ─────────────────────────────────────────────
  (SectionLabel component)

  ┌──────────────────────────────────────────────────┐ ┌──────────┐
  │  🔍  Cerca clubs, jugadors o partits...          │ │  Cercar  │
  │                                                  │ │          │
  │  bg: #FFFFFF                                     │ │ bg:      │
  │  border: #E2E8F0                                 │ │ #1E3A5F  │
  │  rounded-lg                                      │ │ text:    │
  │  h-12                                            │ │ #FFFFFF  │
  │  px-4 pl-11 (espai per icona)                    │ │ font-med │
  │  text-sm text-primary                            │ │ rounded  │
  │  placeholder: text-muted                         │ │ -lg      │
  │  focus: border-[#1E3A5F]                         │ │ h-12     │
  │  focus: ring-2 ring-[#1E3A5F]/10                 │ │ px-8     │
  │  transition-colors                               │ │ hover:   │
  │                                                  │ │ bg-accent│
  │  flex-1                                          │ │ -hover   │
  └──────────────────────────────────────────────────┘ │ trans.   │
                                                       └──────────┘
  Layout: flex gap-3
  Comportament:
    Enter o clic → navega a /cerca?q={query}
    Min 2 chars · Debounce 300ms

  Icona lupa: absolute left-4, text-muted, w-4 h-4
```

---

### 6.3 Accés Ràpid

```
  ACCÉS RÀPID ──────────────────────────────────────────────
  (SectionLabel)

  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
  │ bg: #FFFFFF           │  │ bg: #FFFFFF           │  │ bg: #FFFFFF           │
  │ border: #E2E8F0       │  │ border: #E2E8F0       │  │ border: #E2E8F0       │
  │ rounded-lg            │  │ rounded-lg            │  │ rounded-lg            │
  │ p-6                   │  │ p-6                   │  │ p-6                   │
  │ group                 │  │ group                 │  │ group                 │
  │ hover:border-[#1E3A5F]│  │ hover:border-[#1E3A5F]│  │ hover:border-[#1E3A5F]│
  │ /30                   │  │ /30                   │  │ /30                   │
  │ hover:shadow-sm       │  │ hover:shadow-sm       │  │ hover:shadow-sm       │
  │ transition-all 200ms  │  │ transition-all 200ms  │  │ transition-all 200ms  │
  │ cursor-pointer        │  │ cursor-pointer        │  │ cursor-pointer        │
  │                       │  │                       │  │                       │
  │ ┌──┐                  │  │ ┌──┐                  │  │ ┌──┐                  │
  │ │🏛│                  │  │ │👤│                  │  │ │⚔│                   │
  │ └──┘                  │  │ └──┘                  │  │ └──┘                  │
  │  w-10 h-10            │  │  w-10 h-10            │  │  w-10 h-10            │
  │  bg-[#F0F4F8]         │  │  bg-[#F0F4F8]         │  │  bg-[#F0F4F8]         │
  │  text-[#1E3A5F]       │  │  text-[#1E3A5F]       │  │  text-[#1E3A5F]       │
  │  rounded-md           │  │  rounded-md           │  │  rounded-md           │
  │  flex items-center    │  │  flex items-center    │  │  flex items-center    │
  │  justify-center       │  │  justify-center       │  │  justify-center       │
  │                       │  │                       │  │                       │
  │ Cerca de clubs        │  │ Cerca de jugadors     │  │ Cerca de partits      │
  │ (text-sm font-semi    │  │ (text-sm font-semi    │  │ (text-sm font-semi    │
  │  text-primary mt-4)   │  │  text-primary mt-4)   │  │  text-primary mt-4)   │
  │                       │  │                       │  │                       │
  │ Troba clubs, equips   │  │ Consulta rendiment    │  │ Busca partits per     │
  │ i resultats per       │  │ i estadístiques       │  │ data, competició      │
  │ categoria.            │  │ dels jugadors.        │  │ o jugador.            │
  │ (text-xs text-muted   │  │ (text-xs text-muted   │  │ (text-xs text-muted   │
  │  mt-1.5               │  │  mt-1.5               │  │  mt-1.5               │
  │  leading-relaxed)     │  │  leading-relaxed)     │  │  leading-relaxed)     │
  │                       │  │                       │  │                       │
  │ ───────────────────   │  │ ───────────────────   │  │ ───────────────────   │
  │ border-t #E2E8F0      │  │ border-t #E2E8F0      │  │ border-t #E2E8F0      │
  │ mt-auto pt-4          │  │ mt-auto pt-4          │  │ mt-auto pt-4          │
  │                       │  │                       │  │                       │
  │ Explorar →            │  │ Explorar →            │  │ Explorar →            │
  │ text-[#1E3A5F]        │  │ text-[#1E3A5F]        │  │ text-[#1E3A5F]        │
  │ text-xs font-semibold │  │ text-xs font-semibold │  │ text-xs font-semibold │
  │ group-hover:          │  │ group-hover:          │  │ group-hover:          │
  │  translate-x-0.5      │  │  translate-x-0.5      │  │  translate-x-0.5      │
  └──────────────────────┘  └──────────────────────┘  └──────────────────────┘

  Layout: grid grid-cols-3 gap-5
  Tablet: grid-cols-2
  Mòbil:  grid-cols-1

  Destinacions:
  ┌──────────────────┬───────────┐
  │ Card             │ Ruta      │
  ├──────────────────┼───────────┤
  │ Cerca de clubs   │ /clubs    │
  │ Cerca de jugadors│ /jugadors │
  │ Cerca de partits │ /partits  │
  └──────────────────┴───────────┘

  Nota: les cards són flex flex-col h-full per alinear
  el separador i "Explorar" al fons de cada card.
  Icones Lucide: Building2, Users, Swords (stroke-width 1.5).
```

---

### 6.4 Resum de la Comunitat

```
  ESTADÍSTIQUES ────────────────────────────────────────────
  (SectionLabel)

  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
  │ bg: #FFFFFF      │ │ bg: #FFFFFF      │ │ bg: #FFFFFF      │ │ bg: #FFFFFF      │
  │ border: #E2E8F0  │ │ border: #E2E8F0  │ │ border: #E2E8F0  │ │ border: #E2E8F0  │
  │ rounded-lg       │ │ rounded-lg       │ │ rounded-lg       │ │ rounded-lg       │
  │ p-6              │ │ p-6              │ │ p-6              │ │ p-6              │
  │ border-t-2       │ │ border-t-2       │ │ border-t-2       │ │ border-t-2       │
  │ border-t-[#1E3A5F]│ │ border-t-[#1E3A5F]│ │ border-t-[#1E3A5F]│ │ border-t-[#1E3A5F]│
  │ /20              │ │ /20              │ │ /20              │ │ /20              │
  │                  │ │                  │ │                  │ │                  │
  │ JUGADORS         │ │ CLUBS            │ │ PARTITS          │ │ TEMPORADA        │
  │ (text-[10px]     │ │ (text-[10px]     │ │ (text-[10px]     │ │ (text-[10px]     │
  │  uppercase       │ │  uppercase       │ │  uppercase       │ │  uppercase       │
  │  tracking-wider  │ │  tracking-wider  │ │  tracking-wider  │ │  tracking-wider  │
  │  text-muted      │ │  text-muted      │ │  text-muted      │ │  text-muted      │
  │  mb-1)           │ │  mb-1)           │ │  mb-1)           │ │  mb-1)           │
  │                  │ │                  │ │                  │ │                  │
  │   1.248          │ │     186          │ │   8.432          │ │   24/25          │
  │ (font-mono       │ │ (font-mono       │ │ (font-mono       │ │ (font-mono       │
  │  text-3xl        │ │  text-3xl        │ │  text-3xl        │ │  text-3xl        │
  │  font-bold       │ │  font-bold       │ │  font-bold       │ │  font-bold       │
  │  text-primary    │ │  text-primary    │ │  text-primary    │ │  text-primary    │
  │  tracking-tight) │ │  tracking-tight) │ │  tracking-tight) │ │  tracking-tight) │
  │                  │ │                  │ │                  │ │                  │
  │ ▲ +86            │ │ ▲ +9             │ │ ▲ +1.257         │ │                  │
  │ aquesta temp.    │ │ aquesta temp.    │ │ aquesta temp.    │ │  En curs         │
  │ (flex items-     │ │ (flex items-     │ │ (flex items-     │ │ (text-xs         │
  │  center gap-1    │ │  center gap-1    │ │  center gap-1    │ │  font-medium     │
  │  text-xs         │ │  text-xs         │ │  text-xs         │ │  text-[#0D7C4A]  │
  │  text-[#0D7C4A]  │ │  text-[#0D7C4A]  │ │  text-[#0D7C4A]  │ │  flex items-ctr  │
  │  font-mono       │ │  font-mono       │ │  font-mono       │ │  gap-2)          │
  │  mt-3)           │ │  mt-3)           │ │  mt-3)           │ │                  │
  │                  │ │                  │ │                  │ │ ● punt verd      │
  │ "aquesta temp."  │ │ "aquesta temp."  │ │ "aquesta temp."  │ │  w-1.5 h-1.5     │
  │ text-[10px]      │ │ text-[10px]      │ │ text-[10px]      │ │  bg-[#0D7C4A]    │
  │ text-muted       │ │ text-muted       │ │ text-muted       │ │  rounded-full    │
  │ ml-0.5           │ │ ml-0.5           │ │ ml-0.5           │ │  animate-pulse   │
  └─────────────────┘ └─────────────────┘ └─────────────────┘ └─────────────────┘

  Layout: grid grid-cols-4 gap-5
  Tablet: grid-cols-2
  Mòbil:  grid-cols-2 (compacte 2x2)

  Detall clau: border-t-2 amb accent/20 a la part superior
               de cada card (línia decorativa institucional).

  Xifres: font-mono. Animació countUp 800ms easeOutExpo.
  Trigger: IntersectionObserver threshold 0.3.
  prefers-reduced-motion: valor immediat sense animació.
```

---

### 6.5 Banner Analítica

```
┌────────────────────────────────────────────────────────────────────┐
│  bg: #1E3A5F  ·  rounded-lg  ·  p-6                              │
│  flex items-center justify-between                                │
│                                                                    │
│  ┌─ CONTINGUT ────────────────────────────┐  ┌─────────────────┐  │
│  │                                        │  │                 │  │
│  │  ANALÍTICA AVANÇADA                    │  │  MÉS INFORMACIÓ │  │
│  │  (text-sm font-semibold text-white     │  │                 │  │
│  │   tracking-wide)                       │  │  bg: #FFFFFF    │  │
│  │                                        │  │  text-[#1E3A5F] │  │
│  │  Aviat podràs analitzar, comparar      │  │  font-medium    │  │
│  │  i descobrir patrons amb el suport     │  │  text-sm        │  │
│  │  de la Intel·ligència Artificial.      │  │  rounded-md     │  │
│  │  (text-sm text-[#A3BFDB] mt-1)        │  │  px-5 py-2.5    │  │
│  │                                        │  │  hover:bg-white │  │
│  └────────────────────────────────────────┘  │  /90            │  │
│                                               │  transition     │  │
│                                               └─────────────────┘  │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘

  Nota: el banner utilitza el color de sidebar (#1E3A5F) per crear
  un bloc visual contundent dins la pàgina clara, sense gradients.
  Botó blanc sòlid (no outline) per màxim contrast i CTA clar.
```

---

## 7. Estructura de Components React

```
src/
├── App.jsx
├── index.css                       ← @import fonts, CSS vars
│
├── layouts/
│   └── DashboardLayout.jsx         ← Sidebar + TopBar + <Outlet/>
│
├── components/
│   ├── sidebar/
│   │   ├── Sidebar.jsx
│   │   ├── SidebarItem.jsx
│   │   ├── SidebarSectionLabel.jsx
│   │   └── SidebarFooter.jsx
│   │
│   ├── topbar/
│   │   ├── TopBar.jsx
│   │   ├── Breadcrumb.jsx
│   │   ├── NotificationBell.jsx
│   │   └── UserDropdown.jsx
│   │
│   ├── overview/
│   │   ├── HeroBanner.jsx
│   │   ├── GlobalSearch.jsx
│   │   ├── QuickAccessGrid.jsx
│   │   ├── QuickAccessCard.jsx
│   │   ├── CommunityStats.jsx
│   │   ├── StatCard.jsx
│   │   └── AnalyticsBanner.jsx
│   │
│   └── ui/
│       ├── SectionLabel.jsx        ← label + línia horitzontal
│       ├── AccentBar.jsx           ← línia decorativa
│       └── Badge.jsx
│
├── pages/
│   ├── OverviewPage.jsx
│   ├── ClubsSearchPage.jsx
│   ├── PlayersSearchPage.jsx
│   ├── MatchesSearchPage.jsx
│   ├── SearchResultsPage.jsx
│   └── SettingsPage.jsx
│
├── hooks/
│   ├── useBreadcrumb.js
│   ├── useSidebarCollapse.js
│   └── useCountUp.js
│
├── config/
│   ├── navigation.js
│   └── routes.js
│
└── styles/
    └── theme.js                    ← design tokens
```

---

## 8. Responsivitat

```
══════════════════════════════════════════════════
  DESKTOP  ≥1280px
══════════════════════════════════════════════════
┌──────────┬─────────────────────────────────────┐
│ Sidebar  │  TopBar h-14                        │
│ w-60     ├─────────────────────────────────────│
│ bg fosc  │  Content p-8 max-w-[1360px] mx-auto │
│ fix      │  grid-cols-4 (stats)                │
│          │  grid-cols-3 (accés ràpid)          │
└──────────┴─────────────────────────────────────┘

══════════════════════════════════════════════════
  TABLET  768px — 1279px
══════════════════════════════════════════════════
┌────┬───────────────────────────────────────────┐
│w-16│  TopBar                                   │
│    ├───────────────────────────────────────────│
│icon│  Content p-6                              │
│only│  grid-cols-2 (stats i accés ràpid)        │
└────┴───────────────────────────────────────────┘

══════════════════════════════════════════════════
  MÒBIL  <768px
══════════════════════════════════════════════════
┌───────────────────────────────────────────────┐
│  TopBar  [☰]                                  │
├───────────────────────────────────────────────│
│  Content p-4                                  │
│  grid-cols-1 (accés ràpid)                    │
│  grid-cols-2 (stats, 2x2 compacte)           │
│                                               │
│  Sidebar: drawer overlay                      │
│  backdrop: bg-black/40                        │
│  slide-in esquerra                            │
│  transition: transform 300ms ease-out         │
└───────────────────────────────────────────────┘
```

---

## 9. Accessibilitat

- Sidebar: `<nav aria-label="Navegació principal">`.
- Breadcrumb: `<nav aria-label="Fil d'Ariadna">` amb `<ol>`.
- Ítem actiu: `aria-current="page"`.
- Focus ring: `focus-visible:ring-2 focus-visible:ring-[#1E3A5F]/40 focus-visible:ring-offset-2`.
- Contrast verificat: `#475569` sobre `#FFFFFF` = 7.1:1 ✓ (AA). `#A3BFDB` sobre `#1E3A5F` = 5.4:1 ✓.
- Dropdown: focus trap, `Escape` tanca, `aria-expanded`, `role="menu"`.
- `prefers-reduced-motion`: desactiva countUp i transicions.
- Skip-to-content link ocult fins a focus.

---

## 10. Animacions

```
Cards hover:
  border-color: #E2E8F0 → rgba(30,58,95,0.3)
  shadow: none → shadow-sm
  transition-all 200ms ease

Sidebar item hover:
  bg: transparent → white/8
  transition-colors 150ms

Sidebar expand/collapse:
  width: transition-all 300ms ease-out

Botó "Cercar" hover:
  bg: #1E3A5F → #2B4D78
  transition-colors 150ms

Stats countUp:
  800ms easeOutExpo
  IntersectionObserver (threshold 0.3)
  prefers-reduced-motion: immediat

Fletxa "Explorar →":
  group-hover:translate-x-0.5
  transition-transform 200ms

Dropdown:
  opacity + scale: 0/95% → 100%/100%
  transition 150ms ease-out
```

---

## 11. API

```
GET /api/stats/community
{
  "jugadors":  { "total": 1248, "delta_temporada": 86 },
  "clubs":     { "total": 186,  "delta_temporada": 9 },
  "partits":   { "total": 8432, "delta_temporada": 1257 },
  "temporada": { "nom": "24/25", "estat": "en_curs" }
}

GET /api/cerca?q={query}&tipus={clubs|jugadors|partits|tots}
{
  "resultats": [...],
  "total": 42,
  "pagina": 1,
  "per_pagina": 20
}
```

---

## 12. Regles d'Implementació

1. **Tema clar únic**. Fons `#F0F4F8`, cards `#FFFFFF`. Sidebar és l'únic element fosc.
2. **Un sol accent**: `#1E3A5F`. Totes les accions, hovers, borders d'èmfasi usen aquest blau.
3. **Font mono exclusivament per xifres estadístiques**. Mai per text normal.
4. **SectionLabel amb línia**: cada secció utilitza el component reutilitzable amb línia horitzontal.
5. **Ombres mínimes**: `shadow-sm` com a màxim, i només en hover. Mai `shadow-lg` decoratiu.
6. **Border-t-2 accent/20** a les stat cards com a únic element decoratiu.
7. **CSS variables** al `:root` per a tots els tokens.
8. **Max 150 línies per component**.
9. **Lazy loading** de pàgines.
10. **Hook `useCommunityStats()`** amb suport mock i fetch.

---

## 13. Checklist de Validació

- [ ] Fons general #F0F4F8, cards #FFFFFF — cap element pastel multicolor
- [ ] Sidebar #1E3A5F amb border-l blanc a ítem actiu
- [ ] Sidebar col·lapsable amb transició suau
- [ ] SectionLabel amb línia horitzontal en cada secció
- [ ] Breadcrumb automàtic des de la ruta
- [ ] Dropdown d'usuari amb border i shadow subtil
- [ ] Cercador amb focus ring en accent
- [ ] Cards accés ràpid: hover border accent + shadow-sm
- [ ] Stats amb font mono, border-t accent, countUp
- [ ] Banner analítica #1E3A5F amb botó blanc
- [ ] Responsive 3 breakpoints funcionals
- [ ] Focus visible en tots els interactius
- [ ] Contrast WCAG AA verificat
- [ ] prefers-reduced-motion respectat
- [ ] Footer text del projecte