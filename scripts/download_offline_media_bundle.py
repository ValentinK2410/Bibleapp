#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Скачивание озвучек Библии и Корана в структуру, совместимую с приложением
(com.example.bible.sqlite), чтобы перенести файлы на устройство (adb push).

Важно:
  • Объём — многие ГБ; в Git обычно не коммитят. Для вшивания в APK без сети скопируйте
    дерево bible_audio в app/src/main/assets/bible_audio/ (те же пути, что в
    BibleAudio.kt: bible_audio/<narratorId>/<book>_<chapter>.mp3).
  • Права на распространение озвучек — у правообладателей источников (4bbl.ru,
    WordProject, Mechon Mamre, alquran.cloud и т.д.). Скрипт только повторяет
    публичные URL, как в BibleAudio.kt / QuranAyahAudioApi.
  • Словари, комментарии, справочники «Изучение» кэшируются приложением из
    studybible.ru и SQLite (study_content.db). Их массовая выгрузка здесь не
    делается — прогрейте кэш в приложении и при необходимости снимите копию
    через «Поделиться приложением» / резервную копию (study_offline).

Пример после загрузки (пакет приложения из build.gradle):
  adb push offline_media_bundle/files/bible_audio \\
    /data/data/com.example.bible.sqlite/files/bible_audio
  adb push offline_media_bundle/files/quran_audio \\
    /data/data/com.example.bible.sqlite/files/quran_audio

Запуск:
  python3 scripts/download_offline_media_bundle.py --out ./offline_media_bundle
  python3 scripts/download_offline_media_bundle.py --quran-only --out ./q
  python3 scripts/download_offline_media_bundle.py --bible-only --narrators bondarenko,web
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Callable, Iterable, List, Optional, Sequence, Tuple

# Совпадает с BibleAudio.kt
AUDIO_UA = (
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
)
QURAN_API_UA = "BibleApp/1.0 (Android; QuranAyahOfflineBundle/1.0)"

# (id, urlFolder) — 4bbl.ru; как BibleAudioNarrators.all
NARRATORS_4BBL: List[Tuple[str, str]] = [
    ("bondarenko", "syn-bondarenko"),
    ("kozlov", "syn-kozlov"),
    ("efimov", "syn-efimov"),
    ("jbl", "syn-jbl"),
    ("new-russian", "new-russian"),
    ("rbo", "rbo-orgin"),
    ("bti", "bti-prozorovsky"),
]

# Канон: id, chapters — порядок как BibleCanon.allBooks (нумерация 4bbl = index+1)
CANON: List[Tuple[str, int]] = [
    ("genesis", 50),
    ("exodus", 40),
    ("leviticus", 27),
    ("numbers", 36),
    ("deuteronomy", 34),
    ("joshua", 24),
    ("judges", 21),
    ("ruth", 4),
    ("1_samuel", 31),
    ("2_samuel", 24),
    ("1_kings", 22),
    ("2_kings", 25),
    ("1_chronicles", 29),
    ("2_chronicles", 36),
    ("ezra", 10),
    ("nehemiah", 13),
    ("esther", 10),
    ("job", 42),
    ("psalms", 150),
    ("proverbs", 31),
    ("ecclesiastes", 12),
    ("song_of_solomon", 8),
    ("isaiah", 66),
    ("jeremiah", 52),
    ("lamentations", 5),
    ("ezekiel", 48),
    ("daniel", 12),
    ("hosea", 14),
    ("joel", 3),
    ("amos", 9),
    ("obadiah", 1),
    ("jonah", 4),
    ("micah", 7),
    ("nahum", 3),
    ("habakkuk", 3),
    ("zephaniah", 3),
    ("haggai", 2),
    ("zechariah", 14),
    ("malachi", 4),
    ("matthew", 28),
    ("mark", 16),
    ("luke", 24),
    ("john", 21),
    ("acts", 28),
    ("james", 5),
    ("1_peter", 5),
    ("2_peter", 3),
    ("1_john", 5),
    ("2_john", 1),
    ("3_john", 1),
    ("jude", 1),
    ("romans", 16),
    ("1_corinthians", 16),
    ("2_corinthians", 13),
    ("galatians", 6),
    ("ephesians", 6),
    ("philippians", 4),
    ("colossians", 4),
    ("1_thessalonians", 5),
    ("2_thessalonians", 3),
    ("1_timothy", 6),
    ("2_timothy", 4),
    ("titus", 3),
    ("philemon", 1),
    ("hebrews", 13),
    ("revelation", 22),
]

OT_IDS = {b[0] for b in CANON[:39]}
NT_IDS = {b[0] for b in CANON[39:]}

# WordProject 1..66 — BibleAudio.kt WORDPROJECT_BOOK_NUMBER
_OT = [b[0] for b in CANON[:39]]
_NT_WP = [
    "matthew", "mark", "luke", "john", "acts",
    "romans", "1_corinthians", "2_corinthians", "galatians", "ephesians",
    "philippians", "colossians", "1_thessalonians", "2_thessalonians",
    "1_timothy", "2_timothy", "titus", "philemon", "hebrews",
    "james", "1_peter", "2_peter", "1_john", "2_john", "3_john", "jude",
    "revelation",
]
WORDPROJECT_BOOK_NUMBER = {bid: i + 1 for i, bid in enumerate(_OT + _NT_WP)}

# Mechon Mamre — BibleAudio.kt MECHON_BOOK_PREFIX
MECHON_BOOK_PREFIX = {
    "genesis": "01", "exodus": "02", "leviticus": "03", "numbers": "04", "deuteronomy": "05",
    "joshua": "06", "judges": "07", "1_samuel": "08a", "2_samuel": "08b",
    "1_kings": "09a", "2_kings": "09b", "isaiah": "10", "jeremiah": "11", "ezekiel": "12",
    "hosea": "13", "joel": "14", "amos": "15", "obadiah": "16", "jonah": "17",
    "micah": "18", "nahum": "19", "habakkuk": "20", "zephaniah": "21", "haggai": "22",
    "zechariah": "23", "malachi": "24", "1_chronicles": "25a", "2_chronicles": "25b",
    "psalms": "26", "job": "27", "proverbs": "28", "ruth": "29", "song_of_solomon": "30",
    "ecclesiastes": "31", "lamentations": "32", "esther": "33", "daniel": "34",
    "ezra": "35a", "nehemiah": "35b",
}

CANON_GRID_BOOK_NUMBER = {bid: i + 1 for i, (bid, _) in enumerate(CANON)}


def mechon_psalms_chapter_suffix(chapter: int) -> str:
    if chapter <= 99:
        return f"{chapter:02d}"
    if chapter == 150:
        return "f0"
    idx = chapter - 100
    letter = chr(ord("a") + idx // 10)
    digit = idx % 10
    return f"{letter}{digit}"


def book_audio_number_4bbl(book_id: str) -> str:
    n = CANON_GRID_BOOK_NUMBER.get(book_id)
    return f"{n:02d}" if n else ""


def url_4bbl(folder: str, book_num: str, chapter: int) -> str:
    ch = f"{chapter:02d}"
    return f"https://4bbl.ru/data/{folder}/{book_num}/{ch}.mp3"


def url_web(book_id: str, chapter: int) -> Optional[str]:
    n = WORDPROJECT_BOOK_NUMBER.get(book_id)
    if n is None:
        return None
    return f"https://www.wordpocket.org/bibles/app/audio/1/{n}/{chapter}.mp3"


def url_hebrew(book_id: str, chapter: int) -> Optional[str]:
    if book_id not in OT_IDS:
        return None
    prefix = MECHON_BOOK_PREFIX.get(book_id)
    if not prefix:
        return None
    body = mechon_psalms_chapter_suffix(chapter) if book_id == "psalms" else f"{chapter:02d}"
    return f"https://www.mechon-mamre.org/mp3/t{prefix}{body}.mp3"


def url_greek(book_id: str, chapter: int) -> Optional[str]:
    if book_id not in NT_IDS:
        return None
    n = WORDPROJECT_BOOK_NUMBER.get(book_id)
    if n is None or n < 40:
        return None
    return f"https://www.wordproaudio.net/bibles/app/audio/58/{n}/{chapter}.mp3"


def download_one(
    url: str,
    dest: Path,
    referer: Optional[str] = None,
    min_bytes: int = 1024,
    dry_run: bool = False,
) -> bool:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.is_file() and dest.stat().st_size > min_bytes:
        return True
    if dry_run:
        print(f"[dry-run] {url} -> {dest}")
        return True
    req = urllib.request.Request(url, headers={"User-Agent": AUDIO_UA, "Accept": "*/*"})
    if referer:
        req.add_header("Referer", referer)
    if "wordpocket.org" in url:
        req.add_header("Referer", "https://www.wordpocket.org/")
    if "wordproaudio.net" in url:
        req.add_header("Referer", "https://www.wordproaudio.net/")
    if "mechon-mamre.org" in url:
        req.add_header("Referer", "https://www.mechon-mamre.org/")
    tmp = dest.with_suffix(dest.suffix + ".part")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            if resp.status != 200:
                return False
            data = resp.read()
        if len(data) <= min_bytes:
            return False
        tmp.write_bytes(data)
        tmp.replace(dest)
        return True
    except (urllib.error.HTTPError, urllib.error.URLError, OSError):
        try:
            if tmp.is_file():
                tmp.unlink()
        except OSError:
            pass
        return False


def iter_bible_jobs(
    narrators: Sequence[str],
) -> Iterable[Tuple[str, str, int, str, Optional[str]]]:
    """yields: narrator_id, book_id, chapter, dest_rel_path, referer_hint"""
    want = {n.strip().lower() for n in narrators}

    def emit_4bbl(nid: str, folder: str) -> Iterable[Tuple[str, str, int, str, Optional[str]]]:
        for book_id, nch in CANON:
            bn = book_audio_number_4bbl(book_id)
            if not bn:
                continue
            for ch in range(1, nch + 1):
                rel = f"files/bible_audio/{nid}/{bn}_{ch:02d}.mp3"
                yield nid, book_id, ch, rel, None

    for nid, folder in NARRATORS_4BBL:
        if nid not in want:
            continue
        yield from emit_4bbl(nid, folder)

    if "web" in want:
        for book_id, nch in CANON:
            wp = WORDPROJECT_BOOK_NUMBER.get(book_id)
            if wp is None:
                continue
            bn = f"{wp:02d}"
            for ch in range(1, nch + 1):
                rel = f"files/bible_audio/web/{bn}_{ch:02d}.mp3"
                yield "web", book_id, ch, rel, None

    if "hebrew-ot" in want:
        for book_id, nch in CANON:
            if book_id not in OT_IDS:
                continue
            bn = book_audio_number_4bbl(book_id)
            if not bn:
                continue
            for ch in range(1, nch + 1):
                rel = f"files/bible_audio/hebrew-ot/{bn}_{ch:02d}.mp3"
                yield "hebrew-ot", book_id, ch, rel, None

    if "greek-nt" in want:
        for book_id, nch in CANON:
            if book_id not in NT_IDS:
                continue
            wp = WORDPROJECT_BOOK_NUMBER.get(book_id)
            if wp is None or wp < 40:
                continue
            bn = f"{wp:02d}"
            for ch in range(1, nch + 1):
                rel = f"files/bible_audio/greek-nt/{bn}_{ch:02d}.mp3"
                yield "greek-nt", book_id, ch, rel, None


def build_bible_url(narrator_id: str, book_id: str, chapter: int) -> Optional[str]:
    if narrator_id == "web":
        return url_web(book_id, chapter)
    if narrator_id == "hebrew-ot":
        return url_hebrew(book_id, chapter)
    if narrator_id == "greek-nt":
        return url_greek(book_id, chapter)
    folder = next((f for n, f in NARRATORS_4BBL if n == narrator_id), None)
    if not folder:
        return None
    bn = book_audio_number_4bbl(book_id)
    if not bn:
        return None
    return url_4bbl(folder, bn, chapter)


def fetch_quran_ayah_urls(surah: int, ayah: int) -> List[str]:
    url = f"https://api.alquran.cloud/v1/ayah/{surah}:{ayah}/ar.alafasy"
    req = urllib.request.Request(
        url,
        headers={"User-Agent": QURAN_API_UA, "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            root = json.loads(resp.read().decode("utf-8"))
    except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError, OSError):
        return []
    if root.get("code") != 200:
        return []
    data = root.get("data") or {}
    out: List[str] = []
    a = (data.get("audio") or "").strip()
    if a:
        out.append(a)
    for x in data.get("audioSecondary") or []:
        s = str(x).strip()
        if s:
            out.append(s)
    return out


def download_quran_ayah(out_root: Path, surah: int, ayah: int, dry_run: bool) -> bool:
    dest = out_root / "files" / "quran_audio" / f"alafasy_{surah}_{ayah}.mp3"
    if dest.is_file() and dest.stat().st_size > 512:
        return True
    if dry_run:
        print(f"[dry-run] quran {surah}:{ayah} -> {dest}")
        return True
    urls = fetch_quran_ayah_urls(surah, ayah)
    dest.parent.mkdir(parents=True, exist_ok=True)
    for u in urls:
        try:
            req = urllib.request.Request(u, headers={"User-Agent": AUDIO_UA, "Accept": "*/*"})
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = resp.read()
            if len(data) <= 512:
                continue
            tmp = dest.with_suffix(".part")
            tmp.write_bytes(data)
            tmp.replace(dest)
            return True
        except (urllib.error.HTTPError, urllib.error.URLError, OSError):
            continue
    return False


def load_surah_ayah_counts() -> List[int]:
    """Число аятов по сурам 1..114 через API (один запрос)."""
    url = "https://api.alquran.cloud/v1/meta"
    req = urllib.request.Request(url, headers={"User-Agent": QURAN_API_UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        root = json.loads(resp.read().decode("utf-8"))
    surahs = root["data"]["surahs"]["references"]
    return [int(s["numberOfAyahs"]) for s in surahs]


def main() -> int:
    ap = argparse.ArgumentParser(description="Офлайн-пакет озвучек Библии и Корана для переноса adb.")
    ap.add_argument("--out", default="offline_media_bundle", type=Path, help="Корневая папка вывода")
    ap.add_argument("--bible-only", action="store_true")
    ap.add_argument("--quran-only", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--sleep", type=float, default=0.05, help="Пауза между запросами (сек)")
    ap.add_argument(
        "--narrators",
        default="all",
        help="Список id через запятую или all. Id: "
        + ",".join(n for n, _ in NARRATORS_4BBL)
        + ",web,hebrew-ot,greek-nt",
    )
    ap.add_argument("--max-bible-files", type=int, default=0, help="0 = без лимита (только отладка)")
    ap.add_argument("--max-quran-ayahs", type=int, default=0, help="0 = без лимита")
    args = ap.parse_args()

    out_root: Path = args.out.resolve()
    if args.narrators.strip().lower() == "all":
        narrators = [n for n, _ in NARRATORS_4BBL] + ["web", "hebrew-ot", "greek-nt"]
    else:
        narrators = [x.strip() for x in args.narrators.split(",") if x.strip()]

    bible_ok = bible_fail = 0
    quran_ok = quran_fail = 0

    if not args.quran_only:
        jobs = list(iter_bible_jobs(narrators))
        if args.max_bible_files > 0:
            jobs = jobs[: args.max_bible_files]
        print(f"Библия: {len(jobs)} файлов -> {out_root}")
        for i, (nid, book_id, ch, rel, _) in enumerate(jobs):
            url = build_bible_url(nid, book_id, ch)
            dest = out_root / rel
            if url is None:
                bible_fail += 1
                continue
            ok = download_one(url, dest, dry_run=args.dry_run)
            if ok:
                bible_ok += 1
            else:
                bible_fail += 1
                print(f"FAIL bible {nid} {book_id} {ch}", file=sys.stderr)
            if (i + 1) % 200 == 0:
                print(f"  bible {i + 1}/{len(jobs)} ok={bible_ok} fail={bible_fail}")
            if args.sleep > 0 and not args.dry_run:
                time.sleep(args.sleep)

    if not args.bible_only:
        try:
            counts = load_surah_ayah_counts()
        except Exception as e:
            print("Не удалось загрузить meta Корана:", e, file=sys.stderr)
            return 2
        total_ayahs = sum(counts)
        limit = args.max_quran_ayahs if args.max_quran_ayahs > 0 else total_ayahs
        done = 0
        print(f"Коран: до {limit} аятов из ~{total_ayahs} -> {out_root}")
        for si, nc in enumerate(counts, start=1):
            for ay in range(1, nc + 1):
                if done >= limit:
                    break
                if download_quran_ayah(out_root, si, ay, args.dry_run):
                    quran_ok += 1
                else:
                    quran_fail += 1
                    print(f"FAIL quran {si}:{ay}", file=sys.stderr)
                done += 1
                if done % 200 == 0:
                    print(f"  quran {done}/{limit} ok={quran_ok} fail={quran_fail}")
                if args.sleep > 0 and not args.dry_run:
                    time.sleep(args.sleep)
            if done >= limit:
                break

    print(f"Готово. bible ok={bible_ok} fail={bible_fail}; quran ok={quran_ok} fail={quran_fail}")
    print(f"Папка: {out_root / 'files'}")
    return 0 if bible_fail == 0 and quran_fail == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
