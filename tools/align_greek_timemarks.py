#!/usr/bin/env python3
"""Ставит таймкод на начало каждого стиха греческой озвучки НЗ (диктор «greek-nt»).

Озвучка — MP3 WordProAudio (Koine, современное греческое произношение), их же
играет приложение в подстрочнике (перевод PODSTR). Метки пишутся для PODSTR.

Основной способ — принудительное выравнивание (CTC forced alignment) акустической
моделью MMS_FA из torchaudio: греческий текст стиха из PODSTR переводится в латиницу
«по звучанию» (новогреческое произношение: без ударений, η/ι/υ/ει/οι → i, αι → e,
ω → o, β → v, χ → h …) и привязывается к звуку целиком, буква за буквой. Между стихами
стоит «звёздочка» (*), она поглощает слова, которых нет в тексте PODSTR.

Проверка (--check): faster-whisper (language="el", с VAD) распознаёт главу, слова
сравниваются с текстом по тому же «звучанию» и выравниваются общей логикой из
align_john_timemarks.py; выводится расхождение меток с forced alignment.
Whisper на греческом пропускает куски, поэтому метки ставит forced alignment.

Запуск:
  python3 tools/align_greek_timemarks.py               # Матфей и Иоанн
  python3 tools/align_greek_timemarks.py john 3        # одна глава
  python3 tools/align_greek_timemarks.py --check john 3  # сверить с whisper
Нужно: torch, torchaudio (2.8, CPU), для --check ещё faster-whisper.
"""

import json
import os
import re
import subprocess
import sys
import unicodedata
from difflib import SequenceMatcher
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import align_john_timemarks as base  # noqa: E402

ASSETS = base.ASSETS
OUT = base.OUT
DOCS = base.DOCS
FILES_DIR = base.FILES_DIR
WORK = Path(os.environ.get("GREEK_ALIGN_DIR", "/tmp/greek-align"))
AUDIO = WORK / "audio"
CACHE = WORK / "transcripts"
NARRATOR = "greek-nt"
CODE = "PODSTR"
LABEL = "Греческий (Koine)"
UPDATED_AT = 1794873600000

BOOKS = [
    ("matthew", 40, "От Матфея"),
    ("mark", 41, "От Марка"),
    ("luke", 42, "От Луки"),
    ("john", 43, "От Иоанна"),
    ("romans", 45, "К Римлянам"),
    ("1_corinthians", 46, "1-е Коринфянам"),
    ("2_corinthians", 47, "2-е Коринфянам"),
    ("galatians", 48, "К Галатам"),
    ("ephesians", 49, "К Ефесянам"),
    ("philippians", 50, "К Филиппийцам"),
    ("colossians", 51, "К Колоссянам"),
    ("1_thessalonians", 52, "1-е Фессалоникийцам"),
    ("2_thessalonians", 53, "2-е Фессалоникийцам"),
    ("1_timothy", 54, "1-е Тимофею"),
    ("2_timothy", 55, "2-е Тимофею"),
    ("titus", 56, "Титу"),
    ("philemon", 57, "К Филимону"),
    ("hebrews", 58, "К Евреям"),
]

UA = (
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
)

GREEK = re.compile(r"[α-ω0-9]+")
DIGRAPHS = [
    ("αυ", "av"), ("ευ", "ev"), ("ηυ", "iv"),
    ("ου", "u"), ("ει", "i"), ("οι", "i"), ("υι", "i"), ("αι", "e"),
    ("γγ", "ng"), ("γκ", "g"),
    ("μπ", "b"), ("ντ", "d"), ("τζ", "dz"),
]
SINGLE = str.maketrans({
    "α": "a", "β": "v", "γ": "g", "δ": "d", "ε": "e", "ζ": "z", "η": "i",
    "θ": "th", "ι": "i", "κ": "k", "λ": "l", "μ": "m", "ν": "n", "ξ": "ks",
    "ο": "o", "π": "p", "ρ": "r", "σ": "s", "τ": "t", "υ": "i", "φ": "f",
    "χ": "h", "ψ": "ps", "ω": "o",
})


def plain(word: str) -> str:
    raw = unicodedata.normalize("NFD", word.lower())
    raw = "".join(ch for ch in raw if not unicodedata.combining(ch))
    return raw.replace("ς", "σ")


def sound(word: str) -> str:
    text = plain(word)
    for src, dst in DIGRAPHS:
        text = text.replace(src, dst)
    text = text.translate(SINGLE)
    text = re.sub(r"(.)\1+", r"\1", text)  # γεννήσεως / γενήσεως
    if text.startswith("gi") and len(text) > 3:  # Γιακώβ (новогреч.) = Ἰακώβ
        text = text[1:]
    return text


def tokens(text: str) -> list[str]:
    return [sound(piece) for piece in GREEK.findall(plain(text)) if sound(piece)]


STOP = {sound(w) for w in (
    "και δε ο η το του την τον της των τοις τους τα οι αι τη τω εν εις εκ εξ "
    "απο προς γαρ ουν οτι μη ου ουκ ουχ αυτου αυτω αυτον αυτων αυτοις αυτη "
    "ινα ως επι δια μετα κατα περι υπο συ σε σου σοι εγω με μου μοι ημεις "
    "υμεις υμιν υμων υμας ημιν ημων ημας αν εαν ει τις τι ος ην εστιν"
).split()}


def same_word(a: str, b: str) -> bool:
    if a == b:
        return True
    if len(a) >= 4 and len(b) >= 4:
        return SequenceMatcher(a=a, b=b, autojunk=False).ratio() >= 0.75
    return False


def pair_score(a: str, b: str) -> int:
    if a == b:
        return 2 if a in STOP else 8
    if a in STOP or b in STOP:
        return -3
    if len(a) >= 4 and len(b) >= 4:
        ratio = SequenceMatcher(a=a, b=b, autojunk=False).ratio()
        if ratio >= 0.75:
            return 5
        if ratio >= 0.6:
            return 1
    return -3


base.tokens = tokens
base.STOP = STOP
base.same_word = same_word
base.pair_score = pair_score
base.LATIN = re.compile(r"(?!x)x")
base.CACHE = CACHE


def load_audio(path: Path, start: float | None = None, dur: float | None = None):
    import numpy as np

    cmd = ["ffmpeg", "-nostdin", "-v", "error"]
    if start is not None:
        cmd += ["-ss", f"{start:.3f}", "-t", f"{dur:.3f}"]
    cmd += ["-i", str(path), "-f", "s16le", "-ac", "1", "-ar", "16000", "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.int16).astype(np.float32) / 32768.0


def words_from_segments(segments, offset: float = 0.0):
    words = []
    for segment in segments:
        for word in segment.words or []:
            for piece in tokens(word.word):
                words.append((piece, offset + float(word.start), offset + float(word.end)))
    return words


def _run(model, audio, beam_size: int, offset: float = 0.0):
    segments, _info = model.transcribe(
        audio,
        language="el",
        word_timestamps=True,
        vad_filter=True,
        condition_on_previous_text=False,
        beam_size=beam_size,
        temperature=0.0,
    )
    return words_from_segments(segments, offset=offset)


def transcribe(model, path: Path, beam_size: int):
    return _run(model, load_audio(path), beam_size)


def transcribe_window(model, path: Path, start: float, end: float, beam_size: int):
    dur = max(0.4, end - start)
    return _run(model, load_audio(path, start, dur), beam_size, offset=start)


base.transcribe = transcribe
base.transcribe_window = transcribe_window


def download(url: str, dest: Path) -> None:
    if dest.exists() and dest.stat().st_size > 1024:
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")
    subprocess.check_call(
        [
            "curl", "-fsSL", "--retry", "6", "--retry-all-errors", "--retry-delay", "2",
            "--connect-timeout", "20", "--max-time", "300",
            "-A", UA, "-e", "https://www.wordproaudio.net/", "-o", str(tmp), url,
        ]
    )
    if tmp.stat().st_size < 1024:
        raise RuntimeError(f"short download {url}")
    tmp.replace(dest)


def load_chapters(book_id: str) -> list[list[dict]]:
    data = json.loads((ASSETS / "bible" / CODE / f"{book_id}.json").read_text(encoding="utf-8"))
    chapters = []
    for chapter in data["book"]["chapters"]:
        verses = []
        for verse in chapter["verses"]:
            greek = " ".join(word.get("o", "") for word in verse.get("words") or [])
            verses.append({"number": int(verse["number"]), "text": greek or str(verse["number"])})
        chapters.append(verses)
    return chapters


def align(verses, heard, duration: float):
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
        "id": f"align-{book_id}-GRK-{chapter:02d}",
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
        "id": f"gh_grk_{book_id}_{chapter}",
        "translationCode": CODE,
        "bookId": book_id,
        "chapter": chapter,
        "title": project["title"],
        "narratorId": NARRATOR,
        "audioFileName": audio_name,
        "cues": [{"timeMs": cue["timeMs"], "verseStart": cue["verseStart"]} for cue in cues],
    }
    (DOCS / f"grk_{book_id}_{chapter}.json").write_text(
        json.dumps(portable, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


FRAME_CHUNK = 16000 * 30


def load_fa():
    import torch
    from torchaudio.pipelines import MMS_FA as bundle

    model = bundle.get_model(with_star=True)
    model.eval()
    return model, bundle.get_dict(star="*"), torch


def forced_align(fa, path: Path, verses: list[dict], romanize=None):
    """Начало каждого стиха (сек) и средняя уверенность букв первых слов стиха."""
    import torchaudio

    model, labels, torch = fa
    audio = load_audio(path)
    wav = torch.from_numpy(audio.copy())[None]
    parts = []
    with torch.inference_mode():
        for start in range(0, wav.shape[1], FRAME_CHUNK):
            emission, _ = model(wav[:, start:start + FRAME_CHUNK])
            parts.append(emission)
    emission = torch.cat(parts, 1)
    seconds_per_frame = wav.shape[1] / emission.shape[1] / 16000
    targets = [labels["*"]]
    first_token: dict[int, int] = {}
    head_tokens: dict[int, list[int]] = {}
    for verse in verses:
        if verse is not verses[0]:
            targets.append(labels["*"])
        words = ["".join(ch for ch in word if ch in labels) for word in (romanize or tokens)(verse["text"])]
        words = [word for word in words if word] or ["a"]
        first_token[verse["number"]] = len(targets)
        head = []
        for index, word in enumerate(words):
            if index < 3:
                head.extend(range(len(targets), len(targets) + len(word)))
            targets.extend(labels[ch] for ch in word)
        head_tokens[verse["number"]] = head
    targets.append(labels["*"])
    alignment, scores = torchaudio.functional.forced_align(
        emission, torch.tensor([targets], dtype=torch.int32), blank=0
    )
    spans = torchaudio.functional.merge_tokens(alignment[0], scores[0].exp())
    starts, confidence = {}, {}
    for verse in verses:
        number = verse["number"]
        starts[number] = spans[first_token[number]].start * seconds_per_frame
        head = [spans[i].score for i in head_tokens[number]]
        confidence[number] = sum(head) / max(len(head), 1)
    return starts, confidence


CONFIDENT = 0.5


def fa_cues(verses, starts, duration: float) -> list[dict]:
    cues = []
    last = -1.0
    for verse in verses:
        start = max(0.0, starts[verse["number"]] - 0.08)  # чуть раньше первой буквы
        if cues and start < last + 0.2:
            start = last + 0.2
        start = min(start, max(duration - 0.05, 0.0))
        cues.append({"timeMs": int(round(start * 1000)), "verseStart": verse["number"]})
        last = start
    return cues


def check_with_whisper(model, dest: Path, tag: str, chapter: int, verses, starts, duration: float):
    heard = base.hear(model, dest, tag, chapter, 1, duration, False)
    verse_words, verse_of = [], []
    for verse in verses:
        for part in tokens(verse["text"]) or [str(verse["number"])]:
            verse_words.append(part)
            verse_of.append(verse["number"])
    matched = base.align_words(verse_words, [item[0] for item in heard])
    known, confidence = base.anchors_from_matches(verses, verse_words, verse_of, heard, matched)
    known = base.filter_anchors(verses, known, confidence)
    diffs = [abs(known[n] - starts[n]) for n in known]
    close = sum(1 for d in diffs if d <= 0.7)
    return len(known), close, sorted(diffs)[len(diffs) // 2] if diffs else None


def loose_match(goal: str, heard: str) -> bool:
    if same_word(goal, heard):
        return True
    if len(goal) >= 3 and len(heard) >= 3:
        if goal in heard or heard in goal:
            return True
        return SequenceMatcher(a=goal, b=heard, autojunk=False).ratio() >= 0.6
    return False


def heard_at_cues(model, dest: Path, cues) -> list[str]:
    """Распознаёт ~3 с от каждой метки (для проверки)."""
    texts = []
    for cue in cues:
        start = max(0.0, cue["timeMs"] / 1000 - 0.2)
        segments, _ = model.transcribe(
            load_audio(dest, start, 3.2), language="el", beam_size=1,
            temperature=0.0, condition_on_previous_text=False, without_timestamps=True,
        )
        texts.append(" ".join(segment.text for segment in segments).strip())
    return texts


def verdict(verse_text: str, heard_text: str) -> str:
    """ok — начало стиха слышно у метки; empty — whisper ничего не разобрал; miss — не похоже."""
    heard = tokens(heard_text)[:6]
    if not heard:
        return "empty"
    goal = tokens(verse_text)[:4]
    content = [w for w in goal if w not in STOP] or goal
    if any(loose_match(a, b) for a in content[:2] for b in heard):
        return "ok"
    g = "".join(goal)[:8]
    h = "".join(heard)[:14]
    match = SequenceMatcher(a=g, b=h, autojunk=False).find_longest_match(0, len(g), 0, len(h))
    return "ok" if match.size >= 4 else "miss"


def main() -> None:
    if "--verify" in sys.argv:
        verify_main()
        return
    check = "--check" in sys.argv
    args = [arg for arg in sys.argv[1:] if not arg.startswith("--")]
    books_only = {arg for arg in args if not arg.isdigit()}
    chapters_only = {int(arg) for arg in args if arg.isdigit()}
    fa = load_fa()
    whisper = None
    if check:
        from faster_whisper import WhisperModel

        threads = int(os.environ.get("WHISPER_CPU_THREADS", str(os.cpu_count() or 4)))
        whisper = WhisperModel(os.environ.get("WHISPER_MODEL", "medium"), device="cpu",
                               compute_type="int8", cpu_threads=threads)
    stats = []
    for book_id, book_num, title_ru in BOOKS:
        if books_only and book_id not in books_only:
            continue
        for index, verses in enumerate(load_chapters(book_id), start=1):
            if chapters_only and index not in chapters_only:
                continue
            url = f"https://www.wordproaudio.net/bibles/app/audio/58/{book_num}/{index}.mp3"
            dest = AUDIO / f"{book_num}_{index:02d}.mp3"
            download(url, dest)
            length = base.duration_sec(dest)
            starts, confidence = forced_align(fa, dest, verses)
            cues = fa_cues(verses, starts, length)
            if len(cues) != len(verses):
                raise RuntimeError(f"{book_id} {index}: cue count {len(cues)} != verses {len(verses)}")
            sure = sum(1 for n in confidence if confidence[n] >= CONFIDENT)
            weak = [n for n in confidence if confidence[n] < CONFIDENT]
            row = {
                "book": book_id, "chapter": index, "verses": len(cues), "confident": sure,
                "weak": weak, "duration": round(length, 1),
                "meanConfidence": round(sum(confidence.values()) / len(confidence), 3),
            }
            if whisper is not None:
                anchored, close, median = check_with_whisper(
                    whisper, dest, f"{NARRATOR}-{book_id}", index, verses, starts, length
                )
                row.update({"whisperAnchors": anchored, "within0_7s": close, "medianDiff": median})
            if not check:
                write_project(book_id, book_num, title_ru, index, cues)
            stats.append(row)
            print(json.dumps(row, ensure_ascii=False), flush=True)
    name = "check" if check else "fa"
    suffix = "-".join(sorted(books_only)) if books_only else "all"
    (WORK / f"stats-{name}-{suffix}.json").write_text(json.dumps(stats, indent=1), encoding="utf-8")


def verify_main() -> None:
    from faster_whisper import WhisperModel

    args = [arg for arg in sys.argv[1:] if not arg.startswith("--")]
    books_only = {arg for arg in args if not arg.isdigit()}
    chapters_only = {int(arg) for arg in args if arg.isdigit()}
    cache_path = WORK / "verify-heard.json"
    cache = json.loads(cache_path.read_text(encoding="utf-8")) if cache_path.exists() else {}
    model = None
    rows = []
    for book_id, book_num, _title in BOOKS:
        if books_only and book_id not in books_only:
            continue
        for index, verses in enumerate(load_chapters(book_id), start=1):
            if chapters_only and index not in chapters_only:
                continue
            project = json.loads((OUT / f"align-{book_id}-GRK-{index:02d}.json").read_text(encoding="utf-8"))
            key = f"{book_id}-{index}-" + ",".join(str(c["timeMs"]) for c in project["cues"])
            if key not in cache:
                if model is None:
                    threads = int(os.environ.get("WHISPER_CPU_THREADS", str(os.cpu_count() or 4)))
                    model = WhisperModel(os.environ.get("WHISPER_MODEL", "medium"), device="cpu",
                                         compute_type="int8", cpu_threads=threads)
                cache[key] = heard_at_cues(model, AUDIO / f"{book_num}_{index:02d}.mp3", project["cues"])
                cache_path.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
            verdicts = [verdict(v["text"], t) for v, t in zip(verses, cache[key])]
            row = {
                "book": book_id, "chapter": index, "verses": len(verses),
                "ok": verdicts.count("ok"), "empty": verdicts.count("empty"),
                "miss": [v["number"] for v, d in zip(verses, verdicts) if d == "miss"],
            }
            rows.append(row)
            print(json.dumps(row), flush=True)
    (WORK / "stats-verify.json").write_text(json.dumps(rows, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
