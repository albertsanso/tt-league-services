#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Conversor de actas RFETM (HTML + PDF) a JSON

Recorre lo descargado por download_actas_from_rfetm.py para una temporada:

resources/actas-html-pdf/<season>/<category>/<day>/<sex>/
    grupo_<N>.html       (una página de jornada por grupo)
    acta_<id>.pdf        (acta de cada partido, si está publicada)

Cada partido de cada grupo_<N>.html produce un JSON en:

resources/actas-json/<season>/<category>/<day>/<sex>/acta_<local_id>_<visitante_id>.json

- Sin PDF: datos mínimos del HTML (equipos + ids, fecha, hora, lugar),
  "acta_publicada": false, sin partidos ni alineaciones.
- Con PDF: se parsea con src/actas-pdf/parser-acta-pdf-2025-2026.py y se
  enriquece con los ids de equipo del HTML; "acta_publicada": true.

La salida sigue docs/acta-model-definition.json. Los errores se escriben en
resources/actas-json/<season>/conversion_errors.log.

Uso:
    python src/actas-html-pdf/convert_actas_to_json.py --season 2026-2027
    python src/actas-html-pdf/convert_actas_to_json.py --season 2026-2027 --category divisio-honor --jornada 1
    python src/actas-html-pdf/convert_actas_to_json.py --season 2026-2027 --validate
"""

import re
import sys
import json
import codecs
import argparse
import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.parse import urlparse, parse_qs

from bs4 import BeautifulSoup

from ingest_common.validation import ACTA_SCHEMA_PATH

# ══════════════════════════════════════════
#  Constantes
# ══════════════════════════════════════════

# Raíz del repositorio: src/actas-html-pdf/<script> → parent.parent.parent
# Rutas configuradas por configure(); no hay valor por defecto dentro del repositorio.
WORKSPACE_ROOT = None
INPUT_DIR = None
OUTPUT_DIR = None


def configure(content_dir: Path, json_dir: Path) -> None:
    """Fija el directorio de descargas y el de JSON (<data_dir>/rfetm/{content,actas-json})."""
    global WORKSPACE_ROOT, INPUT_DIR, OUTPUT_DIR
    INPUT_DIR = Path(content_dir)
    OUTPUT_DIR = Path(json_dir)
    WORKSPACE_ROOT = INPUT_DIR.parent

FEDERACION = "Real Federación Española de Tenis de Mesa"

SEXOS = {"masculino", "femenino"}

# ══════════════════════════════════════════
#  Logging
# ══════════════════════════════════════════

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


@dataclass
class Stats:
    """Contadores de la conversión y lista de errores."""
    html_procesados: int = 0
    partidos: int = 0
    con_pdf: int = 0
    pdf_en_blanco: int = 0
    sin_pdf: int = 0
    saltados: int = 0
    invalidos: int = 0
    errores: List[str] = field(default_factory=list)

    def error(self, mensaje: str) -> None:
        logger.error(mensaje)
        self.errores.append(mensaje)


def setup_error_log(season: str) -> Path:
    """Añade un handler que escribe WARNING/ERROR en <season>/conversion_errors.log."""
    log_path = OUTPUT_DIR / season / "conversion_errors.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    handler = logging.FileHandler(log_path, mode="w", encoding="utf-8")
    handler.setLevel(logging.WARNING)
    handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logging.getLogger().addHandler(handler)
    return log_path


def load_pdf_parser():
    """Devuelve parse_acta() de ingest_rfetm.pdf_acta."""
    from ingest_rfetm.pdf_acta import parse_acta
    return parse_acta


# ══════════════════════════════════════════
#  Lectura del HTML
# ══════════════════════════════════════════


def _latin1_fallback(error: UnicodeDecodeError):
    """Decodifica como Latin-1 los bytes que no son utf-8 válido."""
    bad = error.object[error.start:error.end]
    return bad.decode("iso-8859-1"), error.end


codecs.register_error("latin1_fallback", _latin1_fallback)


def read_html(path: Path) -> str:
    """
    Las páginas RFETM mezclan utf-8 y Latin-1 en el mismo fichero
    (p.ej. 'DIVISIÓN' en Latin-1 junto a nombres en utf-8), así que se
    decodifica como utf-8 y los bytes inválidos se interpretan como Latin-1.
    """
    return path.read_bytes().decode("utf-8", errors="latin1_fallback")


def clean(text: Optional[str]) -> Optional[str]:
    """Normaliza espacios (incluido &nbsp;) y devuelve None si queda vacío."""
    if text is None:
        return None
    t = " ".join(str(text).replace("\xa0", " ").split()).strip()
    return t or None


def query_param(href: str, name: str) -> Optional[str]:
    values = parse_qs(urlparse(href).query).get(name)
    return values[0] if values else None


def parse_fecha_hora(text: Optional[str]):
    """'26/09/2026 16:00' → ('2026-09-26', '16:00'). Cualquiera puede faltar."""
    fecha = hora = None
    if not text:
        return fecha, hora
    m = re.search(r"(\d{1,2})/(\d{1,2})/(\d{4})", text)
    if m:
        fecha = f"{m.group(3)}-{m.group(2).zfill(2)}-{m.group(1).zfill(2)}"
    m = re.search(r"\b(\d{1,2}):(\d{2})\b", text)
    if m:
        hora = f"{m.group(1).zfill(2)}:{m.group(2)}"
    return fecha, hora


def parse_lugar(text: Optional[str]) -> Optional[Dict[str, Optional[str]]]:
    """'POLIDEPORTIVO ORCASUR - Madrid (Madrid)' → {recinto, ciudad}."""
    text = clean(text)
    if not text:
        return None
    if " - " in text:
        recinto, ciudad = text.rsplit(" - ", 1)
        return {"ciudad": clean(ciudad), "recinto": clean(recinto)}
    return {"ciudad": None, "recinto": text}


def parse_competicion(soup: BeautifulSoup) -> Optional[str]:
    """Título h3: 'DIVISIÓN DE HONOR MASCULINA GRUPO 1 - Temporada 2026-2027' → 'DIVISIÓN DE HONOR MASCULINA'."""
    h3 = soup.find("h3")
    if not h3:
        return None
    text = clean(h3.get_text(" "))
    if not text:
        return None
    text = re.split(r"\s+-\s+Temporada", text, flags=re.IGNORECASE)[0]
    text = re.sub(r"\s+GRUPO\s+\d+\s*$", "", text, flags=re.IGNORECASE)
    return clean(text)


def find_info_row(header_row) -> Optional[Any]:
    """Fila 'Lugar: ... Árbitro: ...' que sigue a la cabecera de un partido no jugado."""
    for row in header_row.find_all_next("tr"):
        if row is header_row:
            continue
        # Se detiene al llegar a la cabecera del siguiente partido
        if len(row.find_all("a", href=re.compile(r"equipo=\d+"))) >= 2:
            return None
        if "Lugar:" in row.get_text():
            return row
    return None


def parse_info_row(row) -> Dict[str, Any]:
    """Extrae lugar y árbitro de la fila de información del partido."""
    text = row.get_text("\n")
    lugar = arbitro = None
    m = re.search(r"Lugar:\s*(.+)", text)
    if m:
        lugar = parse_lugar(m.group(1))
    m = re.search(r"rbitro:\s*(.+)", text)
    if m:
        nombre = clean(m.group(1))
        if nombre and nombre.lower() != "sin designar":
            arbitro = {"nombre": nombre, "licencia": None}
    return {"lugar": lugar, "arbitro": arbitro}


def parse_html_matches(html: str) -> Dict[str, Any]:
    """
    Extrae de una página de jornada la competición y la lista de partidos.

    Cada partido es la fila <tr> con dos enlaces '?...&equipo=<id>':
    fecha/hora, equipo local, marcador (si se jugó), equipo visitante y,
    si existe, el enlace al acta PDF (/ligas/partido/<id>/imprimir/acta).
    """
    soup = BeautifulSoup(html, "html.parser")
    partidos = []

    for row in soup.find_all("tr"):
        team_links = row.find_all("a", href=re.compile(r"equipo=\d+"), recursive=True)
        # Solo filas cabecera de partido (no las de tablas anidadas que las contienen)
        if len(team_links) != 2 or row.find("tr"):
            continue

        local_a, visitante_a = team_links
        cells = row.find_all("td", recursive=False)

        fecha, hora = parse_fecha_hora(cells[0].get_text(" ") if cells else None)

        # Marcador: celdas numéricas entre los dos equipos (vacío si no se jugó)
        tanteo = []
        for cell in cells:
            if cell.find("a"):
                continue
            value = clean(cell.get_text(" "))
            if value and re.fullmatch(r"\d+", value):
                tanteo.append(int(value))

        acta_id = None
        acta_link = row.find("a", href=re.compile(r"/ligas/partido/\d+/imprimir/acta"))
        if acta_link:
            acta_id = re.search(r"/ligas/partido/(\d+)/", acta_link["href"]).group(1)

        info_row = find_info_row(row)
        info = parse_info_row(info_row) if info_row else {"lugar": None, "arbitro": None}

        partidos.append({
            "fecha": fecha,
            "hora": hora,
            "local": {"id": query_param(local_a["href"], "equipo"), "nombre": clean(local_a.get_text())},
            "visitante": {"id": query_param(visitante_a["href"], "equipo"), "nombre": clean(visitante_a.get_text())},
            "marcador": tanteo if len(tanteo) == 2 else None,
            "acta_id": acta_id,
            "lugar": info["lugar"],
            "arbitro": info["arbitro"],
        })

    return {"competicion": parse_competicion(soup), "partidos": partidos}


# ══════════════════════════════════════════
#  Construcción del JSON
# ══════════════════════════════════════════


def build_id_partido(season: str, category: str, grupo: int, jornada: int, partido: Dict[str, Any]) -> str:
    """'2026-2027_divisio-honor_G1_J1_1351-385'."""
    return f"{season}_{category}_G{grupo}_J{jornada}_{partido['local']['id']}-{partido['visitante']['id']}"


def build_acta_minima(season: str, competicion: Optional[str], genero: str, grupo: int,
                      jornada: int, partido: Dict[str, Any]) -> Dict[str, Any]:
    """Acta sin PDF: solo los datos del HTML."""
    marcador = partido["marcador"]
    ganador = None
    if marcador and marcador[0] != marcador[1]:
        ganador = partido["local"]["nombre"] if marcador[0] > marcador[1] else partido["visitante"]["nombre"]

    def equipo(lado):
        return {
            "id": partido[lado]["id"],
            "nombre": partido[lado]["nombre"],
            "delegado": None,
            "entrenador": None,
        }

    return {
        "acta_publicada": False,
        "federacion": FEDERACION,
        "temporada": season.replace("-", "/"),
        "genero": genero,
        "competicion": competicion,
        "grupo": grupo,
        "jornada": jornada,
        "fecha": partido["fecha"],
        "hora": partido["hora"],
        "lugar": partido["lugar"],
        "equipos": {"local": equipo("local"), "visitante": equipo("visitante")},
        "abc_es_local": None,
        "arbitros": {"principal": partido["arbitro"], "asistente": None},
        "alineaciones": {"local": {}, "visitante": {}},
        "dobles": None,
        "partidos": [],
        "resultado_final": {
            "ganador": ganador,
            "marcador_partidos": {"local": marcador[0], "visitante": marcador[1]} if marcador else None,
            "marcador_juegos": None,
        },
        "acta_protestada": False,
    }


def build_acta_pdf(parse_acta, pdf_path: Path, season: str, competicion: Optional[str], genero: str,
                   grupo: int, jornada: int, partido: Dict[str, Any]) -> Dict[str, Any]:
    """Acta con PDF: se parsea el PDF y se completa con los datos del HTML."""
    acta = parse_acta(str(pdf_path))
    ctx = f"{pdf_path.relative_to(WORKSPACE_ROOT)}"

    # Coherencia HTML ↔ PDF
    for lado in ("local", "visitante"):
        nombre_html = partido[lado]["nombre"]
        nombre_pdf = acta["equipos"][lado]["nombre"]
        if nombre_html and nombre_pdf and nombre_html.upper() != nombre_pdf.upper():
            logger.warning(f"{ctx}: equipo {lado} distinto en HTML ('{nombre_html}') y PDF ('{nombre_pdf}')")
    if acta.get("jornada") not in (None, jornada):
        logger.warning(f"{ctx}: jornada {acta['jornada']} en PDF, {jornada} en la carpeta")

    # Campos que el PDF puede no traer: se completan con el HTML
    acta["temporada"] = acta.get("temporada") or season.replace("-", "/")
    acta["competicion"] = acta.get("competicion") or competicion
    acta["grupo"] = acta["grupo"] if acta.get("grupo") is not None else grupo
    acta["jornada"] = jornada
    acta["fecha"] = acta.get("fecha") or partido["fecha"]
    acta["hora"] = acta.get("hora") or partido["hora"]
    lugar = acta.get("lugar")
    if not lugar or not (lugar.get("ciudad") or lugar.get("recinto")):
        acta["lugar"] = partido["lugar"]
    if acta["arbitros"].get("asistente") is not None:
        # El esquema solo admite null en el asistente
        logger.warning(f"{ctx}: árbitro asistente descartado ({acta['arbitros']['asistente']})")
        acta["arbitros"]["asistente"] = None
    if not isinstance(acta.get("acta_protestada"), bool):
        # El parser devuelve el texto de la protesta; el esquema pide booleano
        logger.warning(f"{ctx}: acta protestada por '{acta['acta_protestada']}'")
        acta["acta_protestada"] = True

    for lado in ("local", "visitante"):
        equipo = acta["equipos"][lado]
        acta["equipos"][lado] = {
            "id": partido[lado]["id"],
            "nombre": equipo.get("nombre") or partido[lado]["nombre"],
            "delegado": equipo.get("delegado"),
            "entrenador": equipo.get("entrenador"),
        }

    resultado = acta["resultado_final"]
    juegos = resultado.get("marcador_juegos")
    if juegos and juegos.get("local") is None and juegos.get("visitante") is None:
        resultado["marcador_juegos"] = None

    # PDF en blanco (acta preimpresa de un partido aún no jugado): ningún set
    # disputado y sin marcador (o 0-0) en el HTML → se trata como acta no
    # publicada, conservando la cabecera del PDF (lugar, árbitro, delegados...).
    publicada = any(p["sets"] for p in acta["partidos"]) or bool(partido["marcador"] and sum(partido["marcador"]))
    if not publicada:
        logger.warning(f"{ctx}: PDF sin ningún partido disputado; se marca como acta no publicada")
        minima = build_acta_minima(season, competicion, genero, grupo, jornada, partido)
        for key in ("abc_es_local", "alineaciones", "dobles", "partidos", "resultado_final"):
            acta[key] = minima[key]

    # Orden de claves: metadatos primero, igual que el acta mínima
    cabecera = {"acta_publicada": publicada, "federacion": FEDERACION, "temporada": acta["temporada"],
                "genero": genero}
    return {**cabecera, **{k: v for k, v in acta.items() if k not in cabecera}}


# ══════════════════════════════════════════
#  Validación
# ══════════════════════════════════════════


def load_validator():
    """Validador del esquema, o None si jsonschema no está instalado."""
    try:
        import jsonschema
    except ImportError:
        logger.warning("jsonschema no está instalado: se omite la validación.")
        return None
    schema = json.loads(ACTA_SCHEMA_PATH.read_text(encoding="utf-8"))
    return jsonschema.Draft202012Validator(schema)


def validation_errors(validator, acta: Dict[str, Any]) -> List[str]:
    if validator is None:
        return []
    return [
        f"{'/'.join(str(p) for p in e.absolute_path) or '<raíz>'}: {e.message}"
        for e in validator.iter_errors(acta)
    ]


# ══════════════════════════════════════════
#  Recorrido de la temporada
# ══════════════════════════════════════════


def iter_html_files(season: str, category: Optional[str], jornadas: Optional[List[int]]):
    """Genera (category, jornada, genero, grupo, path) de cada grupo_<N>.html."""
    season_dir = INPUT_DIR / season
    for path in sorted(season_dir.glob("*/*/*/grupo_*.html")):
        genero_dir, jornada_dir, category_dir = path.parent, path.parent.parent, path.parent.parent.parent
        if category and category_dir.name != category:
            continue
        if genero_dir.name not in SEXOS or not jornada_dir.name.isdigit():
            logger.warning(f"Ruta inesperada, se ignora: {path.relative_to(WORKSPACE_ROOT)}")
            continue
        jornada = int(jornada_dir.name)
        if jornadas and jornada not in jornadas:
            continue
        m = re.fullmatch(r"grupo_(\d+)\.html", path.name)
        if not m:
            continue
        yield category_dir.name, jornada, genero_dir.name, int(m.group(1)), path


def is_up_to_date(out_path: Path, html_path: Path, pdf_path: Optional[Path]) -> bool:
    """El JSON existe y es posterior a su HTML y, si lo hay, a su PDF (la descarga incremental los renueva)."""
    if not out_path.exists():
        return False
    generated = out_path.stat().st_mtime
    sources = [html_path] + ([pdf_path] if pdf_path and pdf_path.exists() else [])
    return all(source.stat().st_mtime <= generated for source in sources)


def convert_season(season: str, category: Optional[str], jornadas: Optional[List[int]],
                   force: bool, validate: bool) -> Stats:
    stats = Stats()
    parse_acta = load_pdf_parser()
    validator = load_validator() if validate else None

    if not (INPUT_DIR / season).is_dir():
        stats.error(f"No existe la carpeta de descargas: {INPUT_DIR / season}")
        return stats

    # Detección de colisiones: dos partidos del mismo cruce en la misma jornada/carpeta
    generados: Dict[Path, str] = {}

    for cat, jornada, genero, grupo, html_path in iter_html_files(season, category, jornadas):
        rel_html = html_path.relative_to(WORKSPACE_ROOT)
        try:
            pagina = parse_html_matches(read_html(html_path))
        except Exception as e:
            stats.error(f"{rel_html}: error leyendo HTML: {e}")
            continue
        stats.html_procesados += 1

        if not pagina["partidos"]:
            logger.debug(f"{rel_html}: sin partidos")
            continue

        out_dir = OUTPUT_DIR / season / cat / str(jornada) / genero

        for partido in pagina["partidos"]:
            stats.partidos += 1
            local_id, visitante_id = partido["local"]["id"], partido["visitante"]["id"]
            out_path = out_dir / f"acta_{local_id}_{visitante_id}.json"
            origen = f"{rel_html} ({partido['local']['nombre']} - {partido['visitante']['nombre']})"

            if out_path in generados:
                stats.error(f"{origen}: colisión con {generados[out_path]} → {out_path.name}; se sobrescribe")
            generados[out_path] = origen

            pdf_path = html_path.parent / f"acta_{partido['acta_id']}.pdf" if partido["acta_id"] else None
            if not force and is_up_to_date(out_path, html_path, pdf_path):
                stats.saltados += 1
                continue

            try:
                if pdf_path and pdf_path.exists():
                    acta = build_acta_pdf(parse_acta, pdf_path, season, pagina["competicion"],
                                          genero, grupo, jornada, partido)
                    if acta["acta_publicada"]:
                        stats.con_pdf += 1
                    else:
                        stats.pdf_en_blanco += 1
                else:
                    if partido["marcador"]:
                        logger.warning(f"{origen}: partido jugado sin PDF de acta (id {partido['acta_id']})")
                    acta = build_acta_minima(season, pagina["competicion"], genero, grupo, jornada, partido)
                    stats.sin_pdf += 1
            except Exception as e:
                stats.error(f"{origen}: error parseando {pdf_path.name if pdf_path else 'HTML'}: {e}")
                continue

            acta = {"id_partido": build_id_partido(season, cat, grupo, jornada, partido), **acta}

            errores = validation_errors(validator, acta)
            if errores:
                stats.invalidos += 1
                logger.warning(f"{origen}: no cumple el esquema:\n    " + "\n    ".join(errores))

            try:
                out_dir.mkdir(parents=True, exist_ok=True)
                out_path.write_text(json.dumps(acta, ensure_ascii=False, indent=2), encoding="utf-8")
                logger.debug(f"Guardado: {out_path.relative_to(WORKSPACE_ROOT)}")
            except OSError as e:
                stats.error(f"{origen}: error guardando {out_path}: {e}")

    return stats


def log_summary(season: str, stats: Stats, log_path: Path, validate: bool) -> None:
    logger.info("=" * 60)
    logger.info(f"Resumen conversión temporada {season}")
    logger.info("=" * 60)
    logger.info(f"  HTML procesados:        {stats.html_procesados}")
    logger.info(f"  Partidos encontrados:   {stats.partidos}")
    logger.info(f"  Actas con PDF:          {stats.con_pdf}")
    logger.info(f"  PDF en blanco:          {stats.pdf_en_blanco}")
    logger.info(f"  Actas sin PDF:          {stats.sin_pdf}")
    logger.info(f"  Ya existentes:          {stats.saltados}")
    if validate:
        logger.info(f"  No cumplen el esquema:  {stats.invalidos}")
    logger.info(f"  Errores:                {len(stats.errores)}")
    logger.info(f"  Destino: {OUTPUT_DIR / season}")
    logger.info(f"  Log de errores: {log_path}")
    logger.info("=" * 60)


# ══════════════════════════════════════════
#  Punto de entrada
# ══════════════════════════════════════════


def validate_season_format(season: str) -> bool:
    """Valida que la temporada tenga el formato YYYY-YYYY con años consecutivos."""
    if not re.match(r"^\d{4}-\d{4}$", season):
        return False
    start_year, end_year = season.split("-")
    return int(end_year) == int(start_year) + 1


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Convierte las actas RFETM descargadas (HTML de jornadas + PDF) a JSON",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ejemplos:
  python src/actas-html-pdf/convert_actas_to_json.py --season 2026-2027
  python src/actas-html-pdf/convert_actas_to_json.py --season 2026-2027 --category divisio-honor --jornada 1 --force
        """,
    )
    parser.add_argument("--content-dir", required=True, type=Path,
                        help="Directorio de descargas (<data_dir>/rfetm/content).")
    parser.add_argument("--json-dir", required=True, type=Path,
                        help="Directorio de salida (<data_dir>/rfetm/actas-json).")
    parser.add_argument("--season", required=True, metavar="YYYY-YYYY",
                        help="Temporada a convertir (ej: 2026-2027).")
    parser.add_argument("--category", help="Convertir solo esta categoría (ej: divisio-honor).")
    parser.add_argument("--jornada", type=int, action="append", metavar="N",
                        help="Convertir solo esta jornada. Se puede repetir.")
    parser.add_argument("--force", action="store_true",
                        help="Regenera los JSON aunque ya existan.")
    parser.add_argument("--validate", action="store_true",
                        help="Valida cada JSON contra docs/acta-model-definition.json (requiere jsonschema).")
    parser.add_argument("--debug", action="store_true", help="Muestra mensajes de depuración.")
    args = parser.parse_args(argv)
    configure(args.content_dir, args.json_dir)

    if args.debug:
        logging.getLogger().setLevel(logging.DEBUG)

    if not validate_season_format(args.season):
        logger.error(f"Formato de temporada incorrecto: '{args.season}'. Use YYYY-YYYY (ej: 2026-2027).")
        return 1

    log_path = setup_error_log(args.season)
    stats = convert_season(args.season, args.category, args.jornada, args.force, args.validate)
    log_summary(args.season, stats, log_path, args.validate)
    return 1 if stats.errores else 0


if __name__ == "__main__":
    sys.exit(main())
