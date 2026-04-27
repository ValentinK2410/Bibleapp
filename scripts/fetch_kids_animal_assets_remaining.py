# -*- coding: utf-8 -*-
"""Докачка последних фото/звуков при лимите 429 (большие паузы между запросами)."""
from __future__ import annotations

import json
import os
import time
import urllib.parse
import urllib.request

ROOT = os.path.join(os.path.dirname(__file__), "..")
DRAW = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw")
UA = "BibleKidsApp/1.0 (educational)"
REF = "https://commons.wikimedia.org/"

IMAGES: dict[str, str] = {
    "yozh": "File:Erinaceus europaeus.jpg",
    "krot": "File:Talpa europaea.jpg",
    "zmeya": "File:Grass snake (Natrix natrix).jpg",
    "cherepakha": "File:Green sea turtle.jpg",
    "yashcheritsa": "File:Lacerta viridis.jpg",
    "lyagushka": "File:Rana temporaria.jpg",
    "delfin": "File:Common bottlenose dolphin.jpg",
    "kit": "File:Humpback whale.jpg",
}

SOUNDS: dict[str, str] = {
    "kids_anml_cow": "File:Mudchute cow 1.ogg",
    "kids_anml_horse": "File:Wiehern.ogg",
    "kids_anml_pig": "File:Pig grunt - Erdie.ogg",
    "kids_anml_sheep": "File:Sheep bleating.ogg",
    "kids_anml_goat": "File:Herd of goats bleating.ogg",
    "kids_anml_deer": "File:Red Deer (Cervus elaphus) (W1CDR0001424 BD3).ogg",
    "kids_anml_hedgehog": "File:Hedgehog O.ogg",
}


def thumb(title: str) -> str:
    q = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(
        {
            "action": "query",
            "titles": title,
            "prop": "imageinfo",
            "iiprop": "url",
            "iiurlwidth": "1024",
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


def orig(title: str) -> str:
    q = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(
        {
            "action": "query",
            "titles": title,
            "prop": "imageinfo",
            "iiprop": "url",
            "format": "json",
        }
    )
    req = urllib.request.Request(q, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        j = json.load(r)
    p = next(iter(j["query"]["pages"].values()))
    if "missing" in p:
        raise FileNotFoundError(title)
    return p["imageinfo"][0]["url"]


def grab(url: str, dest: str) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": REF})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
                f.write(r.read())
            return
        except Exception as e:
            print("retry", attempt, e)
            time.sleep(30 * (attempt + 1))


def main() -> None:
    for slug, title in IMAGES.items():
        dest = os.path.join(DRAW, f"kids_anml_{slug}.jpg")
        if os.path.isfile(dest) and os.path.getsize(dest) > 5000:
            print("skip", slug)
            continue
        u = thumb(title)
        print("img", slug)
        grab(u, dest)
        time.sleep(22)

    for base, title in SOUNDS.items():
        dest = os.path.join(RAW, f"{base}.ogg")
        if os.path.isfile(dest) and os.path.getsize(dest) > 500:
            print("skip snd", base)
            continue
        u = orig(title)
        print("snd", base)
        grab(u, dest)
        time.sleep(22)
    print("done")


if __name__ == "__main__":
    main()
