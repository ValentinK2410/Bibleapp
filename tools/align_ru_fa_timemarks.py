#!/usr/bin/env python3
"""Таймкоды русских озвучек (Синодальный, НРП, РБО, Кулаковых) принудительным
выравниванием (torchaudio MMS_FA), как для греческой озвучки.

Текст стиха транслитерируется латиницей и выравнивается по звуку целиком;
между стихами — «звёздочка» (любой звук: заставки, «Глава первая», паузы).
Это быстрее и точнее, чем распознавание Whisper в align_john_timemarks.py,
у которого в длинных главах выпадают куски. Пути, скачивание с bible.by
и формат файлов берутся из align_john_timemarks.py.

  python3 tools/align_ru_fa_timemarks.py mark            # все четыре дорожки Марка
  python3 tools/align_ru_fa_timemarks.py luke SYN 3 4    # Лука, Синодальный, главы 3–4
"""

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import align_john_timemarks as base  # noqa: E402
import align_greek_timemarks as grk  # noqa: E402  (load_fa, forced_align, CONFIDENT)

TRANSLIT = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e", "ж": "zh",
    "з": "z", "и": "i", "й": "i", "к": "k", "л": "l", "м": "m", "н": "n", "о": "o",
    "п": "p", "р": "r", "с": "s", "т": "t", "у": "u", "ф": "f", "х": "h", "ц": "ts",
    "ч": "ch", "ш": "sh", "щ": "shch", "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu",
    "я": "ya",
}
WORD = re.compile(r"[a-zа-яё]+", re.IGNORECASE)
MISSING = re.compile(r"^\s*\[?\s*отсутствует\s*\]?\s*$", re.IGNORECASE)


def romanize(text: str) -> list[str]:
    text = re.sub(r"^\s*\[\d+(?:[-–]\d+)?\]\s*", "", text)  # «[7-8]» у Кулаковых
    out = []
    for word in WORD.findall(text.lower()):
        roman = "".join(TRANSLIT.get(ch, ch) for ch in word)
        if roman:
            out.append(roman)
    return out


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    books = [a for a in args if a in base.BOOKS]
    base.set_book(books[0] if books else "john")
    args = [a for a in args if a not in base.BOOKS]
    only = [a for a in args if not a.isdigit()]
    chapters_only = {int(a) for a in args if a.isdigit()}
    force = "--force" in sys.argv
    fa = grk.load_fa()
    stats_path = base.AUDIO / "stats-fa.jsonl"
    for code, rel, narrator, folder, label in base.TRACKS:
        if only and code not in only and narrator not in only:
            continue
        for index, verses in enumerate(base.load_chapters(rel), start=1):
            if chapters_only and index not in chapters_only:
                continue
            target = base.OUT / f"align-{base.BOOK}-{code}-{index:02d}.json"
            if target.exists() and not force:
                print(f"{code} {index}: already done", flush=True)
                continue
            dest = base.AUDIO / narrator / f"{base.BOOK_NUM}_{index:02d}.mp3"
            base.download(base.audio_url(folder, index), dest)
            length = base.duration_sec(dest)
            # «[отсутствует]» диктор не читает: такой стих ставим сразу за предыдущим
            spoken = [v for v in verses if not MISSING.match(v["text"])]
            starts, confidence = grk.forced_align(fa, dest, spoken, romanize=romanize)
            cues = []
            last = -1.0
            for verse in verses:
                n = verse["number"]
                t = max(0.0, starts[n] - 0.08) if n in starts else max(last, 0.0)
                if cues and t < last + 0.2:
                    t = last + 0.2
                t = min(t, max(length - 0.05, 0.0))
                cues.append({"timeMs": int(round(t * 1000)), "verseStart": n})
                last = t
            base.write_project(code, narrator, index, cues, label)
            sure = sum(1 for n in confidence if confidence[n] >= grk.CONFIDENT)
            row = {
                "book": base.BOOK, "code": code, "chapter": index, "verses": len(cues),
                "spoken": len(spoken), "confident": sure,
                "weak": [n for n in confidence if confidence[n] < grk.CONFIDENT],
                "meanConfidence": round(sum(confidence.values()) / max(len(confidence), 1), 3),
                "duration": round(length, 1), "firstCue": cues[0]["timeMs"] / 1000,
                "lastCue": cues[-1]["timeMs"] / 1000,
            }
            stats_path.parent.mkdir(parents=True, exist_ok=True)
            with open(stats_path, "a", encoding="utf-8") as fh:
                fh.write(json.dumps(row, ensure_ascii=False) + "\n")
            print(json.dumps(row, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
