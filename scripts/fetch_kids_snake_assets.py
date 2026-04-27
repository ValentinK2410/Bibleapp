# -*- coding: utf-8 -*-
"""JPEG змей (Wikimedia Commons) → app/src/main/res/drawable-nodpi/kids_snake_*.jpg"""
from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.join(os.path.dirname(__file__), "..")
DRAW = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
UA = "BibleKidsApp/1.0 (educational; Android Bible kids section)"
REFERER = "https://commons.wikimedia.org/"

# slug -> точное имя файла на Commons
IMAGES: dict[str, str] = {
    "uzh": "File:Grass snake (Natrix natrix).jpg",
    "gadyuka": "File:Vipera berus.jpg",
    "medyanka": "File:Coronella austriaca.jpg",
    "poloz": "File:Coluber constrictor priapus.jpg",
    "vodyanoy_uzh": "File:Natrix tessellata - dice snake.jpg",
    "kobra": "File:Indian cobra.jpg",
    "korolevskaya_kobra": "File:King Cobra.jpg",
    "gremuchnik": "File:Western diamondback rattlesnake.jpg",
    "piton": "File:Python molurus.jpg",
    "udav": "File:Boa constrictor.jpg",
    "anakonda": "File:Eunectes murinus.jpg",
    "chernaya_mamba": "File:Black mamba.jpg",
}


def api_thumb_url(title: str, width: int = 1280) -> str:
    q = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(
        {
            "action": "query",
            "titles": title,
            "prop": "imageinfo",
            "iiprop": "url",
            "iiurlwidth": str(width),
            "format": "json",
        }
    )
    req = urllib.request.Request(q, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        j = json.load(r)
    p = next(iter(j["query"]["pages"].values()))
    if "missing" in p:
        raise FileNotFoundError(title)
    ii = p["imageinfo"][0]
    return ii.get("thumburl") or ii["url"]


def download(url: str, dest: str) -> None:
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    req = urllib.request.Request(
        url,
        headers={"User-Agent": UA, "Referer": REFERER},
    )
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
                f.write(r.read())
            time.sleep(1.2)
            return
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < 3:
                time.sleep(8.0 * (attempt + 1))
                continue
            raise


def main() -> None:
    for slug, title in IMAGES.items():
        dest = os.path.join(DRAW, f"kids_snake_{slug}.jpg")
        if os.path.isfile(dest) and os.path.getsize(dest) > 5000:
            print("skip", slug)
            continue
        try:
            u = api_thumb_url(title)
        except Exception as e:
            print("FAIL api", slug, title, e)
            continue
        time.sleep(0.5)
        print("img", slug, u[:88])
        try:
            download(u, dest)
        except Exception as e:
            print("FAIL dl", slug, e)
    print("done")


if __name__ == "__main__":
    main()
