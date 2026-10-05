#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Descargador de actas RFETM (HTML + PDF)

Descarga, para una temporada, las páginas HTML de resultados por jornada de:
https://www.rfetm.es/public/resultados/<season>/view.php?liga=...&grupo=...&subgrupo=S&jornada=...&sexo=...

y, para cada partido enlazado en esas páginas, el PDF del acta:
https://clubs.rfetm.es/ligas/partido/<id>/imprimir/acta

Todo se organiza en:
resources/actas-html-pdf/<season>/<category>/<day>/<sex>/
    grupo_<N>.html       (se guarda aunque la jornada no tenga partidos)
    acta_<id>.pdf        (si el PDF está disponible)

La descarga es incremental: un HTML ya guardado se vuelve a pedir solo si la jornada
está en curso (algún partido empezado sin acta publicada). Se saltan las jornadas
completas (todos los partidos con acta), las futuras (ningún partido empezado) y las
páginas sin partidos. Con --force se descarga todo de nuevo.

Al final se escribe un resumen y los errores en:
resources/actas-html-pdf/<season>/download_errors.log

Uso:
    python src/actas-html-pdf/download_actas_from_rfetm.py --season 2025-2026
    python src/actas-html-pdf/download_actas_from_rfetm.py --season 2025-2026 --category divisio-honor --jornada 3
    python src/actas-html-pdf/download_actas_from_rfetm.py --season 2025-2026 --force --no-pdf
"""

import re
import sys
import time
import argparse
import logging
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple
from urllib.parse import urljoin, urlparse, parse_qs

import requests
from bs4 import BeautifulSoup

from ingest_common import health
from ingest_rfetm.parse import parse_html_matches

# ══════════════════════════════════════════
#  Constantes
# ══════════════════════════════════════════

RFETM_BASE = "https://www.rfetm.es/public/resultados"
CLUBS_BASE = "https://clubs.rfetm.es"

# Jornadas a recorrer: 1-22
JORNADAS = list(range(1, 23))

# Sexos disponibles
SEXOS = ["M", "F"]

# Mapeo de códigos de liga (sin los '==') a nombres de carpeta.
# Mismo mapeo que src/actas-html/web_downloader_rfetm.py.
LIGA_MAPPING = {
    "MQ": "super-divisio",
    "Mg": "divisio-honor",
    "Mw": "primera-divisio",
    "NA": "segona-divisio",
    "Ng": "fasc-super-divisio",
    "Nw": "fasc-divisio-honor",
    "OA": "fasc-primera-divisio",
}

# Ligas principales usadas si no se puede descubrir la estructura de la temporada
FALLBACK_LIGAS = ["MQ==", "Mg==", "Mw==", "NA=="]

SEXO_LABEL = {
    "M": "masculino",
    "F": "femenino",
}

# Raíz del repositorio: src/actas-html-pdf/<script> → parent.parent.parent
# Rutas configuradas por configure(); no hay valor por defecto dentro del repositorio.
WORKSPACE_ROOT = None
OUTPUT_DIR = None


def configure(content_dir: Path) -> None:
    """Fija el directorio de descargas (<data_dir>/rfetm/content)."""
    global WORKSPACE_ROOT, OUTPUT_DIR
    OUTPUT_DIR = Path(content_dir)
    WORKSPACE_ROOT = OUTPUT_DIR.parent

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,application/pdf,*/*;q=0.8",
    "Accept-Language": "es-ES,es;q=0.9",
    "Accept-Encoding": "gzip, deflate",
    "DNT": "1",
    "Connection": "keep-alive",
    "Upgrade-Insecure-Requests": "1",
}

# Límites de ritmo y reintentos (configurables por línea de comandos)
DEFAULT_DELAY = 2.0          # segundos entre peticiones HTTP
DEFAULT_RETRIES = 3          # reintentos ante errores de red / HTTP transitorios
RETRY_BACKOFF = 2.0          # segundos base del backoff exponencial
TIMEOUT = 20                 # segundos
TRANSIENT_STATUS = {429, 502, 503, 504}

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
    """Contadores de la descarga y lista de errores."""
    html_descargados: int = 0
    html_saltados: int = 0
    html_actualizados: int = 0
    pdf_descargados: int = 0
    pdf_saltados: int = 0
    pdf_no_disponibles: int = 0
    errores: List[str] = field(default_factory=list)

    def error(self, mensaje: str) -> None:
        logger.error(mensaje)
        self.errores.append(mensaje)


def setup_error_log(season: str) -> Path:
    """Añade un handler que escribe WARNING/ERROR en <season>/download_errors.log."""
    log_path = OUTPUT_DIR / season / "download_errors.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    handler = logging.FileHandler(log_path, mode="w", encoding="utf-8")
    handler.setLevel(logging.WARNING)
    handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logging.getLogger().addHandler(handler)
    return log_path


# ══════════════════════════════════════════
#  Red
# ══════════════════════════════════════════


class Downloader:
    """Sesión HTTP con reintentos y límite de ritmo entre peticiones."""

    def __init__(self, delay: float = DEFAULT_DELAY, retries: int = DEFAULT_RETRIES):
        self.delay = delay
        self.retries = retries
        self.session = requests.Session()
        self.session.headers.update(HEADERS)
        self._last_request = 0.0

    def close(self) -> None:
        self.session.close()

    def _wait_rate_limit(self) -> None:
        elapsed = time.monotonic() - self._last_request
        if elapsed < self.delay:
            time.sleep(self.delay - elapsed)
        self._last_request = time.monotonic()

    def get(self, url: str) -> Optional[requests.Response]:
        """
        GET con reintentos y backoff exponencial.

        NOTA: el servidor de resultados RFETM devuelve HTTP 500 con HTML válido
        (lo usa para "sin datos"), así que 500 NO se reintenta y se devuelve
        la respuesta para que el llamador decida.

        Returns:
            La respuesta, o None si tras los reintentos sigue fallando.
        """
        timed_out = False
        for intento in range(1, self.retries + 2):
            self._wait_rate_limit()
            timed_out = False
            try:
                response = self.session.get(url, timeout=TIMEOUT)
                if response.status_code not in TRANSIENT_STATUS:
                    return response
                motivo = f"HTTP {response.status_code}"
            except requests.RequestException as e:
                motivo = str(e)
                timed_out = isinstance(e, requests.Timeout)

            if intento <= self.retries:
                espera = RETRY_BACKOFF * (2 ** (intento - 1))
                logger.warning(f"Fallo en {url} ({motivo}). Reintento {intento}/{self.retries} en {espera:.0f}s")
                time.sleep(espera)
            else:
                logger.warning(f"Fallo definitivo en {url} ({motivo})")
                health.timeout() if timed_out else health.http_error()
        return None


def fetch_html(downloader: Downloader, url: str) -> Optional[str]:
    """Descarga una página HTML de RFETM. Acepta 200 y 500 con contenido."""
    response = downloader.get(url)
    if response is None:
        return None
    if response.status_code in (200, 500) and response.content:
        return decode_html(response.content)
    logger.warning(f"HTTP {response.status_code} sin contenido útil: {url}")
    health.http_error()
    return None


def decode_html(content: bytes) -> str:
    """Las páginas RFETM pueden venir en Latin-1: se prueba utf-8 y luego iso-8859-1."""
    try:
        return content.decode("utf-8")
    except UnicodeDecodeError:
        return content.decode("iso-8859-1")


# ══════════════════════════════════════════
#  Generación de URLs
# ══════════════════════════════════════════


def validate_season_format(season: str) -> bool:
    """Valida que la temporada tenga el formato YYYY-YYYY con años consecutivos."""
    if not re.match(r"^\d{4}-\d{4}$", season):
        return False
    start_year, end_year = season.split("-")
    return int(end_year) == int(start_year) + 1


def build_season_url(season: str) -> str:
    return f"{RFETM_BASE}/{season}/"


def build_results_url(season: str, liga_code: str, grupo: str, jornada: int, sexo: str) -> str:
    """URL de resultados de una jornada. El '==' de la liga va sin codificar (el servidor lo espera así)."""
    return (
        f"{RFETM_BASE}/{season}/view.php"
        f"?liga={liga_code}&grupo={grupo}&subgrupo=S&jornada={jornada}&sexo={sexo}"
    )


def discover_competitions(downloader: Downloader, season: str) -> Dict[Tuple[str, str], Set[str]]:
    """
    Descarga la portada de la temporada y extrae las combinaciones liga+grupo
    y los sexos disponibles para cada una a partir de los enlaces view.php.

    Returns:
        {(liga_code, grupo): {sexos}}, p.ej. {('Mg==', '1'): {'M', 'F'}}
    """
    season_url = build_season_url(season)
    logger.info(f"Descubriendo ligas/grupos de {season}: {season_url}")

    html = fetch_html(downloader, season_url)
    if not html:
        return {}

    soup = BeautifulSoup(html, "html.parser")
    competitions: Dict[Tuple[str, str], Set[str]] = {}

    for link in soup.find_all("a", href=True):
        href = str(link["href"])
        if "view.php" not in href or "liga=" not in href:
            continue
        params = parse_qs(urlparse(urljoin(season_url, href)).query)
        liga = params.get("liga", [""])[0]
        if not liga:
            continue
        grupo = params.get("grupo", ["0"])[0]
        sexo = params.get("sexo", [""])[0]
        sexos = competitions.setdefault((liga, grupo), set())
        if sexo in SEXOS:
            sexos.add(sexo)

    # Si un enlace no indica sexo, se prueban ambos
    for key, sexos in competitions.items():
        if not sexos:
            sexos.update(SEXOS)

    for (liga, grupo), sexos in sorted(competitions.items()):
        logger.info(f"  {map_liga_to_category(liga):<22} grupo {grupo:>3} → sexos {sorted(sexos)}")

    return competitions


def generate_urls(
    season: str,
    competitions: Dict[Tuple[str, str], Set[str]],
    jornadas: List[int],
    category_filter: Optional[str] = None,
) -> List[Dict[str, str]]:
    """
    Genera la lista de páginas de jornada a descargar.

    Returns:
        Lista de dicts con url, category, grupo, jornada y sexo.
    """
    tareas = []
    for (liga, grupo), sexos in sorted(competitions.items()):
        category = map_liga_to_category(liga)
        if category_filter and category != category_filter:
            continue
        for jornada in jornadas:
            for sexo in sorted(sexos):
                tareas.append({
                    "url": build_results_url(season, liga, grupo, jornada, sexo),
                    "category": category,
                    "grupo": grupo,
                    "jornada": str(jornada),
                    "sexo": sexo,
                })
    return tareas


def extract_acta_links(html: str) -> List[Tuple[str, str]]:
    """
    Extrae los enlaces a las actas PDF de una página de jornada.

    Returns:
        Lista de (acta_url, partido_id) sin duplicados, en orden de aparición.
    """
    soup = BeautifulSoup(html, "html.parser")
    actas: Dict[str, str] = {}
    for link in soup.find_all("a", href=True):
        href = str(link["href"])
        match = re.search(r"/ligas/partido/(\d+)/imprimir/acta", href)
        if match and match.group(1) not in actas:
            actas[match.group(1)] = urljoin(CLUBS_BASE + "/", href)
    return [(url, partido_id) for partido_id, url in actas.items()]


# ══════════════════════════════════════════
#  Rutas y guardado
# ══════════════════════════════════════════


def map_liga_to_category(liga_code: str) -> str:
    """Mapea 'MQ==' → 'super-divisio'. Códigos desconocidos → 'liga-<code>'."""
    base_code = liga_code.rstrip("=")
    return LIGA_MAPPING.get(base_code, f"liga-{base_code.lower()}")


def get_folder(season: str, category: str, jornada: str, sexo: str) -> Path:
    """resources/actas-html-pdf/<season>/<category>/<day>/<sex>/"""
    return OUTPUT_DIR / season / category / jornada / SEXO_LABEL.get(sexo, sexo.lower())


def save_file(content: bytes, path: Path) -> bool:
    """Guarda bytes en disco (escritura atómica vía fichero temporal)."""
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(path.suffix + ".part")
        tmp.write_bytes(content)
        tmp.replace(path)
        logger.info(f"Guardado: {path.relative_to(WORKSPACE_ROOT)}")
        return True
    except OSError as e:
        logger.error(f"Error al guardar {path}: {e}")
        return False


# ══════════════════════════════════════════
#  Descarga
# ══════════════════════════════════════════


def _match_start(partido: Dict[str, object]) -> Optional[datetime]:
    if not partido.get("fecha"):
        return None
    return datetime.fromisoformat(f"{partido['fecha']}T{partido.get('hora') or '00:00'}")


def jornada_status(html: str, now: datetime) -> str:
    """
    Estado de una página de jornada ya guardada:
      complete  todos los partidos tienen acta publicada
      future    ningún partido ha empezado (todas las fechas son posteriores a now)
      empty     la página no lista partidos
      partial   en curso: algún partido empezado sin acta (o fecha desconocida)
    """
    partidos = parse_html_matches(html)["partidos"]
    if not partidos:
        return "empty"
    if all(p["acta_id"] for p in partidos):
        return "complete"
    starts = [_match_start(p) for p in partidos]
    if all(start is not None and start > now for start in starts):
        return "future"
    return "partial"


def download_jornada_html(downloader: Downloader, tarea: Dict[str, str], path: Path,
                          force: bool, stats: Stats, now: Optional[datetime] = None) -> Optional[str]:
    """
    Descarga el HTML de una jornada, o lo lee de disco si ya está guardado y no hay nada que
    actualizar (jornada completa, futura o sin partidos). Las jornadas en curso se vuelven a pedir.
    Se guarda siempre, aunque la jornada no tenga partidos.

    Returns:
        El HTML (el guardado si la nueva descarga falla), o None si no se pudo obtener.
    """
    saved = decode_html(path.read_bytes()) if path.exists() and path.stat().st_size > 0 else None
    if saved is not None and not force:
        status = jornada_status(saved, now or datetime.now())
        if status != "partial":
            logger.debug(f"  Jornada {status}: no se vuelve a descargar")
            stats.html_saltados += 1
            return saved
        logger.info("  Jornada en curso: se vuelve a descargar")

    html = fetch_html(downloader, tarea["url"])
    if html is None:
        stats.error(f"HTML no descargado: {tarea['url']}")
        return saved

    # Se guarda en utf-8 para uniformidad con el resto del dataset
    if saved is not None and html == saved:
        stats.html_saltados += 1
    elif save_file(html.encode("utf-8"), path):
        if saved is None:
            stats.html_descargados += 1
        else:
            stats.html_actualizados += 1
    else:
        stats.error(f"HTML no guardado: {path}")
    return html


def download_acta_pdf(downloader: Downloader, acta_url: str, path: Path,
                      force: bool, stats: Stats) -> None:
    """Descarga un acta PDF. Si no está disponible (no es PDF / 404) se registra sin abortar."""
    if path.exists() and not force:
        stats.pdf_saltados += 1
        return

    response = downloader.get(acta_url)
    if response is None:
        stats.error(f"PDF no descargado (error de red): {acta_url}")
        return

    content_type = response.headers.get("content-type", "").lower()
    es_pdf = content_type.startswith("application/pdf") or response.content[:5] == b"%PDF-"
    if response.status_code != 200 or not es_pdf:
        if response.status_code != 200:
            health.http_error()
        stats.pdf_no_disponibles += 1
        logger.warning(
            f"Acta no disponible (HTTP {response.status_code}, {content_type or 'sin content-type'}): {acta_url}"
        )
        return

    if save_file(response.content, path):
        stats.pdf_descargados += 1
    else:
        stats.error(f"PDF no guardado: {path}")


def download_season(downloader: Downloader, season: str, jornadas: List[int],
                    category_filter: Optional[str], force: bool, with_pdf: bool) -> Stats:
    """Descubre las competiciones de la temporada y descarga HTML + PDF de cada jornada."""
    stats = Stats()

    competitions = discover_competitions(downloader, season)
    if not competitions:
        logger.warning(f"No se pudo descubrir la estructura de {season}. Usando ligas principales, grupo 0.")
        competitions = {(liga, "0"): set(SEXOS) for liga in FALLBACK_LIGAS}

    tareas = generate_urls(season, competitions, jornadas, category_filter)
    if not tareas:
        stats.error(f"No hay páginas que descargar (¿categoría '{category_filter}' inexistente?)")
        return stats
    logger.info(f"{len(tareas)} páginas de jornada a procesar")

    for i, tarea in enumerate(tareas, 1):
        folder = get_folder(season, tarea["category"], tarea["jornada"], tarea["sexo"])
        logger.info(
            f"[{i}/{len(tareas)}] {season} | {tarea['category']} | grupo {tarea['grupo']} | "
            f"jornada {tarea['jornada']} | {tarea['sexo']}"
        )

        html = download_jornada_html(downloader, tarea, folder / f"grupo_{tarea['grupo']}.html", force, stats)
        if html is None or not with_pdf:
            continue

        actas = extract_acta_links(html)
        if not actas:
            logger.info("  Sin actas en esta jornada")
            continue
        for acta_url, partido_id in actas:
            download_acta_pdf(downloader, acta_url, folder / f"acta_{partido_id}.pdf", force, stats)

    return stats


def log_summary(season: str, stats: Stats, log_path: Path) -> None:
    logger.info("=" * 60)
    logger.info(f"Resumen temporada {season}")
    logger.info("=" * 60)
    logger.info(f"  HTML descargados:       {stats.html_descargados}")
    logger.info(f"  HTML actualizados:      {stats.html_actualizados}")
    logger.info(f"  HTML sin cambios:       {stats.html_saltados}")
    logger.info(f"  PDF descargados:        {stats.pdf_descargados}")
    logger.info(f"  PDF ya existentes:      {stats.pdf_saltados}")
    logger.info(f"  PDF no disponibles:     {stats.pdf_no_disponibles}")
    logger.info(f"  Errores:                {len(stats.errores)}")
    logger.info(f"  Destino: {OUTPUT_DIR / season}")
    logger.info(f"  Log de errores: {log_path}")
    logger.info("=" * 60)


# ══════════════════════════════════════════
#  Punto de entrada
# ══════════════════════════════════════════


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Descargador de actas RFETM (HTML de jornadas + PDF de actas)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ejemplos:
  python src/actas-html-pdf/download_actas_from_rfetm.py --season 2025-2026
  python src/actas-html-pdf/download_actas_from_rfetm.py --season 2025-2026 --category divisio-honor --jornada 3
        """,
    )
    parser.add_argument("--content-dir", required=True, type=Path,
                        help="Directorio de descargas (<data_dir>/rfetm/content).")
    parser.add_argument("--season", required=True, metavar="YYYY-YYYY",
                        help="Temporada a descargar (ej: 2025-2026).")
    parser.add_argument("--category", choices=sorted(LIGA_MAPPING.values()),
                        help="Descargar solo esta categoría.")
    parser.add_argument("--jornada", type=int, action="append", choices=JORNADAS, metavar="N",
                        help="Descargar solo esta jornada (1-22). Se puede repetir.")
    parser.add_argument("--no-pdf", action="store_true",
                        help="Descargar solo los HTML, sin las actas PDF.")
    parser.add_argument("--force", action="store_true",
                        help="Vuelve a descargar aunque los archivos ya existan.")
    parser.add_argument("--delay", type=float, default=DEFAULT_DELAY,
                        help=f"Segundos mínimos entre peticiones (por defecto {DEFAULT_DELAY}).")
    parser.add_argument("--retries", type=int, default=DEFAULT_RETRIES,
                        help=f"Reintentos ante errores de red (por defecto {DEFAULT_RETRIES}).")
    parser.add_argument("--debug", action="store_true", help="Muestra mensajes de depuración.")
    args = parser.parse_args(argv)
    configure(args.content_dir)

    if args.debug:
        logging.getLogger().setLevel(logging.DEBUG)

    if not validate_season_format(args.season):
        logger.error(f"Formato de temporada incorrecto: '{args.season}'. Use YYYY-YYYY (ej: 2025-2026).")
        return 1

    log_path = setup_error_log(args.season)
    if args.force:
        logger.warning("MODO FUERZA: se sobrescribirán archivos existentes.")

    downloader = Downloader(delay=args.delay, retries=args.retries)
    try:
        stats = download_season(
            downloader,
            args.season,
            jornadas=sorted(set(args.jornada)) if args.jornada else JORNADAS,
            category_filter=args.category,
            force=args.force,
            with_pdf=not args.no_pdf,
        )
        log_summary(args.season, stats, log_path)
        return 1 if stats.errores else 0
    except KeyboardInterrupt:
        logger.warning("Descarga interrumpida por el usuario.")
        return 130
    finally:
        downloader.close()


if __name__ == "__main__":
    sys.exit(main())
