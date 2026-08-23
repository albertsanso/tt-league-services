# UI Mockup: Club Identity Page

## Page Header

| Element             | Type            | Details                                      |
|---------------------|-----------------|----------------------------------------------|
| Section label       | Text (small)    | `IDENTITAT DEL CLUB`                         |
| Club name           | Heading (H1)    | `CLUB TENNIS TAULA ELS AMICS TERRASSA`       |
| Data source         | Text (caption)  | `Font: RFETM`                                |
| Edit button         | Button (outline) | `✎ Edita el club` — top-right corner         |

---

## Navigation & Filters

### Tab Toggle

| Tab         | Icon | State    |
|-------------|------|----------|
| Jugadors    | 👥   | Inactive |
| Partits     | ⚔    | Active   |

### Dropdown Selectors

| Selector    | Purpose                          |
|-------------|----------------------------------|
| Season      | Filter by season (e.g. 2023-2024)|
| Competition | Filter by competition name       |

---

## Section: Competicions

List of competition cards. Each card displays:

- **Competition name** (bold, left-aligned)
- **Season** (subtitle, muted color, below the name)
- **Match count** (right-aligned): `X partits disponibles`
- **Results summary** (right-aligned): `Resultats: X victòries, X empats, X derrotes`

### Card Data

| # | Competition                 | Season    | Partits | Victòries | Empats | Derrotes |
|---|-----------------------------|-----------|---------|-----------|--------|----------|
| 1 | divisio-honor-masculino     | 2023-2024 | 22      | 11        | 0      | 11       |
| 2 | primera-divisio-femenino    | 2023-2024 | 20      | 16        | 0      | 4        |
| 3 | segona-divisio-masculino    | 2023-2024 | 22      | 12        | 0      | 10       |
| 4 | divisio-honor-masculino     | 2024-2025 | 22      | 9         | 0      | 13       |

---

## Visual Layout (ASCII)

```
+------------------------------------------------------------------------+
| IDENTITAT DEL CLUB                                  [✎ Edita el club]  |
| # CLUB TENNIS TAULA ELS AMICS TERRASSA                                |
| Font: RFETM                                                           |
|                                                                        |
| [👥 Jugadors] [⚔ Partits]    [v Season ▾]  [v Competition ▾]         |
|                                                                        |
| Competicions                                                           |
|                                                                        |
| +------------------------------------------------------------------+  |
| | divisio-honor-masculino          22 partits · 11V / 0E / 11D     |  |
| | 2023-2024                                                        |  |
| +------------------------------------------------------------------+  |
|                                                                        |
| +------------------------------------------------------------------+  |
| | primera-divisio-femenino         20 partits · 16V / 0E / 4D      |  |
| | 2023-2024                                                        |  |
| +------------------------------------------------------------------+  |
|                                                                        |
| +------------------------------------------------------------------+  |
| | segona-divisio-masculino         22 partits · 12V / 0E / 10D     |  |
| | 2023-2024                                                        |  |
| +------------------------------------------------------------------+  |
|                                                                        |
| +------------------------------------------------------------------+  |
| | divisio-honor-masculino          22 partits · 9V / 0E / 13D      |  |
| | 2024-2025                                                        |  |
| +------------------------------------------------------------------+  |
+------------------------------------------------------------------------+
```

---

## Component Legend

| Symbol / Notation   | Meaning                                |
|---------------------|----------------------------------------|
| `[✎ ...]`          | Outlined action button                 |
| `[👥 ...] [⚔ ...]`| Tab toggle group                       |
| `[v ... ▾]`        | Dropdown selector                      |
| `+---...---+`       | Card container (rounded, light border) |
| `V`                 | Victòries (wins)                       |
| `E`                 | Empats (draws)                         |
| `D`                 | Derrotes (losses)                      |