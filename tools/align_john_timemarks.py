#!/usr/bin/env python3
"""Ставит таймкод на начало каждого стиха Евангелия от Иоанна по файлу озвучки.

Дорожки — те же дикторы, что в приложении:
  Синодальный (Бондаренко), Новый русский, РБО, Кулаковых.
Файлы временно качаются с bible.by (бывший 4bbl.ru) и в репозиторий не кладутся.
В репозиторий попадают только JSON-метки.

Граница стиха берётся из распознанных слов диктора: текст стиха выравнивается
по порядку слов, а не растягивается равномерно на всю главу.
"""

import json
import os
import re
import subprocess
import sys
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT = ASSETS / "timemark_projects"
DOCS = ROOT / "docs" / "timemarks" / "projects"
AUDIO = Path("/tmp/bible-align/john")
CACHE = AUDIO / "transcripts"
FILES_DIR = "/data/user/0/com.example.bible.sqlite/files"
BOOK_NUM = 43
UPDATED_AT = 1763323200000

TRACKS = [
    ("SYN", "bible/SYN/john.json", "bondarenko", "syn-bondarenko", "Синодальный · Бондаренко"),
    ("NRT", "bible/NRT/john.json", "new-russian", "new-russian", "Новый русский"),
    ("RBO", "bible/RBO/john.json", "rbo", "rbo-orgin", "РБО"),
    ("BTI", "bible/BTI/john.json", "bti", "bti-prozorovsky", "Кулаковых"),
]

WORD = re.compile(r"[0-9a-zа-яё]+", re.IGNORECASE)
LATIN = re.compile(r"^[a-z]+$")
STOP = {
    "и", "в", "во", "на", "не", "ни", "что", "он", "она", "оно", "они",
    "его", "ее", "её", "их", "ему", "ей", "им", "к", "ко", "с", "со",
    "а", "но", "то", "это", "как", "по", "из", "у", "о", "об", "обо",
    "же", "бы", "ли", "да", "за", "от", "до", "для", "при", "или",
    "мы", "вы", "ты", "я", "мне", "меня", "нас", "вас", "уже", "еще",
    "ещё", "там", "тут", "так", "все", "всё", "был", "была", "было",
    "были", "есть", "этот", "эта", "эти", "того", "том", "тем", "него",
    "нее", "неё", "ним", "них", "свой", "своя", "свое", "своё", "свои",
}
UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
PROMPT = "Евангелие от Иоанна."


def tokens(text: str) -> list[str]:
    raw = text.lower().replace("ё", "е")
    return WORD.findall(raw)


def load_chapters(rel: str) -> list[list[dict]]:
    data = json.loads((ASSETS / rel).read_text(encoding="utf-8"))
    chapters = []
    for chapter in data["book"]["chapters"]:
        verses = [
            {"number": int(v["number"]), "text": v["text"]}
            for v in chapter["verses"]
        ]
        chapters.append(verses)
    return chapters


def audio_url(folder: str, chapter: int) -> str:
    return (
        "https://bible.by/i/php/audio-stream.php"
        f"?audio={folder}&book={BOOK_NUM}&chapter={chapter}"
    )


def download(url: str, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 1024:
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")
    subprocess.check_call(
        [
            "curl",
            "-fL",
            "--retry",
            "6",
            "--retry-all-errors",
            "--retry-delay",
            "2",
            "--connect-timeout",
            "20",
            "--speed-limit",
            "1500",
            "--speed-time",
            "30",
            "--max-time",
            "300",
            "-A",
            UA,
            "-H",
            "Sec-Fetch-Site: same-origin",
            "-H",
            "Sec-Fetch-Mode: no-cors",
            "-H",
            "Sec-Fetch-Dest: audio",
            "-H",
            "Accept: */*",
            "-o",
            str(tmp),
            url,
        ]
    )
    if tmp.stat().st_size < 1024:
        raise RuntimeError(f"short download {url}")
    tmp.replace(dest)


def duration_sec(path: Path) -> float:
    try:
        out = subprocess.check_output(
            [
                "ffprobe",
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                str(path),
            ],
            text=True,
        )
        return float(out.strip() or "0")
    except Exception:
        return 0.0


def words_from_segments(segments, offset: float = 0.0) -> list[tuple[str, float, float]]:
    words = []
    for segment in segments:
        for word in segment.words or []:
            for piece in tokens(word.word):
                words.append((piece, offset + float(word.start), offset + float(word.end)))
    return words


def transcribe(model, path: Path, beam_size: int) -> list[tuple[str, float, float]]:
    segments, _info = model.transcribe(
        str(path),
        language="ru",
        word_timestamps=True,
        vad_filter=False,
        condition_on_previous_text=False,
        beam_size=beam_size,
        temperature=0.0,
        initial_prompt=PROMPT,
    )
    return words_from_segments(segments)


def transcribe_window(model, path: Path, start: float, end: float, beam_size: int) -> list[tuple[str, float, float]]:
    dur = max(0.4, end - start)
    wav = path.with_suffix(f".slice-{int(start * 10)}.wav")
    subprocess.check_call(
        [
            "ffmpeg", "-y", "-ss", f"{start:.3f}", "-t", f"{dur:.3f}",
            "-i", str(path), "-ar", "16000", "-ac", "1", str(wav),
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        segments, _info = model.transcribe(
            str(wav),
            language="ru",
            word_timestamps=True,
            vad_filter=False,
            condition_on_previous_text=False,
            beam_size=beam_size,
            temperature=0.0,
            initial_prompt=PROMPT,
        )
        return words_from_segments(segments, offset=start)
    finally:
        wav.unlink(missing_ok=True)


def merge_windows(windows: list[tuple[float, float]]) -> list[tuple[float, float]]:
    if not windows:
        return []
    windows = sorted(windows)
    merged = [list(windows[0])]
    for start, end in windows[1:]:
        if start <= merged[-1][1] + 0.8:
            merged[-1][1] = max(merged[-1][1], end)
        else:
            merged.append([start, end])
    return [(a, b) for a, b in merged]


def transcript_usable(fresh: list[tuple[str, float, float]], start: float, end: float) -> bool:
    if len(fresh) < 4:
        return False
    dur = max(end - start, 0.1)
    if len(fresh) < dur * 0.85:
        return False
    unique = len({item[0] for item in fresh}) / len(fresh)
    if unique < 0.4:
        return False
    latin = sum(1 for item in fresh if LATIN.match(item[0])) / len(fresh)
    return latin < 0.2


def drop_loops(words: list[tuple[str, float, float]]) -> list[tuple[str, float, float]]:
    """Убирает зацикленные куски распознавания вроде «и лазаря» много раз подряд."""
    cleaned = []
    index = 0
    total = len(words)
    while index < total:
        skipped = False
        for size in (1, 2, 3, 4):
            if index + size * 4 > total:
                continue
            gram = tuple(item[0] for item in words[index:index + size])
            reps = 1
            while index + (reps + 1) * size <= total and tuple(
                item[0] for item in words[index + reps * size:index + (reps + 1) * size]
            ) == gram:
                reps += 1
            if reps >= 4:
                cleaned.extend(words[index:index + size])
                index += reps * size
                skipped = True
                break
        if not skipped:
            cleaned.append(words[index])
            index += 1
    return cleaned


def weak_windows(words: list[tuple[str, float, float]], duration: float) -> list[tuple[float, float]]:
    windows = []
    if words and words[0][1] > 8.0:
        windows.append((0.0, min(duration, words[0][1] + 0.8)))
    for left, right in zip(words, words[1:]):
        gap = right[1] - left[1]
        if gap > 8.0 and left[1] > 1.0 and right[1] < duration - 0.5:
            windows.append((max(0.0, left[1] - 0.4), min(duration, right[1] + 0.4)))
    run_start = None
    run_end = None
    run_count = 0
    for word, start, end in words:
        if LATIN.match(word):
            if run_start is None:
                run_start = start
            run_end = end
            run_count += 1
        else:
            if run_count >= 6 and run_start is not None and run_end is not None:
                windows.append((max(0.0, run_start - 1.2), min(duration, run_end + 1.2)))
            run_start = None
            run_end = None
            run_count = 0
    if run_count >= 6 and run_start is not None and run_end is not None:
        windows.append((max(0.0, run_start - 1.2), min(duration, run_end + 1.2)))
    if words and duration - words[-1][1] > 6.0:
        windows.append((max(0.0, words[-1][1] - 0.4), duration))
    return merge_windows(windows)[:4]


def splice(words, start: float, end: float, fresh: list[tuple[str, float, float]]):
    kept = [item for item in words if item[1] < start - 0.05 or item[1] > end + 0.05]
    return sorted(kept + fresh, key=lambda item: (item[1], item[2]))


def repair(model, path: Path, words, duration: float, beam_size: int):
    windows = weak_windows(words, duration)
    for start, end in windows:
        fresh = transcribe_window(model, path, start, end, beam_size=max(beam_size, 5))
        fresh = drop_loops(fresh)
        if transcript_usable(fresh, start, end):
            words = splice(words, start, end, fresh)
            print(f"  repair {start:.1f}-{end:.1f}s -> {len(fresh)} words", flush=True)
        else:
            print(f"  repair skip {start:.1f}-{end:.1f}s ({len(fresh)} words)", flush=True)
    return drop_loops(words)


def same_word(a: str, b: str) -> bool:
    if a == b:
        return True
    if len(a) >= 5 and len(b) >= 5 and a[:5] == b[:5]:
        return True
    return False


def pair_score(a: str, b: str) -> int:
    if a == b:
        return 2 if a in STOP else 8
    if a not in STOP and b not in STOP and same_word(a, b):
        return 4
    return -3


def align_words(verse_words: list[str], heard_words: list[str]) -> dict[int, int]:
    """Порядковое выравнивание. В карту попадают только реально совпавшие слова."""
    n, m = len(verse_words), len(heard_words)
    if n == 0 or m == 0:
        return {}
    gap = -2
    prev = [i * gap for i in range(m + 1)]
    ptr = bytearray((n + 1) * (m + 1))
    stride = m + 1
    for j in range(1, m + 1):
        ptr[j] = 2
    for i in range(1, n + 1):
        cur = [0] * (m + 1)
        cur[0] = i * gap
        ptr[i * stride] = 1
        ai = verse_words[i - 1]
        row = i * stride
        for j in range(1, m + 1):
            diag = prev[j - 1] + pair_score(ai, heard_words[j - 1])
            up = prev[j] + gap
            left = cur[j - 1] + gap
            if diag >= up and diag >= left:
                cur[j] = diag
                ptr[row + j] = 0
            elif up >= left:
                cur[j] = up
                ptr[row + j] = 1
            else:
                cur[j] = left
                ptr[row + j] = 2
        prev = cur
    matched: dict[int, int] = {}
    i, j = n, m
    while i > 0 or j > 0:
        kind = ptr[i * stride + j]
        if i > 0 and j > 0 and kind == 0:
            i -= 1
            j -= 1
            if pair_score(verse_words[i], heard_words[j]) > 0:
                matched[i] = j
        elif i > 0 and (j == 0 or kind == 1):
            i -= 1
        else:
            j -= 1
    return matched


def content_count(text: str) -> int:
    return sum(1 for word in tokens(text) if word not in STOP and len(word) > 2)


def anchors_from_matches(verses, verse_words, verse_of, heard, matched) -> tuple[dict[int, float], dict[int, int]]:
    heard_words = [item[0] for item in heard]
    known: dict[int, float] = {}
    confidence: dict[int, int] = {}
    first_index = {}
    for index, number in enumerate(verse_of):
        first_index.setdefault(number, index)
    by_verse: dict[int, list[int]] = {}
    for verse_index in matched:
        by_verse.setdefault(verse_of[verse_index], []).append(verse_index)
    for verse in verses:
        number = verse["number"]
        indexes = by_verse.get(number, [])
        if not indexes:
            continue
        origin = first_index[number]
        early = []
        confident = 0
        for verse_index in indexes:
            heard_index = matched[verse_index]
            if not same_word(verse_words[verse_index], heard_words[heard_index]):
                continue
            if verse_words[verse_index] not in STOP:
                confident += 1
            if verse_index - origin <= 4:
                early.append(verse_index)
        need = 1 if content_count(verse["text"]) <= 2 else 2
        if confident < need or not early:
            continue
        verse_index = min(early)
        while verse_index > origin and (verse_index - 1) in matched:
            prev = verse_index - 1
            if same_word(verse_words[prev], heard_words[matched[prev]]):
                verse_index = prev
            else:
                break
        known[number] = heard[matched[verse_index]][1]
        confidence[number] = confident
    return known, confidence


def filter_anchors(verses, known: dict[int, float], confidence: dict[int, int]) -> dict[int, float]:
    numbers = [verse["number"] for verse in verses]
    chars = {verse["number"]: max(len(verse["text"]), 1) for verse in verses}
    changed = True
    while changed and len(known) >= 2:
        changed = False
        ordered = [number for number in numbers if number in known]
        for left, right in zip(ordered, ordered[1:]):
            body = []
            seen = False
            for number in numbers:
                if number == left:
                    seen = True
                if seen and number != right:
                    body.append(number)
                if number == right:
                    break
            seconds = known[right] - known[left]
            total = sum(chars[number] for number in body) or 1
            rate = total / seconds if seconds > 0 else 999.0
            if seconds > 0.25 and 3.0 <= rate <= 20.0:
                continue
            drop = left if confidence.get(left, 0) <= confidence.get(right, 0) else right
            del known[drop]
            changed = True
            break
    return known


def interpolate(verses, known: dict[int, float], duration: float, heard) -> list[dict]:
    numbers = [verse["number"] for verse in verses]
    chars = {verse["number"]: max(len(verse["text"]), 1) for verse in verses}
    times = dict(known)
    speech_start = heard[0][1] if heard else 0.0
    speech_end = heard[-1][2] if heard else duration
    ordered = [number for number in numbers if number in times]

    def spread(start_number: int, end_number: int, t0: float, t1: float) -> None:
        body = []
        seen = False
        for number in numbers:
            if number == start_number:
                seen = True
            if seen:
                body.append(number)
            if number == end_number:
                break
        if not body:
            return
        # Последний якорь — уже начало end_number, время делят предыдущие стихи.
        share = body[:-1] if end_number in times and body[-1] == end_number else body
        if not share:
            times[body[-1]] = t0
            return
        total = sum(chars[number] for number in share) or 1
        cursor = t0
        span = max(t1 - t0, 0.0)
        for number in share:
            times[number] = cursor
            cursor += span * chars[number] / total

    if not ordered:
        step_end = max(speech_end, speech_start + 0.2)
        spread(numbers[0], numbers[-1], speech_start, step_end)
        times[numbers[-1]] = times.get(numbers[-1], speech_start)
    else:
        first = ordered[0]
        if first != numbers[0]:
            lead = []
            for number in numbers:
                if number == first:
                    break
                lead.append(number)
            rate = 8.5
            lead_chars = sum(chars[number] for number in lead)
            # Начало главы иногда выпадает из распознавания. Тогда первые стихи
            # стоят раньше первого услышанного слова, а не сжимаются в одну точку.
            t0 = max(0.0, times[first] - lead_chars / rate)
            cursor = t0
            total = lead_chars or 1
            span = max(times[first] - t0, 0.0)
            for number in lead:
                times[number] = cursor
                cursor += span * chars[number] / total
        for left, right in zip(ordered, ordered[1:]):
            spread(left, right, times[left], times[right])
        last = ordered[-1]
        tail = []
        seen = False
        for number in numbers:
            if number == last:
                seen = True
            if seen:
                tail.append(number)
        end = max(speech_end, times[last] + 0.3)
        end = min(max(duration - 0.05, times[last]), max(end, times[last] + 0.2))
        spread(last, numbers[-1], times[last], end)

    cues = []
    last_t = 0.0
    for number in numbers:
        start = min(max(times.get(number, last_t), last_t), max(duration - 0.05, 0.0))
        if cues and start < last_t + 0.2:
            start = min(last_t + 0.2, max(duration - 0.05, last_t))
        cues.append({"timeMs": int(round(start * 1000)), "verseStart": number})
        last_t = start
    return cues


def opening_hit(verses, cues, heard) -> float:
    if not verses:
        return 0.0
    hits = 0
    for verse, cue in zip(verses, cues):
        moment = cue["timeMs"] / 1000
        window = [item[0] for item in heard if moment - 0.45 <= item[1] <= moment + 1.8]
        opening = [word for word in tokens(verse["text"]) if word not in STOP and len(word) > 2][:4]
        opening = opening or tokens(verse["text"])[:3]
        if any(any(same_word(goal, heard_word) for heard_word in window) for goal in opening[:3]):
            hits += 1
    return hits / len(verses)


def align(verses, heard, duration: float) -> tuple[list[dict], float]:
    verse_words = []
    verse_of = []
    for verse in verses:
        parts = tokens(verse["text"]) or [str(verse["number"])]
        for part in parts:
            verse_words.append(part)
            verse_of.append(verse["number"])
    if not heard:
        step = duration / max(len(verses), 1)
        cues = [
            {"timeMs": int(i * step * 1000), "verseStart": verse["number"]}
            for i, verse in enumerate(verses)
        ]
        return cues, 0.0
    heard_words = [item[0] for item in heard]
    matched = align_words(verse_words, heard_words)
    ratio = len(matched) / max(len(verse_words), 1)
    known, confidence = anchors_from_matches(verses, verse_words, verse_of, heard, matched)
    known = filter_anchors(verses, known, confidence)
    cues = interpolate(verses, known, duration, heard)
    hit = opening_hit(verses, cues, heard)
    return cues, hit if ratio >= 0.35 else min(hit, ratio)


def write_project(code: str, narrator: str, chapter: int, cues: list[dict], label: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    DOCS.mkdir(parents=True, exist_ok=True)
    audio_name = f"{BOOK_NUM}_{chapter:02d}.mp3"
    project = {
        "id": f"align-john-{code}-{chapter:02d}",
        "translationCode": code,
        "bookId": "john",
        "chapter": chapter,
        "title": f"От Иоанна {chapter} · {label}",
        "audioFilePath": f"{FILES_DIR}/bible_audio/{narrator}/{audio_name}",
        "updatedAt": UPDATED_AT,
        "cues": [
            {"timeMs": cue["timeMs"], "verseStart": cue["verseStart"], "attachments": []}
            for cue in cues
        ],
    }
    dest = OUT / f"{project['id']}.json"
    dest.write_text(json.dumps(project, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    portable = {
        "id": f"gh_{code.lower()}_john_{chapter}",
        "translationCode": code,
        "bookId": "john",
        "chapter": chapter,
        "title": project["title"],
        "narratorId": narrator,
        "audioFileName": audio_name,
        "cues": [{"timeMs": cue["timeMs"], "verseStart": cue["verseStart"]} for cue in cues],
    }
    (DOCS / f"{code.lower()}_john_{chapter}.json").write_text(
        json.dumps(portable, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def cache_path(narrator: str, chapter: int, beam: int) -> Path:
    return CACHE / f"{narrator}-{chapter:02d}-b{beam}.json"


def load_cache(path: Path):
    if not path.exists():
        return None
    return [tuple(item) for item in json.loads(path.read_text(encoding="utf-8"))]


def save_cache(path: Path, words) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(words, ensure_ascii=False), encoding="utf-8")


def hear(model, dest: Path, narrator: str, chapter: int, beam: int, duration: float, rehear: bool):
    path = cache_path(narrator, chapter, beam)
    cached = None if rehear else load_cache(path)
    if cached is not None:
        return cached
    words = transcribe(model, dest, beam)
    words = repair(model, dest, words, duration, beam)
    save_cache(path, words)
    return words


def main() -> None:
    from faster_whisper import WhisperModel

    rehear = "--rehear" in sys.argv
    args = [arg for arg in sys.argv[1:] if arg not in {"--force", "--rehear"}]
    only = [arg for arg in args if not arg.isdigit()]
    chapters_only = {int(arg) for arg in args if arg.isdigit()}
    threads = int(os.environ.get("WHISPER_CPU_THREADS", "4"))
    model = WhisperModel("small", device="cpu", compute_type="int8", cpu_threads=threads)
    for code, rel, narrator, folder, label in TRACKS:
        if only and code not in only and narrator not in only:
            continue
        chapters = load_chapters(rel)
        for index, verses in enumerate(chapters, start=1):
            if chapters_only and index not in chapters_only:
                continue
            dest = AUDIO / narrator / f"{BOOK_NUM}_{index:02d}.mp3"
            print(f"{code} {index}: download", flush=True)
            download(audio_url(folder, index), dest)
            length = duration_sec(dest)
            print(f"{code} {index}: listen {dest.stat().st_size} bytes {length:.0f}s", flush=True)
            heard = hear(model, dest, narrator, index, 1, length, rehear)
            cues, hit = align(verses, heard, length)
            if hit < 0.8:
                print(f"{code} {index}: retry beam 5 (hit {hit:.2f}, words {len(heard)})", flush=True)
                heard = hear(model, dest, narrator, index, 5, length, rehear)
                cues, hit = align(verses, heard, length)
            if len(cues) != len(verses):
                raise RuntimeError(f"{code} {index}: cue count {len(cues)} != verses {len(verses)}")
            write_project(code, narrator, index, cues, label)
            print(
                f"{code} {index}: {len(cues)} verses, heard {len(heard)} words, hit {hit:.2f}",
                flush=True,
            )
            for cue in cues[:3]:
                text = next(v["text"] for v in verses if v["number"] == cue["verseStart"])
                print(f"  {cue['timeMs'] / 1000:.1f}s  {cue['verseStart']}. {text[:70]}", flush=True)
            if cues:
                print(f"  last {cues[-1]['timeMs'] / 1000:.1f}s verse {cues[-1]['verseStart']}", flush=True)


if __name__ == "__main__":
    main()
