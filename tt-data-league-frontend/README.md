# TT League Frontend

SPA React/Vite per a la capa d'interfície del projecte TT League. Implementa el
dashboard inicial (FEAT-00001) amb shell de navegació, overview i pàgines bàsiques.

## Requisits

- Node.js 20+ i npm 10+ (la build Maven ja els fixa via `frontend-maven-plugin`).
- Backend opcional: si no hi ha `/api/stats/community`, el frontend usa dades mock
  deterministes. Les rutes del dashboard requereixen una sessió autenticada.

En producció (desplegament en una sola VM) el frontend el serveix la imatge del proxy, `deploy/proxy/Dockerfile`;
veure [deploy/README.md](../deploy/README.md).

## Scripts npm

```bash
npm ci
npm run dev
npm run lint
npm run build
```

## Sistema de disseny

Les noves funcionalitats han de seguir el contracte compacte de
`../docs/frontend/design-contract.md` i poden consultar l'especificació visual
completa a `../docs/frontend/theme-spec.md`. Reutilitzeu els tokens de
`src/index.css` i els components compartits de `src/components/ui` abans
d'afegir estils o primitives locals.

## Execució local

```bash
npm run dev
```

L'aplicació queda disponible a `http://localhost:5173`.

Durant el desenvolupament, les peticions `/api` es redirigeixen a
`http://localhost:8080` per defecte. Es pot canviar amb la variable pública de
configuració `VITE_API_PROXY_TARGET`; no hi poseu credencials. En producció les
peticions continuen sent relatives i same-origin.

## Calendari de la temporada (FEAT-00093)

La pàgina `/calendari` usa FullCalendar (`@fullcalendar/core`, `react` i `list`,
v6, només plugins MIT) per a les vistes dia i jornada, i `date-fns`. Ofereix
les vistes dia, setmana, mes i jornada, filtres per font, temporada, competició,
grup i equip, i un diàleg amb el detall del partit que s'obre en seleccionar-lo.
Tot l'estat viu a la URL (sense llibreria d'estat global):

| Paràmetre | Significat |
| --- | --- |
| `source` | Font. Buit (per defecte) vol dir totes les fonts. |
| `season` | Temporada. Per defecte, la més recent entre les fonts seleccionades; no s’escriu a la URL. |
| `view` | `day`, `week` (per defecte), `month` o `jornada`. |
| `date` | Data àncora `YYYY-MM-DD` (per defecte avui; una data invàlida es tracta com a avui). |
| `competition`, `group`, `team` | Filtres; la vista jornada requereix competició. |
| `round` | Jornada seleccionada (vista jornada; per defecte, la jornada actual del grup). |

Els enllaços antics que encara porten `sort` o `selected` (eliminats amb el detall
del calendari) s'accepten i se n'ignora el valor; es descarten en el canvi
següent de la URL.

Sense `source`, les vistes dia, setmana i mes fan una petició de
`GET /api/v1/match/calendar/range` per a cada font (RFETM, FCTT i BCNESA) i combinen
els resultats: si una font falla, es mostra l’error en lloc d’un calendari parcial.
Les opcions de temporada i competició s’obtenen de la mateixa manera amb
`GET /api/v1/match/options`. La vista jornada necessita una única font i usa
`GET /api/v1/match/calendar`, també sense combinar fonts. El frontend no
calcula estats, terminis ni jornada actual: mostra el `calendarState` de l'API.

## Idiomes i traduccions

La interfície utilitza i18next i té el català (`ca`) com a idioma predeterminat,
amb castellà (`es`) i anglès (`en`) disponibles. L'idioma es pot canviar des de
**Configuració > Preferències > Idioma**; la selecció es desa a
`localStorage` (`tt-league.locale`). Els valors no admesos o els errors d'accés
a l'emmagatzematge tornen al català, que també és el fallback per a claus
absents.

Les claus semàntiques i els recursos són a `src/i18n/ca.js`, `es.js` i `en.js`.
No poseu literals d'interfície als components: afegiu una clau a tots els
recursos i useu `t('namespace.key')`, amb interpolació per a valors dinàmics.
Per afegir un idioma, creeu el seu recurs, registreu el codi a
`src/i18n/index.js` i afegiu-lo al selector de configuració.

La sessió es desa només a `sessionStorage` i s'elimina en tancar sessió o quan
l'API respon `401`. La recuperació de contrasenya usa el flux d'email del
backend i no mostra mai el token a la interfície.

## Integració amb Maven

Des de l'arrel del repositori:

```bash
mvn -pl tt-data-league-frontend -am test
```

Aquest mòdul executa `npm ci`, `npm run lint` i `npm run build` durant el pipeline
Maven. Les proves de components no formen part de FEAT-00001.
