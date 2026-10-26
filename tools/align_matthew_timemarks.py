#!/usr/bin/env python3
"""Ставит таймкод на начало каждого стиха Евангелия от Матфея по файлу озвучки."""

import json
import re
import subprocess
import sys
import threading
from difflib import SequenceMatcher
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT = ASSETS / "timemark_projects"
AUDIO = Path.home() / "Library" / "Caches" / "bible-align" / "matthew"
FILES_DIR = "/data/user/0/com.example.bible.sqlite/files"

TRACKS = [
    ("SYN", "bible/SYN/matthew.json", "bondarenko", "syn-bondarenko", "Синодальный · Бондаренко"),
    ("NRT", "bible/NRT/matthew.json", "new-russian", "new-russian", "Новый русский"),
    ("RBO", "bible/RBO/matthew.json", "rbo", "rbo-orgin", "РБО"),
    ("BTI", "bible/BTI/matthew.json", "bti", "bti-prozorovsky", "Кулаковых"),
]

WORD = re.compile(r"[0-9a-zа-яё]+", re.IGNORECASE)


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


def download(url: str, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 1024:
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")
    subprocess.check_call(
        [
            "/usr/bin/curl",
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
            "-C",
            "-",
            "-o",
            str(tmp),
            url,
        ]
    )
    if tmp.stat().st_size < 1024:
        raise RuntimeError(f"short download {url}")
    tmp.replace(dest)


def transcribe(model, path: Path) -> list[tuple[str, float]]:
    segments, _info = model.transcribe(
        str(path),
        language="ru",
        word_timestamps=True,
        vad_filter=False,
        condition_on_previous_text=False,
        beam_size=1,
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


def align(verses: list[dict], heard: list[tuple[str, float]], duration: float) -> list[dict]:
    verse_words = []
    verse_of = []
    for verse in verses:
        parts = tokens(verse["text"])
        if not parts:
            parts = [str(verse["number"])]
        for part in parts:
            verse_words.append(part)
            verse_of.append(verse["number"])
    if not heard:
        step = duration / max(len(verses), 1)
        return [
            {"timeMs": int(i * step * 1000), "verseStart": verse["number"]}
            for i, verse in enumerate(verses)
        ]
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
        verse_no = verse_of[index]
        known.setdefault(verse_no, heard[heard_index][1])
    cues = []
    last = 0.0
    numbers = [verse["number"] for verse in verses]
    for pos, number in enumerate(numbers):
        if number in known:
            start = known[number]
        else:
            nxt = next((known[n] for n in numbers[pos + 1 :] if n in known), duration)
            missing = 1
            for n in numbers[pos + 1 :]:
                if n in known:
                    break
                missing += 1
            start = last + (nxt - last) / missing
        start = min(max(start, last), max(duration - 0.05, 0))
        if start <= last and pos > 0:
            start = min(last + 0.3, max(duration - 0.05, last))
        cues.append({"timeMs": int(round(start * 1000)), "verseStart": number})
        last = start
    return cues


def duration_sec(path: Path) -> float:
    probe = "ffprobe"
    try:
        out = subprocess.check_output(
            [
                probe,
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
    audio_name = f"40_{chapter:02d}.mp3"
    project = {
        "id": f"align-matthew-{code}-{chapter:02d}",
        "translationCode": code,
        "bookId": "matthew",
        "chapter": chapter,
        "title": f"От Матфея {chapter} · {label}",
        "audioFilePath": f"{FILES_DIR}/bible_audio/{narrator}/{audio_name}",
        "updatedAt": 1761510000000,
        "cues": [
            {"timeMs": cue["timeMs"], "verseStart": cue["verseStart"], "attachments": []}
            for cue in cues
        ],
    }
    dest = OUT / f"{project['id']}.json"
    dest.write_text(json.dumps(project, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    from faster_whisper import WhisperModel

    args = [arg for arg in sys.argv[1:] if arg != "--force"]
    only = [arg for arg in args if not arg.isdigit()]
    chapters_only = {int(arg) for arg in args if arg.isdigit()}
    model = WhisperModel("small", device="cpu", compute_type="int8", cpu_threads=6)
    jobs = []
    for code, rel, narrator, folder, label in TRACKS:
        if only and code not in only and narrator not in only:
            continue
        chapters = load_chapters(rel)
        for index, verses in enumerate(chapters, start=1):
            if chapters_only and index not in chapters_only:
                continue
            url = f"https://4bbl.ru/data/{folder}/40/{index:02d}.mp3"
            dest = AUDIO / narrator / f"40_{index:02d}.mp3"
            project_path = OUT / f"align-matthew-{code}-{index:02d}.json"
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
        print(f"{code} {index}: listen {dest.stat().st_size} bytes {length:.0f}s", flush=True)
        heard = transcribe(model, dest)
        cues = align(verses, heard, length)
        write_project(code, narrator, index, cues, label)
        close = sum(
            1
            for left, right in zip(cues, cues[1:])
            if right["timeMs"] - left["timeMs"] < 400
        )
        print(
            f"{code} {index}: {len(cues)} verses, heard {len(heard)} words, close {close}",
            flush=True,
        )
        for cue in cues[:4]:
            text = next(v["text"] for v in verses if v["number"] == cue["verseStart"])
            print(f"  {cue['timeMs'] / 1000:.1f}s  {cue['verseStart']}. {text[:80]}", flush=True)
        if cues:
            print(f"  last {cues[-1]['timeMs'] / 1000:.1f}s verse {cues[-1]['verseStart']}", flush=True)


if __name__ == "__main__":
    main()
