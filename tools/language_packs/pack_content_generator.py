"""Генерация строк пакета: перевод (MyMemory API), IPA (eng-to-ipa), примеры для EN."""
from __future__ import annotations

import json
import re
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from eng_to_ipa import convert as ipa_convert
from wordfreq import top_n_list

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"

TARGET = 1000

USER_AGENT = "BibleSqlite-language-pack-builder/1.0 (educational; +https://github.com/)"


def mymemory_translate(q: str, langpair: str, retries: int = 4) -> str:
    q = q.strip()
    if not q:
        return ""
    base = "https://api.mymemory.translated.net/get"
    params = urllib.parse.urlencode({"q": q[:498], "langpair": langpair})
    url = f"{base}?{params}"
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=25) as resp:
                raw = json.loads(resp.read().decode())
                if str(raw.get("responseStatus", "")) != "200":
                    raise ValueError("bad status")
                t = raw.get("responseData", {}).get("translatedText", "").strip()
                if t:
                    return t
        except Exception:
            time.sleep(0.35 * (attempt + 1))
    return ""


def parallel_map(
    items: list[str],
    langpair: str,
    *,
    workers: int = 6,
    delay_s: float = 0.06,
) -> dict[str, str]:
    """Словарь lemma (как в items) → перевод."""

    def job(text: str) -> tuple[str, str]:
        time.sleep(delay_s)
        return text, mymemory_translate(text, langpair)

    out: dict[str, str] = {}
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(job, x) for x in items]
        for fut in as_completed(futures):
            key, val = fut.result()
            if val:
                out[key] = val
    return out


# Короткие учебные примеры для частых функциональных слов (EN + RU).
FUNCTION_USAGE: dict[str, tuple[str, str]] = {
    "the": ("The sun is bright.", "Солнце яркое."),
    "a": ("A dog ran past.", "Мимо пробежала собака."),
    "an": ("An hour later.", "Час спустя."),
    "be": ("I want to be helpful.", "Я хочу быть полезным."),
    "to": ("I go to school.", "Я хожу в школу."),
    "of": ("The color of the sky.", "Цвет неба."),
    "and": ("You and me.", "Ты и я."),
    "in": ("In the morning.", "Утром."),
    "that": ("I know that.", "Я это знаю."),
    "have": ("We have time.", "У нас есть время."),
    "i": ("I understand.", "Я понимаю."),
    "it": ("It is cold.", "Холодно."),
    "for": ("This is for you.", "Это для тебя."),
    "not": ("Not now.", "Не сейчас."),
    "on": ("On the table.", "На столе."),
    "with": ("Coffee with milk.", "Кофе с молоком."),
    "he": ("He said yes.", "Он сказал да."),
    "as": ("As you wish.", "Как пожелаешь."),
    "you": ("You know.", "Ты знаешь."),
    "do": ("What do you do?", "Чем ты занимаешься?"),
    "at": ("At home.", "Дома."),
    "this": ("This works.", "Это работает."),
    "but": ("Small but strong.", "Мал, да удал."),
    "his": ("His book.", "Его книга."),
    "by": ("By the window / by bus.", "У окна; на автобусе."),
    "from": ("She is from Berlin.", "Она из Берлина."),
    "they": ("They agree.", "Они согласны."),
    "she": ("She reads.", "Она читает."),
    "or": ("Tea or coffee?", "Чай или кофе?"),
    "my": ("My family.", "Моя семья."),
    "one": ("One more time.", "Ещё раз."),
    "all": ("All people.", "Все люди."),
    "would": ("I would help.", "Я бы помог."),
    "there": ("There is hope.", "Есть надежда."),
    "their": ("Their house.", "Их дом."),
    "what": ("What happened?", "Что случилось?"),
    "so": ("So far so good.", "Пока всё хорошо."),
    "up": ("Look up.", "Посмотри вверх."),
    "out": ("Find out.", "Выяснить."),
    "if": ("If it rains.", "Если пойдёт дождь."),
    "about": ("Think about it.", "Подумай об этом."),
    "who": ("Who is there?", "Кто там?"),
    "get": ("Get ready.", "Приготовься."),
    "which": ("Which one?", "Который?"),
    "when": ("When?", "Когда?"),
    "make": ("Make a plan.", "Составь план."),
    "can": ("I can try.", "Я могу попробовать."),
    "like": ("I like music.", "Я люблю музыку."),
    "time": ("No time.", "Нет времени."),
    "no": ("No problem.", "Без проблем."),
    "just": ("Just in case.", "На всякий случай."),
    "him": ("Tell him.", "Скажи ему."),
    "know": ("I know.", "Я знаю."),
    "take": ("Take a seat.", "Садись."),
    "into": ("Go into the room.", "Зайди в комнату."),
    "year": ("This year.", "В этом году."),
    "your": ("Your turn.", "Твоя очередь."),
    "some": ("Some people.", "Некоторые."),
    "could": ("I could help.", "Я мог бы помочь."),
    "them": ("I see them.", "Я их вижу."),
    "see": ("I see it.", "Я вижу."),
    "other": ("The other day.", "На днях."),
    "than": ("Easier than before.", "Легче, чем раньше."),
    "then": ("First, then next.", "Сначала, потом."),
    "now": ("Right now.", "Прямо сейчас."),
    "look": ("Look here.", "Смотри сюда."),
    "only": ("Only one.", "Только один."),
    "come": ("Come here.", "Иди сюда."),
    "its": ("Its color.", "Его цвет."),
    "over": ("Over the hill.", "За холмом."),
    "think": ("I think so.", "Так думаю."),
    "also": ("I also agree.", "Я тоже согласен."),
    "back": ("Come back.", "Вернись."),
    "after": ("After lunch.", "После обеда."),
    "use": ("Use a pen.", "Пользуйся ручкой."),
    "two": ("Two apples.", "Два яблока."),
    "how": ("How are you?", "Как дела?"),
    "our": ("Our team.", "Наша команда."),
    "work": ("Work hard.", "Усердно работай."),
    "first": ("First step.", "Первый шаг."),
    "well": ("Sleep well.", "Спи спокойно."),
    "way": ("This way.", "Сюда."),
    "even": ("Even better.", "Ещё лучше."),
    "new": ("A new day.", "Новый день."),
    "want": ("I want water.", "Я хочу воды."),
    "because": ("Because of rain.", "Из-за дождя."),
    "any": ("Any ideas?", "Есть идеи?"),
    "these": ("These books.", "Эти книги."),
    "give": ("Give me five.", "Дай пять."),
    "day": ("Have a nice day.", "Хорошего дня."),
    "most": ("Most people.", "Большинство."),
    "us": ("Tell us.", "Расскажи нам."),
}


def freq_lemmas(lang: str) -> list[str]:
    raw = top_n_list(lang, 25000)
    out: list[str] = []
    for w in raw:
        if not w:
            continue
        w = w.strip()
        if len(w) < 2:
            continue
        if lang == "en":
            if not re.match(r"^[a-z][a-z\-']*$", w, re.I):
                continue
        out.append(w)
        if len(out) >= TARGET + 120:
            break
    return out[:TARGET]


def load_en_ru() -> dict[str, str]:
    p = DATA / "en_ru_core.json"
    if not p.is_file():
        return {}
    raw = json.loads(p.read_text(encoding="utf-8"))
    return {k.lower(): v for k, v in raw.items()}


def ipa_for_en(lemma: str) -> str | None:
    try:
        s = ipa_convert(lemma.strip())
        if not s:
            return None
        return "/" + s + "/"
    except Exception:
        return None


def english_example_tail(lemma: str) -> tuple[str, str]:
    """Локально, без API: универсальный учебный контекст."""
    return (
        f"A typical English word: «{lemma}».",
        f"Типичное английское слово «{lemma}» — ориентируйтесь на перевод выше и повторяйте вслух.",
    )


def example_for_english(lemma: str) -> tuple[str, str]:
    low = lemma.lower()
    if low in FUNCTION_USAGE:
        return FUNCTION_USAGE[low]
    return english_example_tail(lemma)


def build_english_rows(words: list[str], enru: dict[str, str]) -> list[dict]:
    merged = dict(enru)
    missing = [w for w in words if w.lower() not in merged]
    got = parallel_map(missing, "en|ru", workers=6, delay_s=0.07)
    for w in missing:
        low = w.lower()
        merged[low] = got.get(w, "").strip() or mymemory_translate(w, "en|ru")

    rows: list[dict] = []
    for i, lemma in enumerate(words[:TARGET], start=1):
        low = lemma.lower()
        gloss = merged.get(low, "").strip()
        if not gloss:
            gloss = lemma
        ipa = ipa_for_en(lemma)
        ex_en, ex_ru = example_for_english(lemma)
        rows.append(
            {
                "id": f"{i:04d}",
                "lemma": lemma,
                "display": lemma,
                "glossRu": gloss,
                "ipa": ipa,
                "pos": "",
                "frequencyRank": i,
                "exampleL2": ex_en,
                "exampleRu": ex_ru,
                "mnemonicHint": "",
                "morphologyNotes": "",
            },
        )
    return rows


def build_foreign_rows(
    *,
    lang_app: str,
    words: list[str],
    translator_src: str,
) -> list[dict]:
    """Перевод формы слова на русский через Google (deep-translator). Параллельно с ограничением потоков."""
    from deep_translator import GoogleTranslator

    chunk = words[:TARGET]

    def fetch_one(w: str) -> tuple[str, str]:
        time.sleep(0.06)
        g = GoogleTranslator(source=translator_src, target="ru")
        ru = g.translate(w).strip()
        if not ru:
            ru = mymemory_translate(w, f"{translator_src}|ru")
        return w, ru or w

    got: dict[str, str] = {}
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = [pool.submit(fetch_one, w) for w in chunk]
        for fut in as_completed(futures):
            w, gloss = fut.result()
            got[w] = gloss

    rows: list[dict] = []
    for i, w in enumerate(chunk, start=1):
        rows.append(
            {
                "id": f"{i:04d}",
                "lemma": w,
                "display": w,
                "glossRu": got.get(w, w),
                "ipa": None,
                "pos": "",
                "frequencyRank": i,
                "mnemonicHint": "",
                "morphologyNotes": "",
            },
        )
    return rows
