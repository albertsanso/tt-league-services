# Acta Simplificada RFETM — Wiremock / ASCII Reconstruction Spec

Source: a screenshot of a simplified RFETM table tennis match report ("acta").
This document is a structural wiremock of that screenshot, meant to be handed
to an LLM as the spec for regenerating an equivalent Acta as HTML. It uses
`@variable_name` placeholders for every piece of data that changes per acta,
and plain prose notes for layout/behavior that should carry over.

====================================================================================================
[ TOP BAR — Jornada label ]
====================================================================================================
Full-width dark bar (dark grey/near-black background, white bold centered text),
with a thin red/accent underline directly beneath it.

    +----------------------------------------------------------------------------------------------+
    |                                        Jornada @jornada_numero                                 |
    +----------------------------------------------------------------------------------------------+
    ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔ (red accent underline, full width) ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔

====================================================================================================
[ SCORE HEADER ROW — date/time | teams + score | print icon ]
====================================================================================================
Single row, three zones, light background, bottom border separating it from the table below.

    +----------------------------------------------------------------------------------------------+
    | @fecha_partido            @equipo_local_nombre  [ @juegos_local ] - [ @juegos_visitante ]     |
    | @hora_partido                                            @equipo_visitante_nombre        🖨️  |
    +----------------------------------------------------------------------------------------------+

- `@fecha_partido` (e.g. `26/09/2025`) and `@hora_partido` (e.g. `18:00`) stack vertically, left-aligned,
  small grey text.
- Team names are bold/uppercase, centered around the score.
- `@juegos_local` / `@juegos_visitante` (aggregate games won by each team, e.g. `4` and `1`) are each
  rendered inside a dark square badge (dark grey background, white bold digit) with the winner's
  badge visually identical to the loser's (no extra highlight at this level).
- A print icon (🖨️) sits at the far right, vertically centered.

====================================================================================================
[ INDIVIDUAL MATCHES TABLE ]
====================================================================================================
Header row (light grey background, bold small-caps-like labels):

    ┌───────────────────────────────┬─────────────────────────────────┬────────┬────────┬────────┬────────┬────────┬─────────┬─────────┐
    │ @equipo_local_nombre          │ @equipo_visitante_nombre         │   J1   │   J2   │   J3   │   J4   │   J5   │ PARCIAL │  GLOBAL │
    └───────────────────────────────┴─────────────────────────────────┴────────┴────────┴────────┴────────┴────────┴─────────┴─────────┘

One row per individual match (5 rows in a standard tie: 3 singles + cross doubles/singles
combinations identified by position letters). Each row:

    ┌───┬───────────────────────────────┬───┬───────────────────────────────┬────────┬────────┬────────┬────────┬────────┬─────────┬─────────┐
    │ @pos_local_letra │ Lic: @jugador_local_licencia  Rk: @jugador_local_ranking     │ @pos_cruce_letra │ Lic: @jugador_visitante_licencia  Rk: @jugador_visitante_ranking │ @set1_local-@set1_vis │ @set2_local-@set2_vis │ @set3_local-@set3_vis │ @set4_local-@set4_vis │ @set5_local-@set5_vis │ @parcial_local-@parcial_vis │ @acumulado_local-@acumulado_vis │
    │                  │ @jugador_local_nombre (bold, uppercase)                     │                  │ @jugador_visitante_nombre (bold, uppercase)                       │                        │                        │                        │                        │                        │                              │                                  │
    └───┴───────────────────────────────┴───┴───────────────────────────────┴────────┴────────┴────────┴────────┴────────┴─────────┴─────────┘

Repeat for 5 rows total: `@match_row[1..5]`, each with its own set of the fields above.

Field notes:
- `@pos_local_letra`: single-letter position code for the local-team player in this individual
  match (e.g. `A`, `B`, `C`). Rendered as a plain bold letter in its own narrow column, left of the
  local player block.
- `@pos_cruce_letra`: single-letter code identifying the pairing/crossing pattern for this match
  (e.g. `Y`, `X`, `Z`), rendered the same way between the local and visitor player blocks. This is
  the RFETM convention for which local position played which visitor position, not the visitor's
  own position letter.
- `Lic:` is the player's federation license number; `Rk:` is their ranking value (may include a
  decimal, e.g. `2465.1`). Both are small, grey/regular weight, on one line above the bold player
  name.
- `@setN_local-@setN_vis`: per-set point score (e.g. `11-8`). Blank cells (`—` or empty) when the
  match ended before 5 sets were played — leave `J5` (and any unplayed trailing set columns) empty
  for matches that finished in fewer sets.
- `@parcial_local-@parcial_vis`: the match result expressed in sets won (best-of-5, so values range
  `3-0` to `3-2`), rendered bold. The winning side's digit is visually emphasized in an accent color
  (orange/amber) while the losing side's digit stays default dark text — i.e. color marks who won
  *this individual match*, independent of which team ultimately won the tie.
- `@acumulado_local-@acumulado_vis`: the running tie score (aggregate individual-match wins for
  each team) immediately after this row's match concludes, rendered in default dark text, not
  colored. Cell values increase monotonically down the column and should match `@juegos_local` /
  `@juegos_visitante` in the header by the last row.

====================================================================================================
[ ALIGNMENT / TOTALS FOOTER ROW ]
====================================================================================================
Row directly below the table, light background, four evenly-spaced columns, centered text:

    +----------------------------------------------------------------------------------------------+
    |  Alineación ABC:     |  Alineación XYZ:     |  Juegos:              |  Puntos:                |
    |  @alineacion_local   |  @alineacion_vis     |  @juegos_total_local  |  @puntos_total_local     |
    |                      |                      |  / @juegos_total_vis  |  / @puntos_total_vis     |
    +----------------------------------------------------------------------------------------------+

- `@alineacion_local` / `@alineacion_vis`: the declared lineup-order rating value for each team
  (decimal, e.g. `6756.9`), tied to the "ABC" (local position letters) and "XYZ" (visitor pairing
  letters) naming used in the table above.
- `@juegos_total_local` / `@juegos_total_vis`: total individual games (not sets) won across all
  matches, shown as `local / visitante` (e.g. `13 / 8`).
- `@puntos_total_local` / `@puntos_total_vis`: total point count across all matches, shown as
  `local / visitante` (e.g. `210 / 195`).

====================================================================================================
[ VENUE / REFEREE FOOTER ]
====================================================================================================
Plain text block below the totals row, left-aligned, no border/card — two lines:

    Lugar: @lugar_nombre - @lugar_localidad
    Árbitro: @arbitro_nombre

- `@lugar_nombre`: venue/facility name (e.g. `CAMPO DE FUTBOL SAN LÁZARO`).
- `@lugar_localidad`: city/region in parentheses in the source (e.g. `Santiago de Compostela (A Coruña)`),
  joined to the venue name with a hyphen.
- `@arbitro_nombre`: referee full name, uppercase in the source.

====================================================================================================
[ DATA SUMMARY — fields an LLM needs to populate this template ]
====================================================================================================
- Header: `@jornada_numero`, `@fecha_partido`, `@hora_partido`, `@equipo_local_nombre`,
  `@equipo_visitante_nombre`, `@juegos_local`, `@juegos_visitante`.
- Per row (×5): `@pos_local_letra`, `@jugador_local_licencia`, `@jugador_local_ranking`,
  `@jugador_local_nombre`, `@pos_cruce_letra`, `@jugador_visitante_licencia`,
  `@jugador_visitante_ranking`, `@jugador_visitante_nombre`, `@set1..5_local-@set1..5_vis`,
  `@parcial_local-@parcial_vis` (+ winner-side highlight), `@acumulado_local-@acumulado_vis`.
- Totals: `@alineacion_local`, `@alineacion_vis`, `@juegos_total_local`, `@juegos_total_vis`,
  `@puntos_total_local`, `@puntos_total_vis`.
- Footer: `@lugar_nombre`, `@lugar_localidad`, `@arbitro_nombre`.

This is a *simplified* acta variant (no lineup/officials/signature cards like the fuller wiremock in
[actas-wiremock.md](./actas-wiremock.md)) — only the scoreboard, the individual-match table, the
alignment/totals row, and the venue/referee line are present.
