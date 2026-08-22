# TT League Frontend

SPA React/Vite per a la capa d'interfície del projecte TT League. Implementa el
dashboard inicial (FEAT-001) amb shell de navegació, overview i pàgines bàsiques.

## Requisits

- Node.js 20+ i npm 10+ (la build Maven ja els fixa via `frontend-maven-plugin`).
- Backend opcional: si no hi ha `/api/stats/community`, el frontend usa dades mock
  deterministes. Les rutes del dashboard requereixen una sessió autenticada.

## Scripts npm

```bash
npm ci
npm run dev
npm run lint
npm run build
```

## Execució local

```bash
npm run dev
```

L'aplicació queda disponible a `http://localhost:5173`.

Durant el desenvolupament, les peticions `/api` es redirigeixen a
`http://localhost:8080` per defecte. Es pot canviar amb la variable pública de
configuració `VITE_API_PROXY_TARGET`; no hi poseu credencials. En producció les
peticions continuen sent relatives i same-origin.

La sessió es desa només a `sessionStorage` i s'elimina en tancar sessió o quan
l'API respon `401`. La recuperació de contrasenya usa el flux d'email del
backend i no mostra mai el token a la interfície.

## Integració amb Maven

Des de l'arrel del repositori:

```bash
mvn -pl tt-data-league-frontend -am test
```

Aquest mòdul executa `npm ci`, `npm run lint` i `npm run build` durant el pipeline
Maven. Les proves de components no formen part de FEAT-001.
