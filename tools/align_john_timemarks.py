#!/usr/bin/env python3
"""Ставит таймкод на начало каждого стиха Евангелия от Иоанна по файлу озвучки.

Дорожки — те же дикторы, что в приложении:
  Синодальный (Бондаренко), Новый русский, РБО, Кулаковых.
Файлы временно качаются с bible.by (бывший 4bbl.ru) и в репозиторий не кладутся.
В репозиторий попадают только JSON-метки.
"""

import json
import os
import re
import subprocess
import sys
import threading
from difflib import SequenceMatcher
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT = ASSETS / "timemark_projects"
DOCS = ROOT / "docs" / "timemarks" / "projects"
AUDIO = Path("/tmp/bible-align/john")
FILES_DIR = "/data/user/0/com.example.bible.sqlite/files"
BOOK_NUM = 43
UPDATED_AT = 1763308800000

TRACKS = [
    ("SYN", "bible/SYN/john.json", "bondarenko", "syn-bondarenko", "Синодальный · Бондаренко"),
    ("NRT", "bible/NRT/john.json", "new-russian", "new-russian", "Новый русский"),
    ("RBO", "bible/RBO/john.json", "rbo", "rbo-orgin", "РБО"),
    ("BTI", "bible/BTI/john.json", "bti", "bti-prozorovsky", "Кулаковых"),
]

WORD = re.compile(r"[0-9a-zа-яё]+", re.IGNORECASE)
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


def transcribe(model, path: Path, prompt: str, beam_size: int = 1) -> list[tuple[str, float]]:
    segments, _info = model.transcribe(
        str(path),
        language="ru",
        word_timestamps=True,
        vad_filter=False,
        condition_on_previous_text=False,
        beam_size=beam_size,
        initial_prompt=prompt[:400] if prompt else None,
    )
    words = []
    for segment in segments:
        for word in segment.words or []:
            token = tokens(word.word)
            if not token:
                continue
            for piece in token:
                words.append((piece, float(word.start)))
    return words


def same_word(a: str, b: str) -> bool:
    if a == b:
        return True
    if len(a) >= 5 and len(b) >= 5 and a[:4] == b[:4]:
        return True
    return False


def content_words(text: str) -> list[str]:
    parts = [w for w in tokens(text) if w not in STOP and len(w) > 2]
    return parts or tokens(text)[:3]


def find_verse_start(targets: list[str], heard_words: list[str], cursor: int) -> int | None:
    if not targets:
        return None
    goals = targets[:8]
    need = 1 if len(goals) == 1 else 2
    for i in range(cursor, len(heard_words)):
        if not same_word(heard_words[i], goals[0]):
            continue
        matched = {0}
        for word in heard_words[i + 1 : i + 18]:
            for gi, goal in enumerate(goals):
                if gi in matched:
                    continue
                if same_word(word, goal):
                    matched.add(gi)
                    break
            if len(matched) >= need:
                return i
    return None


def align_global(verses: list[dict], heard: list[tuple[str, float]], duration: float) -> list[dict]:
    verse_words = []
    verse_of = []
    for verse in verses:
        parts = tokens(verse["text"]) or [str(verse["number"])]
        for part in parts:
            verse_words.append(part)
            verse_of.append(verse["number"])
    heard_words = [item[0] for item in heard]
    matcher = SequenceMatcher(a=verse_words, b=heard_words, autojunk=False)
    mapped = [None] * len(verse_words)
    for block in matcher.get_matching_blocks():
        for offset in range(block.size):
            mapped[block.a + offset] = block.b + offset
    known = {}
    for index, heard_index in enumerate(mapped):
        if heard_index is None:
            continue
        known.setdefault(verse_of[index], heard[heard_index][1])
    return cues_from_known(verses, known, duration)


def cues_from_known(verses: list[dict], known: dict, duration: float) -> list[dict]:
    cues = []
    last = 0.0
    numbers = [verse["number"] for verse in verses]
    for pos, number in enumerate(numbers):
        if number in known and known[number] >= last + 0.35:
            start = known[number]
        else:
            nxt = next(
                (known[n] for n in numbers[pos + 1 :] if n in known and known[n] > last + 0.35),
                duration,
            )
            missing = 1
            for n in numbers[pos + 1 :]:
                if n in known and known[n] > last + 0.35:
                    break
                missing += 1
            # nxt — начало следующего найденного стиха или конец файла,
            # поэтому недостающие начала делят промежуток на missing+1 частей.
            start = last + max(nxt - last, 0) / (missing + 1)
        start = min(max(start, last), max(duration - 0.05, 0))
        cues.append({"timeMs": int(round(start * 1000)), "verseStart": number})
        last = start
    return cues


def align_sequential(verses: list[dict], heard: list[tuple[str, float]], duration: float) -> list[dict]:
    heard_words = [item[0] for item in heard]
    known = {}
    cursor = 0
    for verse in verses:
        idx = find_verse_start(content_words(verse["text"]), heard_words, cursor)
        if idx is None:
            continue
        known[verse["number"]] = heard[idx][1]
        cursor = idx + 1
    return cues_from_known(verses, known, duration)


def alignment_cost(cues: list[dict], duration: float) -> float:
    times = [cue["timeMs"] / 1000 for cue in cues]
    if len(times) < 2 or duration <= 0:
        return 1e9
    gaps = [b - a for a, b in zip(times, times[1:])]
    collapsed = sum(1 for gap in gaps if gap < 0.45)
    span = max(times[-1] - times[0], 0)
    cover_error = abs(span / duration - 0.88)
    late_end = max(0.0, (duration - times[-1]) / duration - 0.08)
    return collapsed * 5 + cover_error * 30 + late_end * 40


def align(verses: list[dict], heard: list[tuple[str, float]], duration: float) -> tuple[list[dict], float]:
    verse_words = []
    for verse in verses:
        verse_words.extend(tokens(verse["text"]) or [str(verse["number"])])
    if not heard:
        step = duration / max(len(verses), 1)
        cues = [
            {"timeMs": int(i * step * 1000), "verseStart": verse["number"]}
            for i, verse in enumerate(verses)
        ]
        return cues, 0.0
    heard_words = [item[0] for item in heard]
    matcher = SequenceMatcher(a=verse_words, b=heard_words, autojunk=False)
    matched = sum(block.size for block in matcher.get_matching_blocks())
    ratio = (2 * matched) / max(len(verse_words) + len(heard_words), 1)
    global_cues = align_global(verses, heard, duration)
    sequential_cues = align_sequential(verses, heard, duration)
    cues = min(
        (global_cues, sequential_cues),
        key=lambda item: alignment_cost(item, duration),
    )
    return cues, ratio


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
    docs_name = f"{code.lower()}_john_{chapter}.json"
    (DOCS / docs_name).write_text(
        json.dumps(portable, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    from faster_whisper import WhisperModel

    args = [arg for arg in sys.argv[1:] if arg != "--force"]
    only = [arg for arg in args if not arg.isdigit()]
    chapters_only = {int(arg) for arg in args if arg.isdigit()}
    threads = int(os.environ.get("WHISPER_CPU_THREADS", "4"))
    model = WhisperModel("small", device="cpu", compute_type="int8", cpu_threads=threads)
    jobs = []
    for code, rel, narrator, folder, label in TRACKS:
        if only and code not in only and narrator not in only:
            continue
        chapters = load_chapters(rel)
        for index, verses in enumerate(chapters, start=1):
            if chapters_only and index not in chapters_only:
                continue
            url = audio_url(folder, index)
            dest = AUDIO / narrator / f"{BOOK_NUM}_{index:02d}.mp3"
            project_path = OUT / f"align-john-{code}-{index:02d}.json"
            jobs.append((code, narrator, label, index, verses, url, dest, project_path))
    prefetch = None
    for pos, (code, narrator, label, index, verses, url, dest, project_path) in enumerate(jobs):
        if project_path.exists() and "--force" not in sys.argv:
            print(f"{code} {index}: already done", flush=True)
            continue
        if prefetch is not None:
            prefetch.join()
            prefetch = None
        print(f"{code} {index}: download", flush=True)
        download(url, dest)
        nxt = next(
            (
                item
                for item in jobs[pos + 1 :]
                if not (item[7].exists() and "--force" not in sys.argv)
                and not (item[6].exists() and item[6].stat().st_size > 1024)
            ),
            None,
        )
        if nxt is not None:
            prefetch = threading.Thread(
                target=download,
                args=(nxt[5], nxt[6]),
                daemon=True,
            )
            prefetch.start()
        length = duration_sec(dest)
        prompt = " ".join(verse["text"] for verse in verses[:3])
        print(f"{code} {index}: listen {dest.stat().st_size} bytes {length:.0f}s", flush=True)
        heard = transcribe(model, dest, prompt, beam_size=1)
        text_count = sum(len(tokens(verse["text"])) for verse in verses)
        if text_count and len(heard) < 0.45 * text_count:
            print(
                f"{code} {index}: retry beam 5 ({len(heard)}/{text_count} words)",
                flush=True,
            )
            heard = transcribe(model, dest, prompt, beam_size=5)
        cues, ratio = align(verses, heard, length)
        if len(cues) != len(verses):
            raise RuntimeError(f"{code} {index}: cue count {len(cues)} != verses {len(verses)}")
        write_project(code, narrator, index, cues, label)
        close = sum(
            1
            for left, right in zip(cues, cues[1:])
            if right["timeMs"] - left["timeMs"] < 400
        )
        print(
            f"{code} {index}: {len(cues)} verses, heard {len(heard)} words, "
            f"close {close}, match {ratio:.2f}",
            flush=True,
        )
        for cue in cues[:4]:
            text = next(v["text"] for v in verses if v["number"] == cue["verseStart"])
            print(f"  {cue['timeMs'] / 1000:.1f}s  {cue['verseStart']}. {text[:80]}", flush=True)
        if cues:
            print(f"  last {cues[-1]['timeMs'] / 1000:.1f}s verse {cues[-1]['verseStart']}", flush=True)


if __name__ == "__main__":
    main()
