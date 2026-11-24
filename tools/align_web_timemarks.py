#!/usr/bin/env python3
"""Ставит таймкод на начало каждого стиха английской озвучки (диктор «web», перевод WEB).

Озвучка — публичные MP3 WordProject (wordpocket.org), их читает приложение для WEB.
Диктор читает текст KJV, поэтому слова сверяются с текстом KJV того же стиха
(нумерация стихов в Евангелиях у KJV и WEB совпадает). Если KJV не скачался
или в главе другое число стихов, берётся текст WEB из assets.

Логика выравнивания та же, что в align_john_timemarks.py (берётся оттуда):
распознаём слова с временем (faster-whisper), выравниваем текст стихов по порядку
слов, ставим метку на первое надёжно совпавшее слово стиха, остальное интерполируем.

Запуск:
  python3 tools/align_web_timemarks.py                 # Матфей и Иоанн целиком
  python3 tools/align_web_timemarks.py john 3 4        # только главы 3 и 4 Иоанна
  python3 tools/align_web_timemarks.py --rehear        # распознать заново, без кэша
  python3 tools/align_web_timemarks.py --repair        # дочинить пропуски в кэше расшифровок
Переменные: WHISPER_MODEL (по умолчанию medium.en), WHISPER_CPU_THREADS.
"""

import json
import os
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import align_john_timemarks as base  # noqa: E402

ROOT = base.ROOT
ASSETS = base.ASSETS
OUT = base.OUT
DOCS = base.DOCS
FILES_DIR = base.FILES_DIR
WORK = Path(os.environ.get("WEB_ALIGN_DIR", "/tmp/web-align"))
AUDIO = WORK / "audio"
CACHE = WORK / "transcripts"
NARRATOR = "web"
CODE = "WEB"
LABEL = "WEB (англ.)"
UPDATED_AT = 1794873600000
KJV_URL = "https://raw.githubusercontent.com/thiagobodruk/bible/master/json/en_kjv.json"

# bookId, номер книги WordProject, индекс в KJV-json, название по-русски, имя для подсказки
BOOKS = [
    ("matthew", 40, 39, "От Матфея", "Matthew"),
    ("mark", 41, 40, "От Марка", "Mark"),
    ("luke", 42, 41, "От Луки", "Luke"),
    ("john", 43, 42, "От Иоанна", "John"),
    ("acts", 44, 43, "Деяния", "Acts"),
    ("romans", 45, 44, "К Римлянам", "Romans"),
    ("1_corinthians", 46, 45, "1-е Коринфянам", "1 Corinthians"),
    ("2_corinthians", 47, 46, "2-е Коринфянам", "2 Corinthians"),
    ("galatians", 48, 47, "К Галатам", "Galatians"),
    ("ephesians", 49, 48, "К Ефесянам", "Ephesians"),
    ("philippians", 50, 49, "К Филиппийцам", "Philippians"),
    ("colossians", 51, 50, "К Колоссянам", "Colossians"),
    ("1_thessalonians", 52, 51, "1-е Фессалоникийцам", "1 Thessalonians"),
    ("2_thessalonians", 53, 52, "2-е Фессалоникийцам", "2 Thessalonians"),
    ("1_timothy", 54, 53, "1-е Тимофею", "1 Timothy"),
    ("2_timothy", 55, 54, "2-е Тимофею", "2 Timothy"),
    ("titus", 56, 55, "Титу", "Titus"),
    ("philemon", 57, 56, "К Филимону", "Philemon"),
    ("hebrews", 58, 57, "К Евреям", "Hebrews"),
    ("james", 59, 58, "Иакова", "James"),
    ("1_peter", 60, 59, "1-е Петра", "1 Peter"),
    ("2_peter", 61, 60, "2-е Петра", "2 Peter"),
    ("1_john", 62, 61, "1-е Иоанна", "1 John"),
    ("2_john", 63, 62, "2-е Иоанна", "2 John"),
    ("3_john", 64, 63, "3-е Иоанна", "3 John"),
    ("jude", 65, 64, "Иуды", "Jude"),
    ("revelation", 66, 65, "Откровение", "Revelation"),
]

UA = (
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
)

STOP = {
    "a", "an", "the", "and", "or", "but", "of", "to", "in", "on", "at", "by",
    "for", "with", "from", "into", "unto", "upon", "as", "is", "was", "be",
    "are", "were", "been", "it", "he", "she", "they", "we", "ye", "you",
    "i", "me", "my", "his", "her", "him", "them", "their", "our", "us",
    "your", "thy", "thee", "thou", "that", "this", "these", "those", "which",
    "who", "whom", "not", "no", "so", "then", "there", "if", "all", "shall",
    "will", "had", "have", "hath", "has", "do", "did", "s", "t", "o",
}

# Подменяем языковые настройки в общем модуле: STOP и LATIN читаются его функциями.
base.STOP = STOP
base.LATIN = re.compile(r"(?!x)x")  # в английском «латиница» — не признак сбоя
base.CACHE = CACHE
tokens = base.tokens


def load_audio(path: Path, start: float | None = None, dur: float | None = None):
    import numpy as np

    cmd = ["ffmpeg", "-nostdin", "-v", "error"]
    if start is not None:
        cmd += ["-ss", f"{start:.3f}", "-t", f"{dur:.3f}"]
    cmd += ["-i", str(path), "-f", "s16le", "-ac", "1", "-ar", "16000", "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.int16).astype(np.float32) / 32768.0


def _run(model, audio, beam_size: int, prompt: str, offset: float = 0.0):
    segments, _info = model.transcribe(
        audio,
        language="en",
        word_timestamps=True,
        vad_filter=False,
        condition_on_previous_text=False,
        beam_size=beam_size,
        temperature=0.0,
        initial_prompt=prompt,
    )
    return base.words_from_segments(segments, offset=offset)


# Без подсказки: с подсказкой «The Gospel according to …» модель считает заголовок
# уже прочитанным и пропускает первые стихи главы.
PROMPT = {"value": None}


def transcribe(model, path: Path, beam_size: int):
    return _run(model, load_audio(path), beam_size, PROMPT["value"])


def transcribe_window(model, path: Path, start: float, end: float, beam_size: int):
    dur = max(0.4, end - start)
    return _run(model, load_audio(path, start, dur), beam_size, PROMPT["value"], offset=start)


base.transcribe = transcribe
base.transcribe_window = transcribe_window

CHUNK = 12.0


def transcribe_chunks(model, path: Path, start: float, end: float, beam_size: int):
    """Окно по кускам ~12 с. Целиком whisper иногда слышит только «Chapter N»
    и перескакивает на 30-ю секунду; короткие куски без заголовка он читает."""
    words = []
    cursor = start
    while cursor < end - 0.3:
        stop = min(end, cursor + CHUNK)
        words.extend(transcribe_window(model, path, cursor, stop, beam_size))
        cursor = stop
    return base.drop_loops(sorted(words, key=lambda item: (item[1], item[2])))


def weak_windows(words, duration: float):
    """Как в base, плюс провал сразу после заголовка «Chapter N» (первые ~30 с):
    у WordProject глава начинается с заголовка, и base его не ловит (слово до 1 с)."""
    windows = list(base.weak_windows(words, duration))
    if words and 3.0 < words[0][1] <= 8.0:
        # Начало главы потеряно частично: заголовок и первый стих не услышаны.
        windows.append((0.0, min(duration, words[0][1] + 8.0)))
    for left, right in zip(words, words[1:]):
        if left[1] > 6.0:
            break
        if right[1] - left[1] > 8.0:
            windows.append((max(0.0, left[2] - 0.2), min(duration, right[1] + 0.4)))
            break
    return base.merge_windows(windows)[:5]


def repair(model, path: Path, words, duration: float, beam_size: int):
    for start, end in weak_windows(words, duration):
        fresh = base.drop_loops(transcribe_window(model, path, start, end, beam_size=max(beam_size, 5)))
        how = "whole"
        if not base.transcript_usable(fresh, start, end):
            fresh = transcribe_chunks(model, path, start, end, beam_size=max(beam_size, 5))
            how = "chunks"
        old = sum(1 for item in words if start - 0.05 <= item[1] <= end + 0.05)
        if base.transcript_usable(fresh, start, end) and len(fresh) > old:
            words = base.splice(words, start, end, fresh)
            print(f"  repair {start:.1f}-{end:.1f}s -> {len(fresh)} words ({how})", flush=True)
        else:
            print(f"  repair skip {start:.1f}-{end:.1f}s ({len(fresh)} words)", flush=True)
    return base.drop_loops(words)


base.repair = repair
REPAIR_CACHED = {"on": False}


def hear(model, dest: Path, tag: str, chapter: int, beam: int, duration: float, rehear: bool):
    """base.hear, но с --repair чинит уже сохранённую расшифровку, не слушая главу заново."""
    path = base.cache_path(tag, chapter, beam)
    cached = None if rehear else base.load_cache(path)
    if cached is not None:
        if REPAIR_CACHED["on"]:
            cached = repair(model, dest, cached, duration, beam)
            base.save_cache(path, cached)
        return cached
    return base.hear(model, dest, tag, chapter, beam, duration, True)


def download(url: str, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 1024:
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")
    subprocess.check_call(
        [
            "curl", "-fsSL", "--retry", "6", "--retry-all-errors", "--retry-delay", "2",
            "--connect-timeout", "20", "--max-time", "300",
            "-A", UA, "-e", "https://www.wordpocket.org/", "-o", str(tmp), url,
        ]
    )
    if tmp.stat().st_size < 1024:
        raise RuntimeError(f"short download {url}")
    tmp.replace(dest)


def load_kjv():
    dest = WORK / "en_kjv.json"
    try:
        if not dest.exists():
            dest.parent.mkdir(parents=True, exist_ok=True)
            with urllib.request.urlopen(KJV_URL, timeout=60) as resp:
                dest.write_bytes(resp.read())
        return json.loads(dest.read_text(encoding="utf-8-sig"))
    except Exception as exc:  # без KJV работаем по тексту WEB
        print(f"KJV unavailable ({exc}); using WEB text", flush=True)
        return None


def chapter_verses(web_chapter: list[dict], kjv_chapter: list[str] | None):
    """Текст для сверки: KJV того же номера стиха, иначе WEB."""
    if kjv_chapter is None or len(kjv_chapter) != len(web_chapter):
        return web_chapter, False
    out = []
    for verse in web_chapter:
        n = verse["number"]
        text = kjv_chapter[n - 1] if 1 <= n <= len(kjv_chapter) else verse["text"]
        out.append({"number": n, "text": re.sub(r"[{}\[\]]", "", text)})
    return out, True


def align(verses, heard, duration: float):
    """Как base.align, но ещё возвращает число стихов с прямым якорем."""
    verse_words, verse_of = [], []
    for verse in verses:
        for part in tokens(verse["text"]) or [str(verse["number"])]:
            verse_words.append(part)
            verse_of.append(verse["number"])
    if not heard:
        step = duration / max(len(verses), 1)
        return [{"timeMs": int(i * step * 1000), "verseStart": v["number"]} for i, v in enumerate(verses)], 0.0, 0
    matched = base.align_words(verse_words, [item[0] for item in heard])
    ratio = len(matched) / max(len(verse_words), 1)
    known, confidence = base.anchors_from_matches(verses, verse_words, verse_of, heard, matched)
    known = base.filter_anchors(verses, known, confidence)
    cues = base.interpolate(verses, known, duration, heard)
    hit = base.opening_hit(verses, cues, heard)
    return cues, (hit if ratio >= 0.35 else min(hit, ratio)), len(known)


def write_project(book_id: str, book_num: int, title_ru: str, chapter: int, cues: list[dict]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    DOCS.mkdir(parents=True, exist_ok=True)
    audio_name = f"{book_num}_{chapter:02d}.mp3"
    project = {
        "id": f"align-{book_id}-{CODE}-{chapter:02d}",
        "translationCode": CODE,
        "bookId": book_id,
        "chapter": chapter,
        "title": f"{title_ru} {chapter} · {LABEL}",
        "audioFilePath": f"{FILES_DIR}/bible_audio/{NARRATOR}/{audio_name}",
        "updatedAt": UPDATED_AT,
        "cues": [
            {"timeMs": cue["timeMs"], "verseStart": cue["verseStart"], "attachments": []}
            for cue in cues
        ],
    }
    (OUT / f"{project['id']}.json").write_text(
        json.dumps(project, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    portable = {
        "id": f"gh_{CODE.lower()}_{book_id}_{chapter}",
        "translationCode": CODE,
        "bookId": book_id,
        "chapter": chapter,
        "title": project["title"],
        "narratorId": NARRATOR,
        "audioFileName": audio_name,
        "cues": [{"timeMs": cue["timeMs"], "verseStart": cue["verseStart"]} for cue in cues],
    }
    (DOCS / f"{CODE.lower()}_{book_id}_{chapter}.json").write_text(
        json.dumps(portable, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def fa_chapter(fa, dest: Path, verses, web_verses, length: float):
    """Принудительное выравнивание (MMS_FA) по тексту KJV/WEB, как для греческой озвучки."""
    import align_greek_timemarks as grk

    starts, confidence = grk.forced_align(
        fa, dest, verses, romanize=lambda text: re.findall(r"[a-z]+", text.lower().replace("'", ""))
    )
    cues = grk.fa_cues(verses, starts, length)
    keep = {v["number"] for v in web_verses}
    cues = [c for c in cues if c["verseStart"] in keep]
    if len(cues) != len(web_verses):
        raise RuntimeError(f"cue count {len(cues)} != verses {len(web_verses)}")
    row = {
        "verses": len(cues), "confident": sum(1 for v in confidence.values() if v >= grk.CONFIDENT),
        "weak": [n for n, v in confidence.items() if v < grk.CONFIDENT],
        "meanConfidence": round(sum(confidence.values()) / max(len(confidence), 1), 3),
        "duration": round(length, 1),
    }
    return cues, row


def main() -> None:
    from faster_whisper import WhisperModel

    rehear = "--rehear" in sys.argv
    REPAIR_CACHED["on"] = "--repair" in sys.argv
    args = [arg for arg in sys.argv[1:] if not arg.startswith("--")]
    books_only = {arg for arg in args if not arg.isdigit()}
    chapters_only = {int(arg) for arg in args if arg.isdigit()}
    model_name = os.environ.get("WHISPER_MODEL", "medium.en")
    threads = int(os.environ.get("WHISPER_CPU_THREADS", str(os.cpu_count() or 4)))
    fa = None
    if "--fa" in sys.argv:
        import align_greek_timemarks as grk  # noqa: F401  (MMS_FA)

        fa = grk.load_fa()
        model = None
        model_name = "fa"
    else:
        model = WhisperModel(model_name, device="cpu", compute_type="int8", cpu_threads=threads)
    kjv = load_kjv()
    stats = []
    for book_id, book_num, kjv_index, title_ru, name_en in BOOKS:
        if books_only and book_id not in books_only:
            continue
        web = base.load_chapters(f"bible/WEB/{book_id}.json")
        for index, web_verses in enumerate(web, start=1):
            if chapters_only and index not in chapters_only:
                continue
            if (OUT / f"align-{book_id}-{CODE}-{index:02d}.json").exists() and "--force" not in sys.argv:
                print(f"{book_id} {index}: already done", flush=True)
                continue
            kjv_chapter = kjv[kjv_index]["chapters"][index - 1] if kjv else None
            verses, used_kjv = chapter_verses(web_verses, kjv_chapter)
            url = f"https://www.wordpocket.org/bibles/app/audio/1/{book_num}/{index}.mp3"
            dest = AUDIO / f"{book_num}_{index:02d}.mp3"
            download(url, dest)
            length = base.duration_sec(dest)
            print(f"{book_id} {index}: listen {length:.0f}s, text {'KJV' if used_kjv else 'WEB'}", flush=True)
            if fa is not None:
                if kjv_chapter is not None and len(kjv_chapter) > len(web_verses):
                    # диктор читает KJV: стих, которого нет в WEB (Лк 17:36), выравниваем
                    # по KJV, а метку для него не пишем — его звук достаётся предыдущему стиху
                    verses = [{"number": i, "text": re.sub(r"[{}\[\]]", "", t)}
                              for i, t in enumerate(kjv_chapter, start=1)]
                    used_kjv = True
                spoken, unread = web_verses, []
                if kjv_chapter is not None and len(kjv_chapter) < len(web_verses):
                    # в WEB стихов больше, чем читает диктор KJV (Рим 14:24-26 — славословие,
                    # у KJV оно в 16:25-27): выравниваем по KJV, а лишние стихи WEB ставим
                    # сразу за последним, как «[отсутствует]» в русских дорожках
                    verses = [{"number": i, "text": re.sub(r"[{}\[\]]", "", t)}
                              for i, t in enumerate(kjv_chapter, start=1)]
                    spoken, unread = web_verses[:len(kjv_chapter)], web_verses[len(kjv_chapter):]
                    used_kjv = True
                cues, row = fa_chapter(fa, dest, verses, spoken, length)
                for verse in unread:
                    t = min(cues[-1]["timeMs"] + 200, int(max(length - 0.05, 0.0) * 1000))
                    cues.append({"timeMs": t, "verseStart": verse["number"]})
                if unread:
                    row["unread"] = [verse["number"] for verse in unread]
                    row["verses"] = len(cues)
                write_project(book_id, book_num, title_ru, index, cues)
                row.update({"book": book_id, "chapter": index, "text": "KJV" if used_kjv else "WEB"})
                stats.append(row)
                with open(WORK / "stats-fa.jsonl", "a", encoding="utf-8") as fh:
                    fh.write(json.dumps(row) + "\n")
                print(json.dumps(row), flush=True)
                continue
            tag = f"{NARRATOR}-{book_id}"
            heard = hear(model, dest, tag, index, 1, length, rehear)
            cues, hit, anchored = align(verses, heard, length)
            if hit < 0.8:
                print(f"{book_id} {index}: retry beam 5 (hit {hit:.2f}, words {len(heard)})", flush=True)
                heard5 = hear(model, dest, tag, index, 5, length, rehear)
                cues5, hit5, anchored5 = align(verses, heard5, length)
                if (hit5, anchored5) > (hit, anchored):
                    cues, hit, anchored, heard = cues5, hit5, anchored5, heard5
            if len(cues) != len(web_verses):
                raise RuntimeError(f"{book_id} {index}: cue count {len(cues)} != verses {len(web_verses)}")
            write_project(book_id, book_num, title_ru, index, cues)
            stats.append({
                "book": book_id, "chapter": index, "verses": len(cues), "anchored": anchored,
                "hit": round(hit, 3), "words": len(heard), "duration": round(length, 1),
                "lastCueSec": cues[-1]["timeMs"] / 1000 if cues else 0, "text": "KJV" if used_kjv else "WEB",
            })
            with open(WORK / "stats.jsonl", "a", encoding="utf-8") as fh:
                fh.write(json.dumps(stats[-1]) + "\n")
            print(f"{book_id} {index}: {len(cues)} verses, anchored {anchored}, hit {hit:.2f}", flush=True)
    suffix = "-".join(sorted(books_only)) or "all"
    (WORK / f"stats-{model_name}-{suffix}.json").write_text(json.dumps(stats, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
