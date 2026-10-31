# -*- coding: utf-8 -*-
"""Загружает фото NASA/ESA для раздела «Детям — Космос» в res/drawable-nodpi.

Запуск:
    python3 scripts/fetch_kids_space_assets.py            # недостающие файлы
    python3 scripts/fetch_kids_space_assets.py --force    # перекачать всё

Файлы: kids_space_<slug>.jpg|png (превью 1280 px).
Источник — Wikimedia Commons. Почти всё — public domain снимки NASA/ESA/Hubble/JWST;
несколько файлов (чёрная дыра M87) имеют лицензию CC — источник печатается в отчёте
и сохраняется в scripts/kids_space_sources.json.
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.join(os.path.dirname(__file__), "..")
DRAW = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
SOURCES = os.path.join(ROOT, "scripts", "kids_space_sources.json")
UA = "BibleKidsApp/1.0 (educational; kids space images)"
REFERER = "https://commons.wikimedia.org/"
THUMB_WIDTH = 1280

SKIP_SUBSTR = (
    "diagram",
    "map ",
    "logo",
    "icon ",
    "chart",
    "poster",
    "cover",
    "stamp",
    "annotated",
    "comparison",
    "model by",
    "students",
    "transit",
)

# (slug, точный заголовок File:, поисковый запрос на случай, если файл не найден)
ITEMS: list[tuple[str, str | None, str]] = [
    ("solntse", "File:The Sun by the Atmospheric Imaging Assembly of NASA's Solar Dynamics Observatory - 20100819.jpg", "Sun full disk SDO"),
    ("merkuriy", "File:Mercury in true color.jpg", "Mercury planet true color"),
    ("venera", "File:Venus globe.jpg", "Venus globe"),
    ("zemlya", "File:The Blue Marble (remastered).jpg", "Earth Blue Marble Apollo 17"),
    ("luna", "File:Full Moon (5836482263).jpg", "Full Moon disk"),
    ("mars", "File:PlanetMars-SyrtisMajor-VikingOrbiter-1980.jpg", "Mars Viking Orbiter global color"),
    ("yupiter", "File:Jupiter in true color.jpg", "Jupiter in true color"),
    ("saturn", "File:Saturn from Cassini Orbiter (2004-10-06).jpg", "Saturn from Cassini Orbiter"),
    ("uran", "File:Uranus true colour.jpg", "Uranus true colour Voyager"),
    ("neptun", "File:Neptune Full.jpg", "Neptune full disk Voyager 2"),
    ("pluton", "File:Pluto in True Color - High-Res.jpg", "Pluto in true color New Horizons"),
    ("kometa", "File:Comet Lovejoy Near the Big Dipper.png", "Comet Lovejoy tail"),
    ("zemlyarize", "File:NASA-Apollo8-Dec24-Earthrise.jpg", "Earthrise Apollo 8"),
    ("pale_blue_dot", "File:Pale Blue Dot.png", "Pale Blue Dot Voyager 1"),
    ("milechnyy_put", "File:Milky Way IR Spitzer.jpg", "Milky Way infrared Spitzer"),
    ("tumannost_oriona", "File:Orion Nebula - Hubble 2006 mosaic 18000.jpg", "Orion Nebula Hubble mosaic"),
    ("krabovodnaya", "File:Crab Nebula.jpg", "Crab Nebula Hubble"),
    ("tumannost_koltso", "File:Hubble image of the Ring Nebula (Messier 57).jpg", "Ring Nebula Hubble Messier 57"),
    ("glaz_boga", "File:Cat's Eye Nebula (3882).jpg", "Cat's Eye Nebula Hubble"),
    ("stolpy_tvoreniya", "File:Pillars of Creation (NIRCam Image).png", "Pillars of Creation NIRCam Webb"),
    ("plejadi", "File:Pleiades large.jpg", "Pleiades M45 star cluster"),
    ("andromeda", "File:Andromeda galaxy.jpg", "Andromeda galaxy GALEX NASA"),
    ("vihr", "File:Messier51 sRGB.jpg", "Whirlpool Galaxy M51"),
    ("sombrero", "File:M104 ngc4594 sombrero galaxy hi-res.jpg", "Sombrero Galaxy M104 Hubble"),
    ("magellanovy_oblaka", "File:Ssc2006-17b.jpg", "Large Magellanic Cloud Spitzer"),
    ("glubokoe_pole", "File:NASA-HS201427a-HubbleUltraDeepField2014-20140603.jpg", "Hubble Ultra Deep Field 2014"),
    ("chornaya_dyra", "File:Black hole - Messier 87.jpg", "Black hole Messier 87 event horizon"),
]


def api_get(params: dict[str, str]) -> dict:
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    for attempt in range(6):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code in (429, 503) and attempt < 5:
                time.sleep(10.0 * (attempt + 1))
                continue
            raise


def thumb_url(title: str, width: int = THUMB_WIDTH) -> str:
    j = api_get(
        {
            "action": "query",
            "titles": title,
            "prop": "imageinfo",
            "iiprop": "url|extmetadata",
            "iiurlwidth": str(width),
            "format": "json",
        }
    )
    page = next(iter(j["query"]["pages"].values()))
    if "missing" in page:
        raise FileNotFoundError(title)
    ii = page["imageinfo"][0]
    return ii.get("thumburl") or ii["url"]


def license_of(title: str) -> str:
    try:
        j = api_get(
            {
                "action": "query",
                "titles": title,
                "prop": "imageinfo",
                "iiprop": "extmetadata",
                "format": "json",
            }
        )
        page = next(iter(j["query"]["pages"].values()))
        md = page["imageinfo"][0]["extmetadata"]
        lic = md.get("LicenseShortName", {}).get("value", "")
        artist = md.get("Artist", {}).get("value", "")
        artist = "".join(c for c in artist if c not in "<>").strip()
        return f"{lic} | {artist[:60]}"
    except Exception:
        return "?"


def search_title(query: str) -> str | None:
    j = api_get(
        {
            "action": "query",
            "list": "search",
            "srsearch": query,
            "srnamespace": "6",
            "format": "json",
            "srlimit": "20",
        }
    )
    for it in j.get("query", {}).get("search", []):
        t = it["title"]
        if not t.startswith("File:"):
            continue
        tl = t.lower()
        if not tl.endswith((".jpg", ".jpeg", ".png")):
            continue
        if any(s in tl for s in SKIP_SUBSTR):
            continue
        return t
    return None


def download(url: str, dest: str) -> None:
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    tmp = dest + ".part"
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": REFERER})
            with urllib.request.urlopen(req, timeout=180) as r, open(tmp, "wb") as f:
                f.write(r.read())
            os.replace(tmp, dest)
            return
        except urllib.error.HTTPError as e:
            if e.code in (429, 503) and attempt < 4:
                time.sleep(15.0 * (attempt + 1))
                continue
            raise
        except Exception:
            if os.path.exists(tmp):
                os.remove(tmp)
            raise


def normalize_jpeg(path: str) -> None:
    """Превью с Commons иногда приходит PNG (бывают и по 5 МБ). Переводим всё в JPEG <= 1280 px."""
    try:
        from PIL import Image
    except ImportError:
        return
    try:
        with Image.open(path) as im:
            too_big = os.path.getsize(path) > 900_000
            if im.format == "JPEG" and not too_big:
                return
            im = im.convert("RGB")
            im.thumbnail((THUMB_WIDTH, THUMB_WIDTH))
            im.save(path, "JPEG", quality=82, optimize=True, progressive=True)
            print("  normalized", os.path.basename(path), f"{os.path.getsize(path) // 1024}K")
    except Exception as e:
        print("  normalize_failed", os.path.basename(path), e)


def grab(slug: str, title: str | None, query: str, force: bool) -> dict | None:
    existing = [
        p
        for p in (f"{slug}.jpg", f"{slug}.png")
        if os.path.isfile(os.path.join(DRAW, f"kids_space_{p}"))
    ]
    if existing and not force:
        print("skip", slug)
        return None
    chosen = title
    url = ""
    if chosen:
        try:
            url = thumb_url(chosen)
        except FileNotFoundError:
            print("  exact missing, fallback search:", chosen)
            chosen = None
        except Exception as e:
            print("  exact failed:", chosen, e)
            chosen = None
    if not chosen:
        time.sleep(2.0)
        chosen = search_title(query)
        if not chosen:
            print("NO_SEARCH", slug, query)
            return None
        try:
            url = thumb_url(chosen)
        except Exception as e:
            print("NO_THUMB", slug, chosen, e)
            return None
    ext = ".png" if url.lower().endswith(".png") else ".jpg"
    dest = os.path.join(DRAW, f"kids_space_{slug}{ext}")
    info = {"file": os.path.basename(dest), "title": chosen, "license": license_of(chosen)}
    time.sleep(2.0)
    try:
        download(url, dest)
    except Exception as e:
        print("FAIL_DL", slug, e)
        return None
    size = os.path.getsize(dest)
    if size < 4000:
        os.remove(dest)
        print("TOO_SMALL", slug, size)
        return None
    print("ok", slug, chosen[:60], f"{size // 1024}K", info["license"])
    return info


def main() -> None:
    argv = sys.argv[1:]
    force = "--force" in argv
    only = {a for a in argv if not a.startswith("--")}
    manifest = {}
    if os.path.isfile(SOURCES):
        with open(SOURCES, encoding="utf-8") as f:
            manifest = json.load(f)
    for slug, title, query in ITEMS:
        if only and slug not in only:
            continue
        info = grab(slug, title, query, force)
        if info:
            manifest[slug] = info
    with open(SOURCES, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    for name in sorted(os.listdir(DRAW)):
        if name.startswith("kids_space_") and name.endswith((".jpg", ".png")):
            normalize_jpeg(os.path.join(DRAW, name))
    print("done")


if __name__ == "__main__":
    main()
