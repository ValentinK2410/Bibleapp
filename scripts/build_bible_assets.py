#!/usr/bin/env python3
"""
Загружает все 66 книг: SYN (jsonbible RST, GitHub — без жёсткого rate limit)
и WEB (bible-api.com). Для WEB между главами пауза и повтор при 429.

Запуск:
  python3 scripts/build_bible_assets.py           # всё подряд
  SYN_ONLY=1 python3 scripts/build_bible_assets.py  # только синодальный
  WEB_ONLY=1 python3 scripts/build_bible_assets.py    # только WEB (медленно)
  WEB_SLEEP=2.0 WEB_ONLY=1 python3 scripts/build_bible_assets.py  # пауза между главами (сек)
"""
from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_WEB = os.path.join(ROOT, "app/src/main/assets/bible/WEB")
OUT_SYN = os.path.join(ROOT, "app/src/main/assets/bible/SYN")
RST_BASE = "https://raw.githubusercontent.com/jsonbible/rst/master"
WEB_TMPL = "https://bible-api.com/{}+{}?translation=web"

BOOK_IDS: list[str] = [
    "genesis", "exodus", "leviticus", "numbers", "deuteronomy",
    "joshua", "judges", "ruth", "1_samuel", "2_samuel", "1_kings", "2_kings",
    "1_chronicles", "2_chronicles", "ezra", "nehemiah", "esther",
    "job", "psalms", "proverbs", "ecclesiastes", "song_of_solomon",
    "isaiah", "jeremiah", "lamentations", "ezekiel", "daniel",
    "hosea", "joel", "amos", "obadiah", "jonah", "micah", "nahum",
    "habakkuk", "zephaniah", "haggai", "zechariah", "malachi",
    "matthew", "mark", "luke", "john", "acts",
    "james", "1_peter", "2_peter", "1_john", "2_john", "3_john", "jude",
    "romans", "1_corinthians", "2_corinthians", "galatians", "ephesians",
    "philippians", "colossians", "1_thessalonians", "2_thessalonians",
    "1_timothy", "2_timothy", "titus", "philemon", "hebrews", "revelation",
]


def web_slug(book_id: str) -> str:
    if book_id == "song_of_solomon":
        return "songofsolomon"
    return book_id.replace("_", "")


def fetch(url: str) -> str | None:
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "Mozilla/5.0 (compatible; BibleAppAssetBuilder/1.1)"},
    )
    last_err: Exception | None = None
    for attempt in range(8):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if e.code == 429:
                wait = min(120, 15 * (attempt + 1))
                print(f"    429, sleep {wait}s …")
                time.sleep(wait)
                continue
            raise
        except (urllib.error.URLError, TimeoutError) as e:
            last_err = e
            time.sleep(5 * (attempt + 1))
    if last_err:
        raise last_err
    return None


def rst_chapters(book_num: int) -> tuple[str, list[dict]]:
    chapters: list[dict] = []
    book_name = ""
    ch = 1
    while True:
        url = f"{RST_BASE}/{book_num}/{ch}.json"
        raw = fetch(url)
        if not raw:
            break
        data = json.loads(raw)
        book_name = data.get("book_name", book_name)
        verses = [
            {"number": x["verse"], "text": x["text"].strip()}
            for x in data["verses"]
        ]
        chapters.append({"number": data["chapter"], "verses": verses})
        ch += 1
        time.sleep(0.05)
    return book_name, chapters


def web_sleep_seconds() -> float:
    try:
        return float(os.environ.get("WEB_SLEEP", "1.2"))
    except ValueError:
        return 1.2


def web_chapters(slug: str) -> tuple[str, list[dict]]:
    delay = web_sleep_seconds()
    chapters: list[dict] = []
    book_name = ""
    ch = 1
    while True:
        url = WEB_TMPL.format(slug, ch)
        raw = fetch(url)
        if not raw:
            break
        data = json.loads(raw)
        if data.get("error"):
            break
        vlist = data.get("verses") or []
        if not vlist:
            break
        book_name = vlist[0].get("book_name", book_name)
        verses = [
            {"number": x["verse"], "text": x["text"].strip().replace("\n", " ")}
            for x in vlist
        ]
        chapters.append({"number": vlist[0]["chapter"], "verses": verses})
        ch += 1
        time.sleep(delay)
    return book_name, chapters


def write_book(out_dir: str, translation: str, book_id: str, name: str, chapters: list[dict]) -> None:
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, f"{book_id}.json")
    payload = {
        "translation": translation,
        "book": {
            "id": book_id,
            "name": name,
            "chapters": chapters,
        },
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print(f"  OK {path} ({len(chapters)} ch)")


def main() -> None:
    assert len(BOOK_IDS) == 66
    syn_only = os.environ.get("SYN_ONLY") == "1"
    web_only = os.environ.get("WEB_ONLY") == "1"

    for i, book_id in enumerate(BOOK_IDS):
        rst_num = i + 1
        if not web_only:
            print(f"[{i + 1}/66] RST {book_id} …")
            name_ru, ch_ru = rst_chapters(rst_num)
            if not ch_ru:
                print(f"  FAIL RST {book_id}")
                continue
            write_book(OUT_SYN, "RST", book_id, name_ru, ch_ru)

        if not syn_only:
            slug = web_slug(book_id)
            out_path = os.path.join(OUT_WEB, f"{book_id}.json")
            if os.path.isfile(out_path):
                try:
                    with open(out_path, encoding="utf-8") as f:
                        existing = json.load(f)
                    nch = len(existing.get("book", {}).get("chapters") or [])
                    if nch > 0:
                        print(f"  WEB {slug} … skip (уже есть, {nch} гл.)")
                        continue
                except (OSError, json.JSONDecodeError, KeyError):
                    pass
            print(f"  WEB {slug} …")
            name_en, ch_en = web_chapters(slug)
            if not ch_en:
                print(f"  FAIL WEB {book_id}")
                continue
            write_book(OUT_WEB, "WEB", book_id, name_en, ch_en)

    print("Готово.")


if __name__ == "__main__":
    main()
