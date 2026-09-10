#!/usr/bin/env python3
"""
Сборка assets/bible/NRT из открытого JSONL (bibleapi/bibleapi-bibles-json).

Источник: https://github.com/bibleapi/bibleapi-bibles-json (nrt.json)
Формат источника — по одному стиху на строку (mongoimport).

Особенности источника:
- «1Kgs» содержит и 1 Kings, и 2 Kings (различать по полю book_name).
- «Jona» — книга Иона.

Запуск:
  python3 scripts/build_nrt_assets.py
  NRT_JSON=/path/to/nrt.json python3 scripts/build_nrt_assets.py
"""
from __future__ import annotations

import json
import os
import sys
import urllib.request
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_NRT = os.path.join(ROOT, "app/src/main/assets/bible/NRT")
NRT_URL = "https://raw.githubusercontent.com/bibleapi/bibleapi-bibles-json/master/nrt.json"
BIBLE_BY_PSALMS_BOOK = 19
BIBLE_BY_NRT = "https://bible.by/nrt/{book}/{chapter}/"

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

# Совпадает с BibleCanon.kt (nameRu)
BOOK_NAMES_RU: dict[str, str] = {
    "genesis": "Бытие",
    "exodus": "Исход",
    "leviticus": "Левит",
    "numbers": "Числа",
    "deuteronomy": "Второзаконие",
    "joshua": "Иисус Навин",
    "judges": "Судьи",
    "ruth": "Руфь",
    "1_samuel": "1-я Царств",
    "2_samuel": "2-я Царств",
    "1_kings": "3-я Царств",
    "2_kings": "4-я Царств",
    "1_chronicles": "1-я Паралипоменон",
    "2_chronicles": "2-я Паралипоменон",
    "ezra": "Ездра",
    "nehemiah": "Неемия",
    "esther": "Есфирь",
    "job": "Иов",
    "psalms": "Псалтирь",
    "proverbs": "Притчи",
    "ecclesiastes": "Екклесиаст",
    "song_of_solomon": "Песнь Песней",
    "isaiah": "Исаия",
    "jeremiah": "Иеремия",
    "lamentations": "Плач Иеремии",
    "ezekiel": "Иезекииль",
    "daniel": "Даниил",
    "hosea": "Осия",
    "joel": "Иоиль",
    "amos": "Амос",
    "obadiah": "Авдий",
    "jonah": "Иона",
    "micah": "Михей",
    "nahum": "Наум",
    "habakkuk": "Аввакум",
    "zephaniah": "Софония",
    "haggai": "Аггей",
    "zechariah": "Захария",
    "malachi": "Малахия",
    "matthew": "От Матфея",
    "mark": "От Марка",
    "luke": "От Луки",
    "john": "От Иоанна",
    "acts": "Деяния",
    "james": "Иакова",
    "1_peter": "1-е Петра",
    "2_peter": "2-е Петра",
    "1_john": "1-е Иоанна",
    "2_john": "2-е Иоанна",
    "3_john": "3-е Иоанна",
    "jude": "Иуды",
    "romans": "К Римлянам",
    "1_corinthians": "1-е Коринфянам",
    "2_corinthians": "2-е Коринфянам",
    "galatians": "К Галатам",
    "ephesians": "К Ефесянам",
    "philippians": "К Филиппийцам",
    "colossians": "К Колоссянам",
    "1_thessalonians": "1-е Фессалоникийцам",
    "2_thessalonians": "2-е Фессалоникийцам",
    "1_timothy": "1-е Тимофею",
    "2_timothy": "2-е Тимофею",
    "titus": "Титу",
    "philemon": "К Филимону",
    "hebrews": "К Евреям",
    "revelation": "Откровение",
}

SOURCE_BOOK_MAP: dict[str, str] = {
    "Gen": "genesis",
    "Exod": "exodus",
    "Lev": "leviticus",
    "Num": "numbers",
    "Deut": "deuteronomy",
    "Josh": "joshua",
    "Judg": "judges",
    "Ruth": "ruth",
    "1Sam": "1_samuel",
    "2Sam": "2_samuel",
    "1Chr": "1_chronicles",
    "2Chr": "2_chronicles",
    "Ezra": "ezra",
    "Neh": "nehemiah",
    "Esth": "esther",
    "Job": "job",
    "Ps": "psalms",
    "Prov": "proverbs",
    "Eccl": "ecclesiastes",
    "Song": "song_of_solomon",
    "Isa": "isaiah",
    "Jer": "jeremiah",
    "Lam": "lamentations",
    "Ezek": "ezekiel",
    "Dan": "daniel",
    "Hos": "hosea",
    "Joel": "joel",
    "Amos": "amos",
    "Obad": "obadiah",
    "Jona": "jonah",
    "Mic": "micah",
    "Nah": "nahum",
    "Hab": "habakkuk",
    "Zeph": "zephaniah",
    "Hag": "haggai",
    "Zech": "zechariah",
    "Mal": "malachi",
    "Matt": "matthew",
    "Mark": "mark",
    "Luke": "luke",
    "John": "john",
    "Acts": "acts",
    "Jas": "james",
    "1Pet": "1_peter",
    "2Pet": "2_peter",
    "1John": "1_john",
    "2John": "2_john",
    "3John": "3_john",
    "Jude": "jude",
    "Rom": "romans",
    "1Cor": "1_corinthians",
    "2Cor": "2_corinthians",
    "Gal": "galatians",
    "Eph": "ephesians",
    "Phil": "philippians",
    "Col": "colossians",
    "1Thess": "1_thessalonians",
    "2Thess": "2_thessalonians",
    "1Tim": "1_timothy",
    "2Tim": "2_timothy",
    "Titus": "titus",
    "Phlm": "philemon",
    "Heb": "hebrews",
    "Rev": "revelation",
}


def fetch_nrt_jsonl(dest: str) -> None:
    if os.path.isfile(dest):
        print(f"  Используем локальный файл: {dest}")
        return
    print(f"  Загрузка {NRT_URL} …")
    req = urllib.request.Request(
        NRT_URL,
        headers={"User-Agent": "BibleAppAssetBuilder/1.0"},
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        data = resp.read()
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as f:
        f.write(data)
    print(f"  Сохранено {len(data)} байт")


def resolve_book_id(row: dict) -> str | None:
    src_id = row["book_id"]
    if src_id == "1Kgs":
        name = row.get("book_name", "")
        if name == "1 Kings":
            return "1_kings"
        if name == "2 Kings":
            return "2_kings"
        return None
    return SOURCE_BOOK_MAP.get(src_id)


def fetch_psalm_from_bible_by(chapter: int) -> dict[int, str]:
    import re
    import time

    url = BIBLE_BY_NRT.format(book=BIBLE_BY_PSALMS_BOOK, chapter=chapter)
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "Mozilla/5.0 (compatible; BibleAppAssetBuilder/1.0)"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        html = resp.read().decode("utf-8", errors="replace")
    segments = re.split(r"<sup\s*>\s*(\d+)\s*</sup>", html)
    verses: dict[int, str] = {}
    for i in range(1, len(segments), 2):
        if i + 1 >= len(segments):
            break
        num = int(segments[i])
        text = re.sub(r"<[^>]+>", "", segments[i + 1])
        text = re.sub(r"\s+", " ", text).strip()
        if text:
            verses[num] = text
    if not verses:
        raise RuntimeError(f"Не удалось разобрать Псалом {chapter} с bible.by")
    time.sleep(0.35)
    return verses


def load_rows(path: str) -> dict[str, dict[int, dict[int, str]]]:
    books: dict[str, dict[int, dict[int, str]]] = defaultdict(lambda: defaultdict(dict))
    skipped = 0
    with open(path, encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            book_id = resolve_book_id(row)
            if not book_id:
                skipped += 1
                continue
            ch = int(row["chapter"])
            if book_id == "psalms":
                ch += 1
            v = int(row["verse"])
            text = row["text"].strip()
            books[book_id][ch][v] = text
    if skipped:
        print(f"  Пропущено строк без маппинга: {skipped}")

    psalms = books["psalms"]
    for ch in range(147, 151):
        if ch in psalms and psalms[ch]:
            continue
        print(f"  Дополнение Псалма {ch} с bible.by …")
        psalms[ch] = fetch_psalm_from_bible_by(ch)

    return books


def write_book(book_id: str, chapters_map: dict[int, dict[int, str]]) -> None:
    chapters = []
    for ch_num in sorted(chapters_map):
        verses_map = chapters_map[ch_num]
        verses = [
            {"number": v_num, "text": verses_map[v_num]}
            for v_num in sorted(verses_map)
        ]
        chapters.append({"number": ch_num, "verses": verses})
    payload = {
        "translation": "NRT",
        "book": {
            "id": book_id,
            "name": BOOK_NAMES_RU[book_id],
            "chapters": chapters,
        },
    }
    os.makedirs(OUT_NRT, exist_ok=True)
    out_path = os.path.join(OUT_NRT, f"{book_id}.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print(f"  OK {book_id}.json ({len(chapters)} гл., {sum(len(c['verses']) for c in chapters)} ст.)")


def main() -> None:
    cache = os.environ.get("NRT_JSON") or os.path.join(ROOT, "scripts/.cache/nrt.jsonl")
    fetch_nrt_jsonl(cache)
    books = load_rows(cache)

    missing = [bid for bid in BOOK_IDS if bid not in books]
    if missing:
        print("ОШИБКА: нет книг:", ", ".join(missing))
        sys.exit(1)

    for book_id in BOOK_IDS:
        write_book(book_id, books[book_id])

    # Быстрая проверка отличия от синодального (Ин 3:16)
    syn_path = os.path.join(ROOT, "app/src/main/assets/bible/SYN/john.json")
    nrt_path = os.path.join(OUT_NRT, "john.json")
    if os.path.isfile(syn_path) and os.path.isfile(nrt_path):
        with open(syn_path, encoding="utf-8") as f:
            syn = json.load(f)
        with open(nrt_path, encoding="utf-8") as f:
            nrt = json.load(f)

        def v16(data: dict) -> str:
            for ch in data["book"]["chapters"]:
                if ch["number"] == 3:
                    for verse in ch["verses"]:
                        if verse["number"] == 16:
                            return verse["text"]
            return ""

        s16, n16 = v16(syn), v16(nrt)
        print("\nПроверка Иоанна 3:16:")
        print("  SYN:", s16[:90] + ("…" if len(s16) > 90 else ""))
        print("  NRT:", n16[:90] + ("…" if len(n16) > 90 else ""))
        print("  Совпадает с SYN:", s16 == n16)

    print(f"\nГотово: {len(BOOK_IDS)} книг в {OUT_NRT}")


if __name__ == "__main__":
    main()
